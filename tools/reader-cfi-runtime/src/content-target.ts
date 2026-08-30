import type { ColibrioResolvedTarget } from "./colibrio";
import { resolvePackageTarget, verifiedContentResourceUrl } from "./package-cfi";
import {
    createPublicationSnapshot,
    isCharacterData,
    isOwnedRuntimeNode,
    validateTextBoundary
} from "./publication-dom";
import {
    containsDurableText,
    publicationBody,
    takeFirstCodeUnitSafe,
    takeLastCodeUnitSafe,
    textAfterPosition,
    textContext,
    type QuoteContext
} from "./quote-context";
import {
    CONTEXT_LENGTH,
    MOVEMENT_QUOTE_LENGTH,
    SELECTION_CONTEXT_LENGTH,
    targetKind,
    type TargetKind,
    validateSupportedFullCfi
} from "./protocol";
import { visibilityProbeRange } from "./bookmark-visibility";
import {
    publicationViewportHeight,
    publicationViewportWidth,
    requireReflowableCfiDocument
} from "./visible-position";

export interface MovementAnchor {
    readonly exact: string;
    readonly before: string | null;
    readonly after: string | null;
}
export interface ContentResolution {
    readonly kind: TargetKind;
    readonly selectedText?: string | null;
    readonly prefix?: string | null;
    readonly suffix?: string | null;
    readonly movementAnchor?: MovementAnchor;
}
export interface ContentTargetDetails {
    readonly liveRange: Range;
    readonly resolution: ContentResolution;
}
interface CachedContentTarget {
    readonly fullCfi: string;
    readonly packagePath: string;
    readonly spineIndex: number;
    readonly idref: string;
    readonly itemrefId: string | null;
    readonly resourceHref: string;
    readonly details: ContentTargetDetails;
}
let lastResolvedContentTarget: CachedContentTarget | null = null;

export function resolveContentTargetDetails(
    fullCfi: string,
    packageDocumentXml: string,
    packagePath: string,
    expectedSpineIndex: number,
    expectedIdref: string,
    expectedItemrefId: string | null,
    expectedResourceHref: string,
    geometryOnly?: boolean
): ContentTargetDetails {
    requireReflowableCfiDocument();
    const packageTarget = resolvePackageTarget(
        fullCfi,
        packageDocumentXml,
        packagePath
    );
    const resourceUrl = verifiedContentResourceUrl(
        packageTarget,
        packagePath,
        expectedSpineIndex,
        expectedIdref,
        expectedItemrefId,
        expectedResourceHref
    );
    const expectedKind = targetKind(validateSupportedFullCfi(fullCfi));
    const snapshot = createPublicationSnapshot(document);
    let remainingIndirection;
    try {
        remainingIndirection = packageTarget.resolver.continueResolving(
            snapshot.document,
            resourceUrl
        );
    } catch (_error: unknown) {
        throw new Error("DOM_TARGET_NOT_FOUND");
    }
    if (remainingIndirection) {
        throw new Error("UNSUPPORTED_CFI_FEATURE");
    }
    const resolvedTarget = packageTarget.resolver.getResolvedTarget();
    if (resolvedTarget.getParserErrors().length > 0 ||
        resolvedTarget.getResolverErrors().length > 0 ||
        resolvedTarget.indirectionErrors.length > 0 ||
        resolvedTarget.hasErrors() ||
        !resolvedTarget.isEveryIndirectionResolved() ||
        !resolvedTarget.isEveryStepAndOffsetParsed() ||
        !resolvedTarget.isEveryStepResolved() ||
        !resolvedTarget.isOwnedBySingleDocument()) {
        throw new Error("DOM_TARGET_NOT_FOUND");
    }
    let snapshotRange: Range;
    try {
        snapshotRange = resolvedTarget.createDomRange();
    } catch (_error: unknown) {
        throw new Error(expectedKind === "range" ? "INVALID_RANGE" : "DOM_TARGET_NOT_FOUND");
    }
    const snapshotBody = publicationBody(snapshot.document);
    if (!snapshotRange || !snapshotBody ||
        !snapshotBody.contains(snapshotRange.startContainer) ||
        !snapshotBody.contains(snapshotRange.endContainer)) {
        throw new Error("DOM_TARGET_NOT_FOUND");
    }
    validateResolvedRange(snapshotRange, resolvedTarget, expectedKind);

    const liveRange = snapshot.toLiveRange(snapshotRange);
    validateResolvedRange(liveRange, resolvedTarget, expectedKind);
    if (liveRange.toString() !== snapshotRange.toString()) {
        throw new Error("DOM_TARGET_NOT_FOUND");
    }

    if (geometryOnly === true) {
        return {
            liveRange: liveRange,
            resolution: { kind: expectedKind }
        };
    }

    // Resolution and live selection expose the same raw quote-context contract.
    // The smaller CONTEXT_LENGTH remains reserved for CFI repair/movement assertions.
    const context = textContext(
        snapshotRange,
        snapshot.document,
        SELECTION_CONTEXT_LENGTH
    );
    const movementAnchor = createMovementAnchor(
        snapshotRange,
        snapshot.document,
        context
    );
    return {
        liveRange: liveRange,
        resolution: {
            kind: expectedKind,
            selectedText: context.selectedText,
            prefix: context.prefix,
            suffix: context.suffix,
            movementAnchor: movementAnchor
        }
    };
}

