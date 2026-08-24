(function installSecondPassEpubCfiRuntime(global) {
    "use strict";

    const RUNTIME_VERSION = "1.11.0";
    const CONTEXT_LENGTH = 64;
    const MOVEMENT_QUOTE_LENGTH = 128;
    const existing = global.__secondPassEpubCfi;
    if (existing && existing.runtimeVersion() === RUNTIME_VERSION) {
        return;
    }

    const cfi = global.SecondPassColibrio;
    if (!cfi) {
        throw new Error("COLIBRIO_UNAVAILABLE");
    }

    function success(value) {
        return { ok: true, value: value };
    }

    function failure(code) {
        return { ok: false, error: { code: code } };
    }

    function safely(operation) {
        try {
            return success(operation());
        } catch (error) {
            return failure(classifyError(error));
        }
    }

    function classifyError(error) {
        const code = error && typeof error.message === "string" ? error.message : "";
        switch (code) {
            case "INVALID_CFI":
            case "UNSUPPORTED_CFI_FEATURE":
            case "INVALID_PACKAGE_DOCUMENT":
            case "PACKAGE_TARGET_NOT_FOUND":
            case "PACKAGE_TARGET_MISMATCH":
            case "DOM_TARGET_NOT_FOUND":
            case "INVALID_RANGE":
            case "SELECTION_UNAVAILABLE":
            case "VISIBLE_POSITION_UNAVAILABLE":
            case "MOVEMENT_ANCHOR_UNAVAILABLE":
            case "UNSUPPORTED_FIXED_LAYOUT":
            case "UNSUPPORTED_SCROLL_MODE":
            case "UNSUPPORTED_WRITING_MODE":
                return code;
            default:
                return "CFI_RUNTIME_FAILURE";
        }
    }

    function parseCfi(source) {
        if (typeof source !== "string") {
            throw new Error("INVALID_CFI");
        }
        let root;
        try {
            root = cfi.EpubCfiParser.parse(source);
            cfi.EpubCfiValidator.runAllValidations(root);
        } catch (error) {
            throw new Error("INVALID_CFI");
        }
        if (root.errors.length > 0 || !root.parentPath) {
            throw new Error("INVALID_CFI");
        }
        return root;
    }

    function targetKind(root) {
        return root.rangeStartPath && root.rangeEndPath ? "range" : "point";
    }

    function validateSupportedFullCfi(source) {
        const root = parseCfi(source);
        const localPaths = root.parentPath.localPaths;
        const indirectionIndexes = localPaths.reduce(function (indexes, path, index) {
            if (path.indirection) {
                indexes.push(index);
            }
            return indexes;
        }, []);
        if (indirectionIndexes.length !== 1 ||
            indirectionIndexes[0] === 0) {
            throw new Error("UNSUPPORTED_CFI_FEATURE");
        }
        const kind = targetKind(root);
        if (kind === "point" && !isCharacterOffset(root.parentPath.offset)) {
            throw new Error("UNSUPPORTED_CFI_FEATURE");
        }
        if (kind === "range" &&
            (!isCharacterOffset(root.rangeStartPath.offset) ||
                !isCharacterOffset(root.rangeEndPath.offset))) {
            throw new Error("UNSUPPORTED_CFI_FEATURE");
        }
        return root;
    }

    function isCharacterOffset(offset) {
        return offset && offset.type === "CHARACTER";
    }

    function parsePackageDocument(packageDocumentXml) {
        if (typeof packageDocumentXml !== "string" || packageDocumentXml.length === 0) {
            throw new Error("INVALID_PACKAGE_DOCUMENT");
        }
        const packageDocument = new DOMParser().parseFromString(
            packageDocumentXml,
            "application/xml"
        );
        if (packageDocument.querySelector("parsererror")) {
            throw new Error("INVALID_PACKAGE_DOCUMENT");
        }
        return packageDocument;
    }

    function packageUrl(packagePath) {
        if (typeof packagePath !== "string" || packagePath.length === 0) {
            throw new Error("INVALID_PACKAGE_DOCUMENT");
        }
        return new URL(packagePath, "https://secondpass.invalid/");
    }

    function resolvePackageTarget(fullCfi, packageDocumentXml, packagePath) {
        const packageDocument = parsePackageDocument(packageDocumentXml);
        const resolver = new cfi.EpubCfiResolver(validateSupportedFullCfi(fullCfi), {
            processTextAssertions: true,
            textAssertionSearchDistance: 10000
        });
        const indirection = resolver.continueResolving(
            packageDocument,
            packageUrl(packagePath)
        );
        if (!indirection || indirection.element.localName !== "itemref") {
            throw new Error("PACKAGE_TARGET_NOT_FOUND");
        }
        const resolvedTarget = resolver.getResolvedTarget();
        if (resolvedTarget.getParserErrors().length > 0 ||
            resolvedTarget.getResolverErrors().length > 0 ||
            resolvedTarget.getTargetElement() !== indirection.element) {
            throw new Error("PACKAGE_TARGET_MISMATCH");
        }
        const idref = indirection.element.getAttribute("idref");
        if (!idref) {
            throw new Error("PACKAGE_TARGET_NOT_FOUND");
        }
        const itemrefs = packageSpineItemrefs(packageDocument);
        const spineIndex = itemrefs.indexOf(indirection.element);
        if (spineIndex < 0) {
            throw new Error("PACKAGE_TARGET_NOT_FOUND");
        }
        return {
            packageDocument: packageDocument,
            resolver: resolver,
            itemref: indirection.element,
            idref: idref,
            spineIndex: spineIndex
        };
    }

    function verifiedContentResourceUrl(
        packageTarget,
        packagePath,
        expectedSpineIndex,
        expectedIdref,
        expectedItemrefId,
        expectedResourceHref
    ) {
        const itemrefId = packageTarget.itemref.getAttribute("id") || null;
        if (packageTarget.spineIndex !== expectedSpineIndex ||
            packageTarget.idref !== expectedIdref ||
            itemrefId !== expectedItemrefId) {
            throw new Error("PACKAGE_TARGET_MISMATCH");
        }
        if (typeof expectedResourceHref !== "string" ||
            expectedResourceHref.length === 0) {
            throw new Error("PACKAGE_TARGET_MISMATCH");
        }
        const manifestItems = Array.from(
            packageTarget.packageDocument.getElementsByTagNameNS("*", "item")
        ).filter(function (item) {
            return item.getAttribute("id") === packageTarget.idref;
        });
        if (manifestItems.length !== 1) {
            throw new Error("PACKAGE_TARGET_NOT_FOUND");
        }
        const manifestHref = manifestItems[0].getAttribute("href");
        if (!manifestHref) {
            throw new Error("PACKAGE_TARGET_NOT_FOUND");
        }
        let manifestUrl;
        let expectedUrl;
        try {
            manifestUrl = new URL(manifestHref, packageUrl(packagePath));
            expectedUrl = new URL(
                expectedResourceHref,
                "https://secondpass.invalid/"
            );
        } catch (error) {
            throw new Error("PACKAGE_TARGET_MISMATCH");
        }
        if (manifestUrl.href !== expectedUrl.href) {
            throw new Error("PACKAGE_TARGET_MISMATCH");
        }
        return manifestUrl;
    }

    function packageSpineItemrefs(packageDocument) {
        const spines = Array.from(packageDocument.getElementsByTagNameNS("*", "spine"));
        if (spines.length !== 1) {
            throw new Error("INVALID_PACKAGE_DOCUMENT");
        }
        return Array.from(spines[0].children).filter(function (element) {
            return element.localName === "itemref";
        });
    }

    function verifiedPackageItemref(
        packageDocument,
        spineIndex,
        expectedIdref,
        expectedItemrefId
    ) {
        const itemref = packageSpineItemrefs(packageDocument)[spineIndex];
        if (!itemref || itemref.getAttribute("idref") !== expectedIdref) {
            throw new Error("PACKAGE_TARGET_MISMATCH");
        }
        const actualItemrefId = itemref.getAttribute("id") || null;
        if (actualItemrefId !== expectedItemrefId) {
            throw new Error("PACKAGE_TARGET_MISMATCH");
        }
        return itemref;
    }

    function generatePackageCfi(
        packageDocumentXml,
        packagePath,
        spineIndex,
        expectedIdref,
        expectedItemrefId
    ) {
        const packageDocument = parsePackageDocument(packageDocumentXml);
        const itemref = verifiedPackageItemref(
            packageDocument,
            spineIndex,
            expectedIdref,
            expectedItemrefId
        );
        const builder = new cfi.EpubCfiBuilder();
        builder.appendLocalPathTo(itemref);
        const packageCfi = builder.toString();
        const resolver = new cfi.EpubCfiResolver(packageCfi, {
            processTextAssertions: true,
            textAssertionSearchDistance: 10000
        });
        resolver.continueResolving(packageDocument, packageUrl(packagePath));
        const resolvedTarget = resolver.getResolvedTarget();
        if (resolvedTarget.getParserErrors().length > 0 ||
            resolvedTarget.getResolverErrors().length > 0 ||
            resolvedTarget.getTargetElement() !== itemref) {
            throw new Error("PACKAGE_TARGET_MISMATCH");
        }
        return packageCfi;
    }

    function serializeComponent(source) {
        const root = parseCfi(source);
        return cfi.EpubCfiStringifier.stringifyRootNode(root).slice(8, -1);
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
    function isOwnedRuntimeNode(node, liveDocument) {
        return isReadiumDecorationRoot(node, liveDocument) ||
            isReadiumVirtualPage(node, liveDocument) ||
            isReadiumHeadResource(node, liveDocument);
    }

    function isReadiumDecorationRoot(node, liveDocument) {
        return node.nodeType === Node.ELEMENT_NODE &&
            node.parentNode === liveDocument.body &&
            node.localName === "div" &&
            READIUM_DECORATION_ROOT_ID.test(node.id) &&
            node.hasAttribute("data-group") &&
            node.style.pointerEvents === "none";
    }

    function isReadiumVirtualPage(node, liveDocument) {
        return node.nodeType === Node.ELEMENT_NODE &&
            node.parentNode === liveDocument.body &&
            node.localName === "div" &&
            node.id === "readium-virtual-page" &&
            node.style.breakBefore === "column" &&
            node.textContent === "\u200b";
    }

    function isReadiumHeadResource(node, liveDocument) {
        if (node.nodeType !== Node.ELEMENT_NODE || node.parentNode !== liveDocument.head) {
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

    function resourcePath(resourceUrl) {
        if (!resourceUrl) {
            return "";
        }
        try {
            return new URL(resourceUrl, document.baseURI).pathname;
        } catch (error) {
            return "";
        }
    }

    function createPublicationSnapshot(liveDocument) {
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
            toSnapshotNode: function toSnapshotNode(liveNode) {
                return liveToSnapshot.get(liveNode) || null;
            },
            toLiveNode: function toLiveNode(snapshotNode) {
                return snapshotToLive.get(snapshotNode) || null;
            },
            toSnapshotRange: function toSnapshotRange(liveRange) {
                return translateRange(
                    liveRange,
                    snapshotDocument,
                    liveToSnapshot,
                    "live"
                );
            },
            toLiveRange: function toLiveRange(snapshotRange) {
                return translateRange(
                    snapshotRange,
                    liveDocument,
                    snapshotToLive,
                    "snapshot"
                );
            }
        });
    }

    function clonePublicationNode(
        liveNode,
        snapshotParent,
        snapshotDocument,
        liveDocument,
        liveToSnapshot,
        snapshotToLive
    ) {
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

    function translateRange(sourceRange, targetDocument, nodeMap, sourceKind) {
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

    function translateBoundary(sourceContainer, sourceOffset, nodeMap, sourceKind) {
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

    function isCharacterData(node) {
        return node.nodeType === Node.TEXT_NODE ||
            node.nodeType === Node.CDATA_SECTION_NODE ||
            node.nodeType === Node.COMMENT_NODE;
    }

    function liveBoundaryOffset(liveChildren, liveOffset, liveToSnapshot, snapshotParent) {
        return liveChildren.slice(0, liveOffset).reduce(function (offset, liveChild) {
            const snapshotChild = liveToSnapshot.get(liveChild);
            return snapshotChild && snapshotChild.parentNode === snapshotParent
                ? offset + 1
                : offset;
        }, 0);
    }

    function snapshotBoundaryOffset(
        snapshotChildren,
        snapshotOffset,
        snapshotToLive,
        liveParent
    ) {
        if (snapshotOffset < snapshotChildren.length) {
            return liveChildIndex(
                snapshotChildren[snapshotOffset],
                snapshotToLive,
                liveParent
            );
        }
        if (snapshotChildren.length === 0) {
            return 0;
        }
        return liveChildIndex(
            snapshotChildren[snapshotChildren.length - 1],
            snapshotToLive,
            liveParent
        ) + 1;
    }

    function liveChildIndex(snapshotChild, snapshotToLive, liveParent) {
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
    function validateTextBoundary(container, offset) {
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

    function isHighSurrogate(codeUnit) {
        return codeUnit >= 0xd800 && codeUnit <= 0xdbff;
    }

    function isLowSurrogate(codeUnit) {
        return codeUnit >= 0xdc00 && codeUnit <= 0xdfff;
    }

    function textContext(range, publicationDocument) {
        validateTextBoundary(range.startContainer, range.startOffset);
        validateTextBoundary(range.endContainer, range.endOffset);
        const body = publicationBody(publicationDocument);
        if (!body || !body.contains(range.startContainer) ||
            !body.contains(range.endContainer)) {
            throw new Error("DOM_TARGET_NOT_FOUND");
        }
        const before = publicationDocument.createRange();
        before.selectNodeContents(body);
        before.setEnd(range.startContainer, range.startOffset);
        const after = publicationDocument.createRange();
        after.selectNodeContents(body);
        after.setStart(range.endContainer, range.endOffset);
        return {
            selectedText: range.collapsed ? null : range.toString(),
            prefix: takeLastCodeUnitSafe(before.toString(), CONTEXT_LENGTH),
            suffix: takeFirstCodeUnitSafe(after.toString(), CONTEXT_LENGTH)
        };
    }

    function resolveContentTargetDetails(
        fullCfi,
        packageDocumentXml,
        packagePath,
        expectedSpineIndex,
        expectedIdref,
        expectedItemrefId,
        expectedResourceHref
    ) {
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
        } catch (error) {
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
        let snapshotRange;
        try {
            snapshotRange = resolvedTarget.createDomRange();
        } catch (error) {
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

        const context = textContext(snapshotRange, snapshot.document);
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

    function resolveContentTarget() {
        return resolveContentTargetDetails.apply(null, arguments).resolution;
    }

    function verifyContentTarget(
        fullCfi,
        packageDocumentXml,
        packagePath,
        expectedSpineIndex,
        expectedIdref,
        expectedItemrefId,
        expectedResourceHref,
        expectedKind,
        expectedSelectedText,
        expectedPrefix,
        expectedSuffix,
        expectedExact,
        expectedBefore,
        expectedAfter
    ) {
        const details = resolveContentTargetDetails(
            fullCfi,
            packageDocumentXml,
            packagePath,
            expectedSpineIndex,
            expectedIdref,
            expectedItemrefId,
            expectedResourceHref
        );
        const resolution = details.resolution;
        const semanticMatch = resolution.kind === expectedKind &&
            resolution.selectedText === expectedSelectedText &&
            resolution.prefix === expectedPrefix &&
            resolution.suffix === expectedSuffix &&
            resolution.movementAnchor.exact === expectedExact &&
            resolution.movementAnchor.before === expectedBefore &&
            resolution.movementAnchor.after === expectedAfter;
        return {
            semanticMatch: semanticMatch,
            visible: semanticMatch && isTargetRangeVisible(details.liveRange, document)
        };
    }

    function isTargetRangeVisible(range, publicationDocument) {
        const probe = visibilityProbeRange(range, publicationDocument);
        const viewportWidth = global.innerWidth || publicationDocument.documentElement.clientWidth;
        const viewportHeight = global.innerHeight || publicationDocument.documentElement.clientHeight;
        return Array.from(probe.getClientRects()).some(function (rectangle) {
            const visibleWidth = Math.min(rectangle.right, viewportWidth) -
                Math.max(rectangle.left, 0);
            const visibleHeight = Math.min(rectangle.bottom, viewportHeight) -
                Math.max(rectangle.top, 0);
            return visibleWidth > 0.5 && visibleHeight > 0.5 &&
                rectangle.width > 0 && rectangle.height > 0;
        });
    }

    function visibilityProbeRange(range, publicationDocument) {
        if (!range.collapsed || range.getClientRects().length > 0) {
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
        const walker = publicationDocument.createTreeWalker(body, NodeFilter.SHOW_TEXT);
        let foundContainer = false;
        let node = walker.nextNode();
        while (node) {
            if (node === container) {
                foundContainer = true;
            } else if (foundContainer && node.length > 0 && !isInsideRuntimeNode(node)) {
                probe.setStart(node, 0);
                probe.setEnd(node, nextCodeUnitBoundary(node.data, 0));
                return probe;
            }
            node = walker.nextNode();
        }
        return probe;
    }

    function nextCodeUnitBoundary(text, offset) {
        const first = text.charCodeAt(offset);
        const second = text.charCodeAt(offset + 1);
        return offset + (isHighSurrogate(first) && isLowSurrogate(second) ? 2 : 1);
    }

    function isInsideRuntimeNode(node) {
        let candidate = node;
        while (candidate && candidate !== document) {
            if (isOwnedRuntimeNode(candidate, document)) {
                return true;
            }
            candidate = candidate.parentNode;
        }
        return false;
    }

    function validateResolvedRange(range, resolvedTarget, expectedKind) {
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

    function createMovementAnchor(range, publicationDocument, context) {
        const exactSource = range.collapsed
            ? textFollowingRange(range, publicationDocument)
            : range.toString();
        const exact = takeFirstCodeUnitSafe(exactSource, MOVEMENT_QUOTE_LENGTH);
        if (!exact || !containsDurableText(exact)) {
            throw new Error("MOVEMENT_ANCHOR_UNAVAILABLE");
        }
        const trailingText = range.collapsed
            ? exactSource.slice(exact.length)
            : range.toString().slice(exact.length) +
                textFollowingRange(range, publicationDocument);
        return {
            exact: exact,
            before: context.prefix,
            after: takeFirstCodeUnitSafe(trailingText, CONTEXT_LENGTH)
        };
    }

    function textFollowingRange(range, publicationDocument) {
        const body = publicationBody(publicationDocument);
        if (!body) {
            throw new Error("DOM_TARGET_NOT_FOUND");
        }
        const following = publicationDocument.createRange();
        following.selectNodeContents(body);
        following.setStart(range.endContainer, range.endOffset);
        return following.toString();
    }

    function publicationBody(publicationDocument) {
        return publicationDocument.getElementsByTagNameNS("*", "body")[0] || null;
    }

    function takeLastCodeUnitSafe(value, limit) {
        let start = Math.max(0, value.length - limit);
        if (start > 0 && isLowSurrogate(value.charCodeAt(start)) &&
            isHighSurrogate(value.charCodeAt(start - 1))) {
            start += 1;
        }
        return value.slice(start) || null;
    }

    function takeFirstCodeUnitSafe(value, limit) {
        let end = Math.min(value.length, limit);
        if (end < value.length && end > 0 &&
            isHighSurrogate(value.charCodeAt(end - 1)) &&
            isLowSurrogate(value.charCodeAt(end))) {
            end -= 1;
        }
        return value.slice(0, end) || null;
    }

    function generateSelectionContentCfi() {
        requireReflowableCfiDocument();
        const selection = global.getSelection();
        if (!selection || selection.rangeCount === 0 || selection.isCollapsed) {
            return null;
        }
        if (selection.rangeCount !== 1) {
            throw new Error("INVALID_RANGE");
        }
        const liveRange = selection.getRangeAt(0);
        if (liveRange.collapsed) {
            return null;
        }
        const snapshot = createPublicationSnapshot(document);
        const snapshotRange = snapshot.toSnapshotRange(liveRange);
        if (snapshotRange.collapsed) {
            throw new Error("SELECTION_UNAVAILABLE");
        }
        const context = textContext(snapshotRange, snapshot.document);
        if (context.selectedText === null) {
            throw new Error("SELECTION_UNAVAILABLE");
        }
        const builder = new cfi.EpubCfiBuilder();
        builder.setTextAssertionOptions({
            preLength: CONTEXT_LENGTH,
            postLength: CONTEXT_LENGTH,
            snapToWordBoundaries: false
        });
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

    /*
     * Colibrio 1.1.0 emits an invalid compact range when a DOM Range's common
     * ancestor is itself a text node (`/1,:start,:end`). Supplying the same
     * boundaries with their parent element as the common ancestor produces the
     * standards-shaped equivalent (`,/1:start,/1:end`) without altering the
     * selected content or offsets.
     */
    function rangeForCfiBuilder(range) {
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
    function generateVisiblePositionContentCfi() {
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

        const builder = new cfi.EpubCfiBuilder();
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

    function visiblePositionMode() {
        requireReflowableCfiDocument();
        const root = document.documentElement;
        const style = root.style;
        if (style.getPropertyValue("--USER__view").trim() === "readium-scroll-on" ||
            style.getPropertyValue("--USER__scroll").trim() === "readium-scroll-on") {
            throw new Error("UNSUPPORTED_SCROLL_MODE");
        }
        const rootStyle = global.getComputedStyle(root);
        const writingMode = rootStyle.getPropertyValue("writing-mode").trim();
        if (writingMode !== "horizontal-tb") {
            throw new Error("UNSUPPORTED_WRITING_MODE");
        }
        const bodyDirection = global.getComputedStyle(document.body)
            .getPropertyValue("direction")
            .trim()
            .toLowerCase();
        if (bodyDirection !== "ltr" && bodyDirection !== "rtl") {
            throw new Error("UNSUPPORTED_WRITING_MODE");
        }
        return { direction: bodyDirection };
    }

    function requireReflowableCfiDocument() {
        if (!global.readium || global.readium.isFixedLayout === true ||
            global.readium.isReflowable !== true) {
            throw new Error("UNSUPPORTED_FIXED_LAYOUT");
        }
    }

    function firstVisibleTextBoundary(liveDocument, direction) {
        const body = liveDocument.body;
        if (!body || global.innerWidth <= 0 || global.innerHeight <= 0) {
            return null;
        }
        const walker = liveDocument.createTreeWalker(
            body,
            NodeFilter.SHOW_TEXT,
            null
        );
        let textNode = walker.nextNode();
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
            textNode = walker.nextNode();
        }
        return null;
    }

    function isInsideOwnedRuntimeNode(node, liveDocument) {
        let ancestor = node.parentNode;
        while (ancestor && ancestor !== liveDocument.body) {
            if (isOwnedRuntimeNode(ancestor, liveDocument)) {
                return true;
            }
            ancestor = ancestor.parentNode;
        }
        return false;
    }

    function nodeIntersectsViewport(textNode, liveDocument, direction) {
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

    function firstVisibleCharacterOffset(textNode, liveDocument, direction) {
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
            const codePoint = textNode.data.codePointAt(offset);
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

    function codePointStart(value, offset) {
        return offset > 0 && isLowSurrogate(value.charCodeAt(offset)) &&
            isHighSurrogate(value.charCodeAt(offset - 1))
            ? offset - 1
            : offset;
    }

    function isRenderedTextNode(textNode) {
        let element = textNode.parentElement;
        while (element) {
            const style = global.getComputedStyle(element);
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

    function containsDurableText(value) {
        return /[^\s\u200b\u200c\u200d\ufeff]/u.test(value);
    }

    function hasPositiveViewportIntersection(rect, direction) {
        const blockStart = Math.max(0, rect.top);
        const blockEnd = Math.min(global.innerHeight, rect.bottom);
        const inlineStart = direction === "rtl"
            ? Math.min(global.innerWidth, rect.right)
            : Math.max(0, rect.left);
        const inlineEnd = direction === "rtl"
            ? Math.max(0, rect.left)
            : Math.min(global.innerWidth, rect.right);
        const inlineExtent = direction === "rtl"
            ? inlineStart - inlineEnd
            : inlineEnd - inlineStart;
        return blockEnd > blockStart && inlineExtent > 0 && rect.width > 0 && rect.height > 0;
    }

    function isTextPosition(container, offset) {
        return (container.nodeType === Node.TEXT_NODE ||
            container.nodeType === Node.CDATA_SECTION_NODE) &&
            Number.isInteger(offset) &&
            offset >= 0 &&
            offset <= container.length;
    }

    global.__secondPassEpubCfi = Object.freeze({
        runtimeVersion: function runtimeVersion() {
            return RUNTIME_VERSION;
        },

        parse: function parse(fullCfi) {
            return safely(function () {
                const root = parseCfi(fullCfi);
                return {
                    kind: targetKind(root),
                    hasIndirection: root.parentPath.localPaths.some(function (path) {
                        return path.indirection;
                    })
                };
            });
        },

        resolvePackage: function resolvePackage(fullCfi, packageDocumentXml, packagePath) {
            return safely(function () {
                const target = resolvePackageTarget(fullCfi, packageDocumentXml, packagePath);
                return {
                    itemrefId: target.itemref.getAttribute("id"),
                    idref: target.idref,
                    spineIndex: target.spineIndex,
                    kind: targetKind(parseCfi(fullCfi))
                };
            });
        },

        generatePackage: function generatePackage(
            packageDocumentXml,
            packagePath,
            spineIndex,
            expectedIdref,
            expectedItemrefId
        ) {
            return safely(function () {
                return generatePackageCfi(
                    packageDocumentXml,
                    packagePath,
                    spineIndex,
                    expectedIdref,
                    expectedItemrefId
                );
            });
        },

        resolveContent: function resolveContent(
            fullCfi,
            packageDocumentXml,
            packagePath,
            expectedSpineIndex,
            expectedIdref,
            expectedItemrefId,
            expectedResourceHref
        ) {
            return safely(function () {
                return resolveContentTarget(
                    fullCfi,
                    packageDocumentXml,
                    packagePath,
                    expectedSpineIndex,
                    expectedIdref,
                    expectedItemrefId,
                    expectedResourceHref
                );
            });
        },

        verifyContentTarget: function verifyResolvedContentTarget(
            fullCfi,
            packageDocumentXml,
            packagePath,
            expectedSpineIndex,
            expectedIdref,
            expectedItemrefId,
            expectedResourceHref,
            expectedKind,
            expectedSelectedText,
            expectedPrefix,
            expectedSuffix,
            expectedExact,
            expectedBefore,
            expectedAfter
        ) {
            return safely(function () {
                return verifyContentTarget(
                    fullCfi,
                    packageDocumentXml,
                    packagePath,
                    expectedSpineIndex,
                    expectedIdref,
                    expectedItemrefId,
                    expectedResourceHref,
                    expectedKind,
                    expectedSelectedText,
                    expectedPrefix,
                    expectedSuffix,
                    expectedExact,
                    expectedBefore,
                    expectedAfter
                );
            });
        },

        generateSelectionContentCfi: function generateSelection() {
            return safely(generateSelectionContentCfi);
        },

        generateVisiblePositionContentCfi: function generateVisiblePosition() {
            return safely(generateVisiblePositionContentCfi);
        },

        composeFullCfi: function composeFullCfi(packageCfi, contentCfi) {
            return safely(function () {
                const full = "epubcfi(" + serializeComponent(packageCfi) + "!" +
                    serializeComponent(contentCfi) + ")";
                parseCfi(full);
                return full;
            });
        }
    });
})(window);
