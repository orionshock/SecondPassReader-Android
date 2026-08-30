import { colibrio } from "./colibrio";
import {
    createPublicationSnapshot,
    isHighSurrogate,
    isLowSurrogate,
    isOwnedRuntimeNode,
    validateTextBoundary,
    type BoundaryPoint
} from "./publication-dom";
import { containsDurableText } from "./quote-context";
import {
    CONTEXT_LENGTH,
    MIN_VISIBLE_EXTENT_PIXELS,
    parseCfi,
    targetKind
} from "./protocol";
import { isTextPosition } from "./content-target";

type ReadingDirection = "ltr" | "rtl";

export function generateVisiblePositionContentCfi(): string {
    const mode = visiblePositionMode();
    const boundary = firstVisibleTextBoundary(document, mode.direction);
    if (!boundary) {
        throw new Error("VISIBLE_POSITION_UNAVAILABLE");
    }

    const liveRange = document.createRange();
    liveRange.setStart(boundary.container, boundary.offset);
    liveRange.collapse(true);
    const snapshot = createPublicationSnapshot(document);
    const snapshotRange = snapshot.toSnapshotRange(liveRange);
    if (!snapshotRange.collapsed ||
        !isTextPosition(snapshotRange.startContainer, snapshotRange.startOffset)) {
        throw new Error("VISIBLE_POSITION_UNAVAILABLE");
    }
    validateTextBoundary(snapshotRange.startContainer, snapshotRange.startOffset);

    const builder = new colibrio.EpubCfiBuilder();
    builder.setTextAssertionOptions({
        preLength: CONTEXT_LENGTH,
        postLength: CONTEXT_LENGTH,
        snapToWordBoundaries: false
    });
    builder.appendTerminalDomPosition(
        snapshotRange.startContainer,
        snapshotRange.startOffset
    );
    const contentCfi = builder.toString();
    const parsed = parseCfi(contentCfi);
    if (targetKind(parsed) !== "point") {
        throw new Error("VISIBLE_POSITION_UNAVAILABLE");
    }
    return contentCfi;
}

export function visiblePositionMode(): { readonly direction: ReadingDirection } {
    requireReflowableCfiDocument();
    const root = document.documentElement;
    const style = root.style;
    if (style.getPropertyValue("--USER__view").trim() === "readium-scroll-on" ||
        style.getPropertyValue("--USER__scroll").trim() === "readium-scroll-on") {
        throw new Error("UNSUPPORTED_SCROLL_MODE");
    }
    const rootStyle = window.getComputedStyle(root);
    const writingMode = rootStyle.getPropertyValue("writing-mode").trim();
    if (writingMode !== "horizontal-tb") {
        throw new Error("UNSUPPORTED_WRITING_MODE");
    }
    const bodyDirection = window.getComputedStyle(document.body)
        .getPropertyValue("direction")
        .trim()
        .toLowerCase();
    if (bodyDirection !== "ltr" && bodyDirection !== "rtl") {
        throw new Error("UNSUPPORTED_WRITING_MODE");
    }
    return { direction: bodyDirection };
}

export function requireReflowableCfiDocument(): void {
    if (!window.readium || window.readium.isFixedLayout === true ||
        window.readium.isReflowable !== true) {
        throw new Error("UNSUPPORTED_FIXED_LAYOUT");
    }
}

export function firstVisibleTextBoundary(
    liveDocument: Document,
    direction: ReadingDirection
): BoundaryPoint | null {
    const body = liveDocument.body;
    if (!body || publicationViewportWidth(liveDocument) <= 0 ||
        publicationViewportHeight(liveDocument) <= 0) {
        return null;
    }
    const walker = liveDocument.createTreeWalker(
        body,
        NodeFilter.SHOW_TEXT,
        null
    );
    let textNode = walker.nextNode() as Text | null;
    while (textNode) {
        if (!isInsideOwnedRuntimeNode(textNode, liveDocument) &&
            nodeIntersectsViewport(textNode, liveDocument, direction)) {
            const offset = firstVisibleCharacterOffset(
                textNode,
                liveDocument,
                direction
            );
            if (offset !== null) {
                return { container: textNode, offset: offset };
            }
        }
        textNode = walker.nextNode() as Text | null;
    }
    return null;
}