export function resolveContentTarget(
    fullCfi: string,
    packageDocumentXml: string,
    packagePath: string,
    expectedSpineIndex: number,
    expectedIdref: string,
    expectedItemrefId: string | null,
    expectedResourceHref: string
): ContentResolution {
    const details = resolveContentTargetDetails(
        fullCfi, packageDocumentXml, packagePath, expectedSpineIndex, expectedIdref,
        expectedItemrefId, expectedResourceHref
    );
    rememberResolvedContentTarget(
        fullCfi, packagePath, expectedSpineIndex, expectedIdref, expectedItemrefId,
        expectedResourceHref, details
    );
    return details.resolution;
}

export function rememberResolvedContentTarget(
    fullCfi: string,
    packagePath: string,
    spineIndex: number,
    idref: string,
    itemrefId: string | null,
    resourceHref: string,
    details: ContentTargetDetails
): void {
    lastResolvedContentTarget = {
        fullCfi, packagePath, spineIndex, idref, itemrefId, resourceHref, details
    };
}

export function resolvedContentTargetForVerification(
    fullCfi: string,
    expectedResourceHref: string
): ContentTargetDetails {
    const cached = lastResolvedContentTarget;
    if (cached && cached.fullCfi === fullCfi &&
        cached.resourceHref === expectedResourceHref &&
        rangeBelongsToDocument(cached.details.liveRange, document)) {
        return cached.details;
    }
    throw new Error("DOM_TARGET_NOT_FOUND");
}

export function rangeBelongsToDocument(range: Range, publicationDocument: Document): boolean {
    const body = publicationBody(publicationDocument);
    return Boolean(body && body.contains(range.startContainer) &&
        body.contains(range.endContainer));
}

export function verifyContentTarget(
    fullCfi: string,
    expectedResourceHref: string,
    expectedKind: TargetKind,
    expectedSelectedText: string | null,
    expectedPrefix: string | null,
    expectedSuffix: string | null,
    expectedExact: string,
    expectedBefore: string | null,
    expectedAfter: string | null
): { readonly semanticMatch: boolean; readonly visible: boolean } {
    const details = resolvedContentTargetForVerification(
        fullCfi,
        expectedResourceHref
    );
    const resolution = details.resolution;
    const anchor = resolution.movementAnchor;
    const semanticMatch = anchor !== undefined && resolution.kind === expectedKind &&
        resolution.selectedText === expectedSelectedText &&
        resolution.prefix === expectedPrefix &&
        resolution.suffix === expectedSuffix &&
        anchor.exact === expectedExact && anchor.before === expectedBefore &&
        anchor.after === expectedAfter;
    return {
        semanticMatch: semanticMatch,
        visible: semanticMatch && isTargetRangeVisible(details.liveRange, document)
    };
}

