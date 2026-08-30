export type BoundarySource = "live" | "snapshot";
export interface BoundaryPoint { readonly container: Node; readonly offset: number; }
export interface PublicationSnapshot {
    readonly document: Document;
    toSnapshotNode(node: Node): Node | null;
    toLiveNode(node: Node): Node | null;
    toSnapshotRange(range: Range): Range;
    toLiveRange(range: Range): Range;
}

const READIUM_DECORATION_ROOT_ID = /^r2-decoration-[0-9]+$/;
const READIUM_REFLOWABLE_SCRIPT_PATH = "/readium/scripts/readium-reflowable.js";
const READIUM_STYLESHEET_PATH = "/readium/readium-css/";

/*
 * Colibrio has no node-filter hook. Work on a mapped clone instead of ever
 * removing nodes from the live publication. This predicate is deliberately
 * narrow: it recognizes concrete DOM ownership from Readium 3.3.0 rather
 * than publisher-facing class-name patterns.
 *
 * Readium's selection and viewport support use event handlers, root
 * classes and head metadata, not body helper elements. Second Pass installs
 * this bridge with evaluateJavascript(), so it does not add a script node.
 * Those mechanisms therefore need no broader content-node exclusion.
 */
export function isOwnedRuntimeNode(node: Node, liveDocument: Document): boolean {
    return isReadiumDecorationRoot(node, liveDocument) ||
        isReadiumVirtualPage(node, liveDocument) ||
        isReadiumHeadResource(node, liveDocument);
}

export function isReadiumDecorationRoot(node: Node, liveDocument: Document): boolean {
    if (!(node instanceof Element)) return false;
    const element = node as HTMLElement;
    return element.parentNode === liveDocument.body && element.localName === "div" &&
        READIUM_DECORATION_ROOT_ID.test(element.id) && element.hasAttribute("data-group") &&
        element.style.pointerEvents === "none";
}

export function isReadiumVirtualPage(node: Node, liveDocument: Document): boolean {
    if (!(node instanceof Element)) return false;
    const element = node as HTMLElement;
    return element.parentNode === liveDocument.body && element.localName === "div" &&
        element.id === "readium-virtual-page" && element.style.breakBefore === "column" &&
        element.textContent === "\u200b";
}

export function isReadiumHeadResource(node: Node, liveDocument: Document): boolean {
    if (!(node instanceof Element) || node.parentNode !== liveDocument.head) {
        return false;
    }
    if (node.localName === "script") {
        return resourcePath(node.getAttribute("src")).endsWith(
            READIUM_REFLOWABLE_SCRIPT_PATH
        );
    }
    const relationship = (node.getAttribute("rel") || "").toLowerCase();
    if (node.localName === "link" && relationship === "stylesheet") {
        return resourcePath(node.getAttribute("href")).indexOf(READIUM_STYLESHEET_PATH) >= 0;
    }
    return false;
}

export function resourcePath(resourceUrl: string | null): string {
    if (!resourceUrl) {
        return "";
    }
    try {
        return new URL(resourceUrl, document.baseURI).pathname;
    } catch (error) {
        return "";
    }
}

export function createPublicationSnapshot(liveDocument: Document): PublicationSnapshot {
    if (!liveDocument || !liveDocument.documentElement) {
        throw new Error("DOM_TARGET_NOT_FOUND");
    }
    const snapshotDocument = liveDocument.implementation.createDocument(null, "", null);
    const liveToSnapshot = new WeakMap();
    const snapshotToLive = new WeakMap();
    liveToSnapshot.set(liveDocument, snapshotDocument);
    snapshotToLive.set(snapshotDocument, liveDocument);
    clonePublicationNode(
        liveDocument.documentElement,
        snapshotDocument,
        snapshotDocument,
        liveDocument,
        liveToSnapshot,
        snapshotToLive
    );

    return Object.freeze({
        document: snapshotDocument,
        toSnapshotNode: function toSnapshotNode(liveNode: Node) {
            return liveToSnapshot.get(liveNode) || null;
        },
        toLiveNode: function toLiveNode(snapshotNode: Node) {
            return snapshotToLive.get(snapshotNode) || null;
        },
        toSnapshotRange: function toSnapshotRange(liveRange: Range) {
            return translateRange(
                liveRange,
                snapshotDocument,
                liveToSnapshot,
                "live"
            );
        },
        toLiveRange: function toLiveRange(snapshotRange: Range) {
            return translateRange(
                snapshotRange,
                liveDocument,
                snapshotToLive,
                "snapshot"
            );
        }
    });
}

export function clonePublicationNode(
    liveNode: Node,
    snapshotParent: Node,
    snapshotDocument: Document,
    liveDocument: Document,
    liveToSnapshot: WeakMap<Node, Node>,
    snapshotToLive: WeakMap<Node, Node>
): void {
    if (isOwnedRuntimeNode(liveNode, liveDocument)) {
        return;
    }
    const snapshotNode = snapshotDocument.importNode(liveNode, false);
    snapshotParent.appendChild(snapshotNode);
    liveToSnapshot.set(liveNode, snapshotNode);
    snapshotToLive.set(snapshotNode, liveNode);
    Array.from(liveNode.childNodes).forEach(function (liveChild) {
        clonePublicationNode(
            liveChild,
            snapshotNode,
            snapshotDocument,
            liveDocument,
            liveToSnapshot,
            snapshotToLive
        );
    });
}