export function isInsideOwnedRuntimeNode(node: Node, liveDocument: Document): boolean {
    let ancestor = node.parentNode;
    while (ancestor && ancestor !== liveDocument.body) {
        if (isOwnedRuntimeNode(ancestor, liveDocument)) {
            return true;
        }
        ancestor = ancestor.parentNode;
    }
    return false;
}

export function nodeIntersectsViewport(
    textNode: Text,
    liveDocument: Document,
    direction: ReadingDirection
): boolean {
    if (!textNode.data || !containsDurableText(textNode.data) ||
        !isRenderedTextNode(textNode)) {
        return false;
    }
    const range = liveDocument.createRange();
    range.selectNodeContents(textNode);
    return Array.from(range.getClientRects()).some(function (rect) {
        return hasPositiveViewportIntersection(rect, direction);
    });
}

export function firstVisibleCharacterOffset(
    textNode: Text,
    liveDocument: Document,
    direction: ReadingDirection
): number | null {
    let low = 1;
    let high = textNode.length;
    let firstIntersectingEnd = null;
    while (low <= high) {
        const end = Math.floor((low + high) / 2);
        const prefix = liveDocument.createRange();
        prefix.setStart(textNode, 0);
        prefix.setEnd(textNode, end);
        const intersects = Array.from(prefix.getClientRects()).some(function (rect) {
            return hasPositiveViewportIntersection(rect, direction);
        });
        if (intersects) {
            firstIntersectingEnd = end;
            high = end - 1;
        } else {
            low = end + 1;
        }
    }
    if (firstIntersectingEnd === null) {
        return null;
    }
    let offset = codePointStart(textNode.data, firstIntersectingEnd - 1);
    while (offset < textNode.length) {
        const codePoint = textNode.data.codePointAt(offset)!;
        const characterLength = codePoint > 0xffff ? 2 : 1;
        const character = textNode.data.slice(offset, offset + characterLength);
        if (containsDurableText(character)) {
            const range = liveDocument.createRange();
            range.setStart(textNode, offset);
            range.setEnd(textNode, offset + characterLength);
            const isVisible = Array.from(range.getClientRects()).some(function (rect) {
                return hasPositiveViewportIntersection(rect, direction);
            });
            if (isVisible) {
                return offset;
            }
        }
        offset += characterLength;
    }
    return null;
}

export function codePointStart(value: string, offset: number): number {
    return offset > 0 && isLowSurrogate(value.charCodeAt(offset)) &&
        isHighSurrogate(value.charCodeAt(offset - 1))
        ? offset - 1
        : offset;
}

export function isRenderedTextNode(textNode: Text): boolean {
    let element = textNode.parentElement;
    while (element) {
        const style = window.getComputedStyle(element);
        if (element.hidden || style.display === "none" ||
            style.visibility === "hidden" || style.visibility === "collapse" ||
            style.opacity === "0" || style.contentVisibility === "hidden") {
            return false;
        }
        if (element === document.body) {
            break;
        }
        element = element.parentElement;
    }
    return true;
}

export function hasPositiveViewportIntersection(
    rect: DOMRect,
    direction: ReadingDirection
): boolean {
    const viewportWidth = publicationViewportWidth(document);
    const viewportHeight = publicationViewportHeight(document);
    const blockStart = Math.max(0, rect.top);
    const blockEnd = Math.min(viewportHeight, rect.bottom);
    const inlineStart = direction === "rtl"
        ? Math.min(viewportWidth, rect.right)
        : Math.max(0, rect.left);
    const inlineEnd = direction === "rtl"
        ? Math.max(0, rect.left)
        : Math.min(viewportWidth, rect.right);
    const inlineExtent = direction === "rtl"
        ? inlineStart - inlineEnd
        : inlineEnd - inlineStart;
    return blockEnd - blockStart > MIN_VISIBLE_EXTENT_PIXELS &&
        inlineExtent > MIN_VISIBLE_EXTENT_PIXELS &&
        rect.width > MIN_VISIBLE_EXTENT_PIXELS &&
        rect.height > MIN_VISIBLE_EXTENT_PIXELS;
}

export function publicationViewportWidth(publicationDocument: Document): number {
    return publicationDocument.documentElement.clientWidth || window.innerWidth;
}

export function publicationViewportHeight(publicationDocument: Document): number {
    return publicationDocument.documentElement.clientHeight || window.innerHeight;
}

