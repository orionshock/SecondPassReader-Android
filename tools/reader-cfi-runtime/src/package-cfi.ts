import { colibrio, type ColibrioResolver } from "./colibrio";
import { parseCfi, validateSupportedFullCfi } from "./protocol";

export interface PackageTarget {
    readonly packageDocument: Document;
    readonly resolver: ColibrioResolver;
    readonly itemref: Element;
    readonly idref: string;
    readonly spineIndex: number;
}

export function parsePackageDocument(packageDocumentXml: unknown): Document {
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

export function packageUrl(packagePath: unknown): URL {
    if (typeof packagePath !== "string" || packagePath.length === 0) {
        throw new Error("INVALID_PACKAGE_DOCUMENT");
    }
    return new URL(packagePath, "https://secondpass.invalid/");
}

export function resolvePackageTarget(
    fullCfi: string,
    packageDocumentXml: string,
    packagePath: string
): PackageTarget {
    const packageDocument = parsePackageDocument(packageDocumentXml);
    const resolver = new colibrio.EpubCfiResolver(validateSupportedFullCfi(fullCfi), {
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

export function verifiedContentResourceUrl(
    packageTarget: PackageTarget,
    packagePath: string,
    expectedSpineIndex: number,
    expectedIdref: string,
    expectedItemrefId: string | null,
    expectedResourceHref: string,
    _geometryOnly?: boolean
): URL {
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
    const manifestHref = manifestItems[0]!.getAttribute("href");
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

export function packageSpineItemrefs(packageDocument: Document): Element[] {
    const spines = Array.from(packageDocument.getElementsByTagNameNS("*", "spine"));
    if (spines.length !== 1) {
        throw new Error("INVALID_PACKAGE_DOCUMENT");
    }
    return Array.from(spines[0]!.children).filter(function (element) {
        return element.localName === "itemref";
    });
}

export function verifiedPackageItemref(
    packageDocument: Document,
    spineIndex: number,
    expectedIdref: string,
    expectedItemrefId: string | null
): Element {
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

export function generatePackageCfi(
    packageDocumentXml: string,
    packagePath: string,
    spineIndex: number,
    expectedIdref: string,
    expectedItemrefId: string | null
): string {
    const packageDocument = parsePackageDocument(packageDocumentXml);
    const itemref = verifiedPackageItemref(
        packageDocument,
        spineIndex,
        expectedIdref,
        expectedItemrefId
    );
    const builder = new colibrio.EpubCfiBuilder();
    builder.appendLocalPathTo(itemref);
    const packageCfi = builder.toString();
    const resolver = new colibrio.EpubCfiResolver(packageCfi, {
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

export function serializeComponent(source: string): string {
    const root = parseCfi(source);
    return colibrio.EpubCfiStringifier.stringifyRootNode(root).slice(8, -1);
}