export function translateRange(
    sourceRange: Range,
    targetDocument: Document,
    nodeMap: WeakMap<Node, Node>,
    sourceKind: BoundarySource
): Range {
    if (!sourceRange) {
        throw new Error("INVALID_RANGE");
    }
    const start = translateBoundary(
        sourceRange.startContainer,
        sourceRange.startOffset,
        nodeMap,
        sourceKind
    );
    const end = translateBoundary(
        sourceRange.endContainer,
        sourceRange.endOffset,
        nodeMap,
        sourceKind
    );
    const targetRange = targetDocument.createRange();
    try {
        targetRange.setStart(start.container, start.offset);
        targetRange.setEnd(end.container, end.offset);
    } catch (error) {
        throw new Error("INVALID_RANGE");
    }
    return targetRange;
}

export function translateBoundary(
    sourceContainer: Node,
    sourceOffset: number,
    nodeMap: WeakMap<Node, Node>,
    sourceKind: BoundarySource
): BoundaryPoint {
    const targetContainer = nodeMap.get(sourceContainer);
    if (!targetContainer || !Number.isInteger(sourceOffset) || sourceOffset < 0) {
        throw new Error("DOM_TARGET_NOT_FOUND");
    }
    if (isCharacterData(sourceContainer)) {
        if (sourceOffset > sourceContainer.length) {
            throw new Error("INVALID_RANGE");
        }
        return { container: targetContainer, offset: sourceOffset };
    }
    const sourceChildren = Array.from(sourceContainer.childNodes);
    if (sourceOffset > sourceChildren.length) {
        throw new Error("INVALID_RANGE");
    }
    const targetOffset = sourceKind === "live"
        ? liveBoundaryOffset(sourceChildren, sourceOffset, nodeMap, targetContainer)
        : snapshotBoundaryOffset(sourceChildren, sourceOffset, nodeMap, targetContainer);
    return { container: targetContainer, offset: targetOffset };
}

export function isCharacterData(node: Node): node is CharacterData {
    return node.nodeType === Node.TEXT_NODE ||
        node.nodeType === Node.CDATA_SECTION_NODE ||
        node.nodeType === Node.COMMENT_NODE;
}

export function liveBoundaryOffset(
    liveChildren: readonly Node[],
    liveOffset: number,
    liveToSnapshot: WeakMap<Node, Node>,
    snapshotParent: Node
): number {
    return liveChildren.slice(0, liveOffset).reduce(function (offset, liveChild) {
        const snapshotChild = liveToSnapshot.get(liveChild);
        return snapshotChild && snapshotChild.parentNode === snapshotParent
            ? offset + 1
            : offset;
    }, 0);
}

export function snapshotBoundaryOffset(
    snapshotChildren: readonly Node[],
    snapshotOffset: number,
    snapshotToLive: WeakMap<Node, Node>,
    liveParent: Node
): number {
    if (snapshotOffset < snapshotChildren.length) {
        return liveChildIndex(
            snapshotChildren[snapshotOffset]!,
            snapshotToLive,
            liveParent
        );
    }
    if (snapshotChildren.length === 0) {
        return 0;
    }
    return liveChildIndex(
        snapshotChildren[snapshotChildren.length - 1]!,
        snapshotToLive,
        liveParent
    ) + 1;
}

export function liveChildIndex(
    snapshotChild: Node,
    snapshotToLive: WeakMap<Node, Node>,
    liveParent: Node
): number {
    const liveChild = snapshotToLive.get(snapshotChild);
    const liveIndex = liveChild
        ? Array.prototype.indexOf.call(liveParent.childNodes, liveChild)
        : -1;
    if (liveIndex < 0) {
        throw new Error("DOM_TARGET_NOT_FOUND");
    }
    return liveIndex;
}

/*
 * DOM character offsets are UTF-16 code-unit offsets. Keep every CFI and
 * context operation in JavaScript so Kotlin never reinterprets them as
 * code-point indexes. A boundary splitting a surrogate pair is not a
 * durable text position and is rejected.
 */
export function validateTextBoundary(container: Node, offset: number): void {
    if (!isCharacterData(container) || container.nodeType === Node.COMMENT_NODE) {
        return;
    }
    const text = container.data;
    if (offset > 0 && offset < text.length &&
        isHighSurrogate(text.charCodeAt(offset - 1)) &&
        isLowSurrogate(text.charCodeAt(offset))) {
        throw new Error("INVALID_RANGE");
    }
}

export function isHighSurrogate(codeUnit: number): boolean {
    return codeUnit >= 0xd800 && codeUnit <= 0xdbff;
}

export function isLowSurrogate(codeUnit: number): boolean {
    return codeUnit >= 0xdc00 && codeUnit <= 0xdfff;
}

