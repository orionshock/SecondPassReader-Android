(function installSecondPassEpubCfiRuntime(global) {
    "use strict";

    const RUNTIME_VERSION = "1.1.0";
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
        const resolver = new cfi.EpubCfiResolver(parseCfi(fullCfi), {
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
