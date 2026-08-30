import { colibrio, type CfiDomRange } from "./colibrio";
import {
    createPublicationSnapshot,
    isCharacterData,
    type BoundaryPoint
} from "./publication-dom";
import { publicationBody, textContext } from "./quote-context";
import {
    CONTEXT_LENGTH,
    parseCfi,
    SELECTION_CONTEXT_LENGTH
} from "./protocol";
import { requireReflowableCfiDocument } from "./visible-position";

export interface ContentSelection {
    readonly contentCfi: string;
    readonly selectedText: string;
    readonly prefix: string | null;
    readonly suffix: string | null;
}

export function generateSelectionContentCfi(): ContentSelection | null {
    requireReflowableCfiDocument();
    const selection = window.getSelection();
    if (!selection || selection.rangeCount === 0 || selection.isCollapsed) {
        return null;
    }
    if (selection.rangeCount !== 1) {
        throw new Error("INVALID_RANGE");
    }
    const liveRange = rangeWithTextBoundaries(selection.getRangeAt(0), document);
    if (liveRange.collapsed) {
        return null;
    }
    const snapshot = createPublicationSnapshot(document);
    const snapshotRange = snapshot.toSnapshotRange(liveRange);
    if (snapshotRange.collapsed) {
        throw new Error("SELECTION_UNAVAILABLE");
    }
    const context = textContext(
        snapshotRange,
        snapshot.document,
        SELECTION_CONTEXT_LENGTH
    );
    if (context.selectedText === null) {
        throw new Error("SELECTION_UNAVAILABLE");
    }
    const builder = new colibrio.EpubCfiBuilder();
    if (context.prefix !== null && context.suffix !== null) {
        builder.setTextAssertionOptions({
            preLength: CONTEXT_LENGTH,
            postLength: CONTEXT_LENGTH,
            snapToWordBoundaries: false
        });
    }
    builder.appendTerminalDomRange(rangeForCfiBuilder(snapshotRange));
    const contentCfi = builder.toString();
    parseCfi(contentCfi);
    return {
        contentCfi: contentCfi,
        selectedText: context.selectedText,
        prefix: context.prefix,
        suffix: context.suffix
    };
}

export function rangeWithTextBoundaries(range: Range, publicationDocument: Document): Range {
    const body = publicationBody(publicationDocument);
    if (!body) {
        throw new Error("SELECTION_UNAVAILABLE");
    }
    const start = textBoundaryAtOrAfter(
        range.startContainer,
        range.startOffset,
        body
    );
    const end = textBoundaryAtOrBefore(
        range.endContainer,
        range.endOffset,
        body
    );
    if (!start || !end) {
        throw new Error("SELECTION_UNAVAILABLE");
    }
    const normalized = publicationDocument.createRange();
    try {
        normalized.setStart(start.container, start.offset);
        normalized.setEnd(end.container, end.offset);
    } catch (error) {
        throw new Error("SELECTION_UNAVAILABLE");
    }
    if (normalized.collapsed || normalized.toString() !== range.toString()) {
        throw new Error("SELECTION_UNAVAILABLE");
    }
    return normalized;
}

export function textBoundaryAtOrAfter(
    container: Node,
    offset: number,
    root: Element
): BoundaryPoint | null {
    if (isCharacterData(container)) {
        return { container: container, offset: offset };
    }
    let candidate = container.childNodes[offset] || nodeAfter(container, root);
    while (candidate) {
        if (isCharacterData(candidate)) {
            return { container: candidate, offset: 0 };
        }
        candidate = candidate.firstChild || nodeAfter(candidate, root);
    }
    return null;
}

export function textBoundaryAtOrBefore(
    container: Node,
    offset: number,
    root: Element
): BoundaryPoint | null {
    if (isCharacterData(container)) {
        return { container: container, offset: offset };
    }
    let candidate = offset > 0
        ? container.childNodes[offset - 1]
        : nodeBefore(container, root);
    while (candidate) {
        if (isCharacterData(candidate)) {
            return { container: candidate, offset: candidate.length };
        }
        candidate = candidate.lastChild || nodeBefore(candidate, root);
    }
    return null;
}

export function nodeAfter(node: Node, root: Node): Node | null {
    let candidate: Node | null = node;
    while (candidate && candidate !== root) {
        if (candidate.nextSibling) {
            return candidate.nextSibling;
        }
        candidate = candidate.parentNode;
    }
    return null;
}

export function nodeBefore(node: Node, root: Node): Node | null {
    let candidate: Node | null = node;
    while (candidate && candidate !== root) {
        if (candidate.previousSibling) {
            return candidate.previousSibling;
        }
        candidate = candidate.parentNode;
    }
    return null;
}

/*
 * Colibrio 1.1.0 emits an invalid compact range when a DOM Range's common
 * ancestor is itself a text node (`/1,:start,:end`). Supplying the same
 * boundaries with their parent element as the common ancestor produces the
 * standards-shaped equivalent (`,/1:start,/1:end`) without altering the
 * selected content or offsets.
 */
export function rangeForCfiBuilder(range: Range): CfiDomRange {
    const commonAncestor = range.commonAncestorContainer;
    if (!isCharacterData(commonAncestor)) {
        return range;
    }
    const parent = commonAncestor.parentElement;
    if (!parent) {
        throw new Error("DOM_TARGET_NOT_FOUND");
    }
    return {
        collapsed: range.collapsed,
        startContainer: range.startContainer,
        startOffset: range.startOffset,
        endContainer: range.endContainer,
        endOffset: range.endOffset,
        commonAncestorContainer: parent
    };
}

/*
 * A progress CFI is captured only when explicitly requested. It denotes
 * the first durable nonblank character in DOM order which has a positive
 * intersection with the current horizontal page viewport. DOM order is
 * also the logical reading order for RTL content; screen-space sorting
 * would incorrectly reverse RTL columns in a two-page spread.
 */
