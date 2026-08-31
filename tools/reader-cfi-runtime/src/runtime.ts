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
    RUNTIME_VERSION,
    safely,
    targetKind,
    type RuntimeResult
} from "./protocol";
import { generateSelectionContentCfi } from "./selection";
import { generateVisiblePositionContentCfi } from "./visible-position";

interface RuntimeFacade {
    runtimeVersion(): string;
    isDocumentReady(): RuntimeResult<boolean>;
    parse(cfi: string): RuntimeResult<unknown>;
    resolvePackage(cfi: string, packageXml: string, packagePath: string): RuntimeResult<unknown>;
    resolvePackageCandidates(
        serializedCandidates: string,
        packageXml: string,
        packagePath: string
    ): RuntimeResult<unknown>;
    generatePackage(
        packageXml: string,
        packagePath: string,
        spineIndex: number,
        idref: string,
        itemrefId: string | null
    ): RuntimeResult<string>;
    resolveContent(
        cfi: string,
        packageXml: string,
        packagePath: string,
        spineIndex: number,
        idref: string,
        itemrefId: string | null,
        resourceHref: string
    ): RuntimeResult<unknown>;
    verifyContentTarget(...values: Parameters<typeof verifyContentTarget>): RuntimeResult<unknown>;
    visiblePointTargets(...values: Parameters<typeof visiblePointTargets>): RuntimeResult<unknown>;
    generateSelectionContentCfi(): RuntimeResult<unknown>;
    generateVisiblePositionContentCfi(): RuntimeResult<string>;
    composeFullCfi(packageCfi: string, contentCfi: string): RuntimeResult<string>;
}

declare global {
    interface Window {
        __secondPassEpubCfi?: RuntimeFacade;
    }
}

const existing = window.__secondPassEpubCfi;
if (!existing || existing.runtimeVersion() !== RUNTIME_VERSION) {
    const runtime: RuntimeFacade = {
        isDocumentReady: () => safely(() => {
            if (window.readium?.isFixedLayout === true) {
                throw new Error("UNSUPPORTED_FIXED_LAYOUT");
            }
            return document.readyState !== "loading" && document.documentElement !== null &&
                publicationBody(document) !== null && Boolean(window.readium) &&
                window.readium?.isReflowable === true;
        }),
        runtimeVersion: () => RUNTIME_VERSION,
        parse: (fullCfi) => safely(() => {
            const root = parseCfi(fullCfi);
            return {
                kind: targetKind(root),
                hasIndirection: root.parentPath.localPaths.some((path) => path.indirection)
            };
        }),
        resolvePackage: (fullCfi, packageDocumentXml, packagePath) => safely(() => {
            const target = resolvePackageTarget(fullCfi, packageDocumentXml, packagePath);
            return {
                itemrefId: target.itemref.getAttribute("id"),
                idref: target.idref,
                spineIndex: target.spineIndex,
                kind: targetKind(parseCfi(fullCfi))
            };
        }),
        resolvePackageCandidates: (...values) => safely(() => resolvePackageCandidates(...values)),
        generatePackage: (
            packageXml, packagePath, spineIndex, expectedIdref, expectedItemrefId
        ) => safely(() => generatePackageCfi(
            packageXml, packagePath, spineIndex, expectedIdref, expectedItemrefId
        )),
        resolveContent: (
            fullCfi, packageXml, packagePath, spineIndex, idref, itemrefId, resourceHref
        ) => safely(() => resolveContentTarget(
            fullCfi, packageXml, packagePath, spineIndex, idref, itemrefId, resourceHref
        )),
        verifyContentTarget: (...values) => safely(() => verifyContentTarget(...values)),
        visiblePointTargets: (...values) => safely(() => visiblePointTargets(...values)),
        generateSelectionContentCfi: () => safely(generateSelectionContentCfi),
        generateVisiblePositionContentCfi: () => safely(generateVisiblePositionContentCfi),
        composeFullCfi: (packageCfi, contentCfi) => safely(() => {
            const full = `epubcfi(${serializeComponent(packageCfi)}!${serializeComponent(contentCfi)})`;
            parseCfi(full);
            return full;
        })
    };
    window.__secondPassEpubCfi = Object.freeze(runtime);
}