export function isTargetRangeVisible(range: Range, publicationDocument: Document): boolean {
    const probe = visibilityProbeRange(range, publicationDocument);
    const viewportWidth = publicationViewportWidth(publicationDocument);
    const viewportHeight = publicationViewportHeight(publicationDocument);
    const rectangles = Array.from(probe.getClientRects());
    if (rectangles.length === 0) {
        rectangles.push(probe.getBoundingClientRect());
    }
    return rectangles.some(function (rectangle) {
        const visibleWidth = Math.min(rectangle.right, viewportWidth) -
            Math.max(rectangle.left, 0);
        const visibleHeight = Math.min(rectangle.bottom, viewportHeight) -
            Math.max(rectangle.top, 0);
        return visibleWidth > 0.5 && visibleHeight > 0.5 &&
            rectangle.width > 0 && rectangle.height > 0;
    });
}

export function hasDurableCharacter(node: Node): node is CharacterData {
    return isCharacterData(node) && /\S/u.test(node.data);
}

export function isInsideRuntimeNode(node: Node): boolean {
    let candidate: Node | null = node;
    while (candidate && candidate !== document) {
        if (isOwnedRuntimeNode(candidate, document)) {
            return true;
        }
        candidate = candidate.parentNode;
    }
    return false;
}

export function validateResolvedRange(
    range: Range,
    resolvedTarget: ColibrioResolvedTarget,
    expectedKind: TargetKind
): void {
    if (!isTextPosition(range.startContainer, range.startOffset) ||
        !isTextPosition(range.endContainer, range.endOffset)) {
        throw new Error("DOM_TARGET_NOT_FOUND");
    }
    validateTextBoundary(range.startContainer, range.startOffset);
    validateTextBoundary(range.endContainer, range.endOffset);
    if (expectedKind === "range") {
        if (!resolvedTarget.hasRangePaths() || !resolvedTarget.isDomRange() ||
            range.collapsed || range.toString().length === 0) {
            throw new Error("INVALID_RANGE");
        }
    } else if (resolvedTarget.hasRangePaths() || !range.collapsed) {
        throw new Error("INVALID_RANGE");
    }
}

export function createMovementAnchor(
    range: Range,
    publicationDocument: Document,
    context: QuoteContext
): MovementAnchor {
    const exactSource = range.collapsed
        ? textFollowingRange(
            range,
            publicationDocument,
            MOVEMENT_QUOTE_LENGTH + CONTEXT_LENGTH
        )
        : context.selectedText ?? "";
    const exact = takeFirstCodeUnitSafe(exactSource, MOVEMENT_QUOTE_LENGTH);
    if (!exact || !containsDurableText(exact)) {
        throw new Error(
            range.collapsed
                ? "UNSUPPORTED_CFI_FEATURE"
                : "MOVEMENT_ANCHOR_UNAVAILABLE"
        );
    }
    const trailingText = range.collapsed
        ? exactSource.slice(exact.length)
        : (context.selectedText ?? "").slice(exact.length) +
            textFollowingRange(range, publicationDocument, CONTEXT_LENGTH);
    return {
        exact: exact,
        before: context.prefix === null
            ? null
            : takeLastCodeUnitSafe(context.prefix, CONTEXT_LENGTH),
        after: takeFirstCodeUnitSafe(trailingText, CONTEXT_LENGTH)
    };
}

export function textFollowingRange(
    range: Range,
    publicationDocument: Document,
    limit: number
): string {
    const body = publicationBody(publicationDocument);
    if (!body) {
        throw new Error("DOM_TARGET_NOT_FOUND");
    }
    return textAfterPosition(
        range.endContainer,
        range.endOffset,
        body,
        publicationDocument,
        limit
    ) || "";
}

export function isTextPosition(container: Node, offset: number): container is CharacterData {
    if (container.nodeType !== Node.TEXT_NODE &&
        container.nodeType !== Node.CDATA_SECTION_NODE) {
        return false;
    }
    return Number.isInteger(offset) && offset >= 0 &&
        offset <= (container as CharacterData).length;
}

