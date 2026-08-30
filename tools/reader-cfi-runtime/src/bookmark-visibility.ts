import {
    hasDurableCharacter,
    isInsideRuntimeNode,
    isTargetRangeVisible,
    resolveContentTargetDetails
} from "./content-target";
import { isCharacterData, isHighSurrogate, isLowSurrogate } from "./publication-dom";
import { publicationBody } from "./quote-context";

interface PointCandidate { readonly id: string; readonly cfi: string; }
interface PointVisibility { readonly id: string; readonly visible: boolean; }

export function visiblePointTargets(
    serializedCandidates: string,
    packageDocumentXml: string,
    packagePath: string,
    expectedSpineIndex: number,
    expectedIdref: string,
    expectedItemrefId: string | null,
    expectedResourceHref: string
): PointVisibility[] {
    const candidates: unknown = JSON.parse(serializedCandidates);
    if (!Array.isArray(candidates) || candidates.length > 1000) {
        throw new Error("INVALID_CFI");
    }
    return candidates.map(function (candidate: unknown) {
        try {
            if (!isPointCandidate(candidate)) {
                return null;
            }
            const details = resolveContentTargetDetails(
                candidate.cfi,
                packageDocumentXml,
                packagePath,
                expectedSpineIndex,
                expectedIdref,
                expectedItemrefId,
                expectedResourceHref,
                true
            );
            return {
                id: candidate.id,
                visible: details.resolution.kind === "point" &&
                    isTargetRangeVisible(details.liveRange, document)
            };
        } catch (_error: unknown) {
            return { id: isPointCandidate(candidate) ? candidate.id : null, visible: false };
        }
    }).filter(function (result): result is PointVisibility {
        return result !== null && typeof result.id === "string";
    });
}

function isPointCandidate(value: unknown): value is PointCandidate {
    return typeof value === "object" && value !== null &&
        typeof (value as Record<string, unknown>).id === "string" &&
        typeof (value as Record<string, unknown>).cfi === "string";
}

export function visibilityProbeRange(range: Range, publicationDocument: Document): Range {
    if (!range.collapsed) {
        return range;
    }
    const probe = range.cloneRange();
    const container = range.startContainer;
    const offset = range.startOffset;
    if (isCharacterData(container) && offset < container.length) {
        probe.setEnd(container, nextCodeUnitBoundary(container.data, offset));
        return probe;
    }
    const body = publicationBody(publicationDocument);
    if (!body) {
        throw new Error("DOM_TARGET_NOT_FOUND");
    }
    const walker = publicationDocument.createTreeWalker(body, NodeFilter.SHOW_TEXT);
    let foundContainer = false;
    let node = walker.nextNode();
    while (node) {
        if (node === container) {
            foundContainer = true;
        } else if (foundContainer && hasDurableCharacter(node) && !isInsideRuntimeNode(node)) {
            probe.setStart(node, 0);
            probe.setEnd(node, nextCodeUnitBoundary(node.data, 0));
            return probe;
        }
        node = walker.nextNode();
    }
    if (isCharacterData(container) && offset > 0) {
        probe.setStart(container, previousCodeUnitBoundary(container.data, offset));
    }
    return probe;
}

export function nextCodeUnitBoundary(text: string, offset: number): number {
    const first = text.charCodeAt(offset);
    const second = text.charCodeAt(offset + 1);
    return offset + (isHighSurrogate(first) && isLowSurrogate(second) ? 2 : 1);
}

export function previousCodeUnitBoundary(text: string, offset: number): number {
    const previous = text.charCodeAt(offset - 1);
    const beforePrevious = text.charCodeAt(offset - 2);
    return offset - (isLowSurrogate(previous) && isHighSurrogate(beforePrevious) ? 2 : 1);
}

