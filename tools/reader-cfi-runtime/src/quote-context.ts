import { isCharacterData, isHighSurrogate, isLowSurrogate, validateTextBoundary } from "./publication-dom";
import { CONTEXT_LENGTH, MAX_SELECTED_TEXT_LENGTH } from "./protocol";

export interface QuoteContext {
    readonly selectedText: string | null;
    readonly prefix: string | null;
    readonly suffix: string | null;
}

export function textContext(
    range: Range,
    publicationDocument: Document,
    contextLength?: number
): QuoteContext {
    contextLength = contextLength || CONTEXT_LENGTH;
    validateTextBoundary(range.startContainer, range.startOffset);
    validateTextBoundary(range.endContainer, range.endOffset);
    const body = publicationBody(publicationDocument);
    if (!body || !body.contains(range.startContainer) ||
        !body.contains(range.endContainer)) {
        throw new Error("DOM_TARGET_NOT_FOUND");
    }
    const selectedText = range.collapsed ? null : range.toString();
    if (selectedText && selectedText.length > MAX_SELECTED_TEXT_LENGTH) {
        throw new Error("RESULT_TOO_LARGE");
    }
    return {
        selectedText: selectedText,
        prefix: textBeforePosition(
            range.startContainer,
            range.startOffset,
            body,
            publicationDocument,
            contextLength
        ),
        suffix: textAfterPosition(
            range.endContainer,
            range.endOffset,
            body,
            publicationDocument,
            contextLength
        )
    };
}

export function textBeforePosition(
    container: Node,
    offset: number,
    body: Element,
    publicationDocument: Document,
    limit: number
): string | null {
    const walker = publicationDocument.createTreeWalker(body, NodeFilter.SHOW_TEXT);
    let result = "";
    let anchor = container;
    if (isCharacterData(container)) {
        result = takeLastCodeUnitSafe(container.data.slice(0, offset), limit) || "";
    } else if (offset > 0) {
        anchor = container.childNodes[offset - 1]!;
        const text = lastTextDescendant(anchor, publicationDocument);
        if (text) {
            anchor = text;
            result = takeLastCodeUnitSafe(text.data, limit) || "";
        }
    }
    walker.currentNode = anchor;
    let node = walker.previousNode();
    while (node && result.length < limit) {
        const remaining = limit - result.length;
        result = (takeLastCodeUnitSafe((node as CharacterData).data, remaining) || "") + result;
        node = walker.previousNode();
    }
    return result || null;
}

export function textAfterPosition(
    container: Node,
    offset: number,
    body: Element,
    publicationDocument: Document,
    limit: number
): string | null {
    const walker = publicationDocument.createTreeWalker(body, NodeFilter.SHOW_TEXT);
    let result = "";
    let anchor = container;
    if (isCharacterData(container)) {
        result = takeFirstCodeUnitSafe(container.data.slice(offset), limit) || "";
    } else if (offset < container.childNodes.length) {
        anchor = container.childNodes[offset]!;
        const text = firstTextDescendant(anchor, publicationDocument);
        if (text) {
            anchor = text;
            result = takeFirstCodeUnitSafe(text.data, limit) || "";
        }
    } else if (offset > 0) {
        anchor = container.childNodes[offset - 1]!;
        const text = lastTextDescendant(anchor, publicationDocument);
        if (text) {
            anchor = text;
        }
    }
    walker.currentNode = anchor;
    let node = walker.nextNode();
    while (node && result.length < limit) {
        const remaining = limit - result.length;
        result += takeFirstCodeUnitSafe((node as CharacterData).data, remaining) || "";
        node = walker.nextNode();
    }
    return result || null;
}

export function firstTextDescendant(root: Node, publicationDocument: Document): CharacterData | null {
    if (isCharacterData(root)) {
        return root;
    }
    return publicationDocument
        .createTreeWalker(root, NodeFilter.SHOW_TEXT)
        .nextNode() as CharacterData | null;
}

export function lastTextDescendant(root: Node, publicationDocument: Document): CharacterData | null {
    if (isCharacterData(root)) {
        return root;
    }
    const walker = publicationDocument.createTreeWalker(root, NodeFilter.SHOW_TEXT);
    let last: CharacterData | null = null;
    let node = walker.nextNode();
    while (node) {
        last = node as CharacterData;
        node = walker.nextNode();
    }
    return last;
}

export function publicationBody(publicationDocument: Document): Element | null {
    return publicationDocument.getElementsByTagNameNS("*", "body")[0] || null;
}

export function takeLastCodeUnitSafe(value: string, limit: number): string | null {
    let start = Math.max(0, value.length - limit);
    if (start > 0 && isLowSurrogate(value.charCodeAt(start)) &&
        isHighSurrogate(value.charCodeAt(start - 1))) {
        start += 1;
    }
    return value.slice(start) || null;
}

export function takeFirstCodeUnitSafe(value: string, limit: number): string | null {
    let end = Math.min(value.length, limit);
    if (end < value.length && end > 0 &&
        isHighSurrogate(value.charCodeAt(end - 1)) &&
        isLowSurrogate(value.charCodeAt(end))) {
        end -= 1;
    }
    return value.slice(0, end) || null;
}

export function containsDurableText(value: string): boolean {
    return /[^\s\u200b\u200c\u200d\ufeff]/u.test(value);
}

