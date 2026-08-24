(function installSecondPassEpubCfiRuntime(global) {
    "use strict";

    const RUNTIME_VERSION = "1.3.0";
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
            case "UNSUPPORTED_FIXED_LAYOUT":
                return code;
            default:
                return "CFI_RUNTIME_FAILURE";
        }
    }

    function parseCfi(source) {
        if (typeof source !== "string") {
            throw new Error("INVALID_CFI");
        }
        const root = cfi.EpubCfiParser.parse(source);
        cfi.EpubCfiValidator.runAllValidations(root);
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
            indirectionIndexes[0] === 0 ||
            indirectionIndexes[0] === localPaths.length - 1) {
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

        resolveContent: function resolveContent() {
            return failure("UNSUPPORTED_CFI_FEATURE");
        },

        generateSelectionContentCfi: function generateSelectionContentCfi() {
            return failure("UNSUPPORTED_CFI_FEATURE");
        },

        generateVisiblePositionContentCfi: function generateVisiblePositionContentCfi() {
            return failure("UNSUPPORTED_CFI_FEATURE");
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
