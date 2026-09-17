import { visiblePointTargets } from "./bookmark-visibility";
import { resolveContentTarget, verifyContentTarget } from "./content-target";
import {
    generatePackageCfi,
    resolvePackageCandidates,
    resolvePackageTarget,
    serializeComponent
} from "./package-cfi";
import { publicationBody } from "./quote-context";
import {
    parseCfi,
    safely,
    targetKind
} from "./protocol";
import { generateSelectionContentCfi } from "./selection";
import { generateVisiblePositionContentCfi } from "./visible-position";

import { Protocol as P, type RuntimeFacade } from "./protocol.generated";

declare global {
    interface Window {
        [P.GLOBAL]?: RuntimeFacade;
    }
}

const existing = window[P.GLOBAL];
if (!existing || existing[P.METHOD_RUNTIME_VERSION]() !== P.RUNTIME_VERSION) {
    const runtime: RuntimeFacade = {
        [P.METHOD_IS_DOCUMENT_READY]: () => safely(() => {
            if (window.readium?.isFixedLayout === true) {
                throw new Error(P.ERROR_UNSUPPORTED_FIXED_LAYOUT);
            }
            return document.readyState !== "loading" && document.documentElement !== null &&
                publicationBody(document) !== null && Boolean(window.readium) &&
                window.readium?.isReflowable === true;
        }),
        [P.METHOD_RUNTIME_VERSION]: () => P.RUNTIME_VERSION,
        [P.METHOD_PARSE]: (cfi) => safely(() => {
            const root = parseCfi(cfi);
            return {
                kind: targetKind(root),
                hasIndirection: root.parentPath.localPaths.some((path) => path.indirection)
            };
        }),
        [P.METHOD_RESOLVE_PACKAGE]: (cfi, packageXml, packagePath) => safely(() => {
            const target = resolvePackageTarget(cfi, packageXml, packagePath);
            return {
                itemrefId: target.itemref.getAttribute("id"),
                idref: target.idref,
                spineIndex: target.spineIndex,
                kind: targetKind(parseCfi(cfi))
            };
        }),
        [P.METHOD_RESOLVE_PACKAGE_CANDIDATES]: (
            serializedCandidates, packageXml, packagePath
        ) => safely(() => resolvePackageCandidates(serializedCandidates, packageXml, packagePath)),
        [P.METHOD_GENERATE_PACKAGE]: (
            packageXml, packagePath, spineIndex, idref, itemrefId
        ) => safely(() => generatePackageCfi(packageXml, packagePath, spineIndex, idref, itemrefId)),
        [P.METHOD_RESOLVE_CONTENT]: (
            cfi, packageXml, packagePath, spineIndex, idref, itemrefId, resourceHref
        ) => safely(() => resolveContentTarget(
            cfi, packageXml, packagePath, spineIndex, idref, itemrefId, resourceHref
        )),
        [P.METHOD_VERIFY_CONTENT_TARGET]: (
            cfi, resourceHref, kind, selectedText, prefix, suffix, exact, before, after
        ) => safely(() => verifyContentTarget(
            cfi, resourceHref, kind, selectedText, prefix, suffix, exact, before, after
        )),
        [P.METHOD_VISIBLE_POINT_TARGETS]: (
            serializedCandidates, packageXml, packagePath, spineIndex, idref, itemrefId, resourceHref
        ) => safely(() => visiblePointTargets(
            serializedCandidates, packageXml, packagePath, spineIndex, idref, itemrefId, resourceHref
        )),
        [P.METHOD_GENERATE_SELECTION_CONTENT_CFI]: () => safely(generateSelectionContentCfi),
        [P.METHOD_GENERATE_VISIBLE_POSITION_CONTENT_CFI]: () => safely(generateVisiblePositionContentCfi),
        [P.METHOD_COMPOSE_FULL_CFI]: (packageCfi, contentCfi) => safely(() => {
            const full = `epubcfi(${serializeComponent(packageCfi)}!${serializeComponent(contentCfi)})`;
            parseCfi(full);
            return full;
        })
    };
    window[P.GLOBAL] = Object.freeze(runtime);
}
