// GENERATED from tools/reader-cfi-runtime; do not edit.
// Source-SHA256: 4FD882DB9B14F5D49E0C67FD7AAD95C8AA3187A7208CCB0C35389178CAD033BD
// CFI-Protocol-Version: 1.12.10
// Rebuild: cd tools/reader-cfi-runtime && npm run build
"use strict";
(() => {
  // src/colibrio.ts
  var colibrio = new Proxy({}, {
    get(_target, property) {
      const installed = window.SecondPassColibrio;
      if (!installed) throw new Error("COLIBRIO_UNAVAILABLE");
      return installed[property];
    }
  });

  // src/protocol.generated.ts
  var Protocol = {
    ARG_AFTER: "after",
    ARG_BEFORE: "before",
    ARG_CFI: "cfi",
    ARG_CONTENT_CFI: "contentCfi",
    ARG_EXACT: "exact",
    ARG_IDREF: "idref",
    ARG_ITEMREF_ID: "itemrefId",
    ARG_KIND: "kind",
    ARG_PACKAGE_CFI: "packageCfi",
    ARG_PACKAGE_PATH: "packagePath",
    ARG_PACKAGE_XML: "packageXml",
    ARG_PREFIX: "prefix",
    ARG_RESOURCE_HREF: "resourceHref",
    ARG_SELECTED_TEXT: "selectedText",
    ARG_SERIALIZED_CANDIDATES: "serializedCandidates",
    ARG_SPINE_INDEX: "spineIndex",
    ARG_SUFFIX: "suffix",
    CONTEXT_LENGTH: 64,
    ERROR_CFI_RUNTIME_FAILURE: "CFI_RUNTIME_FAILURE",
    ERROR_DOM_TARGET_NOT_FOUND: "DOM_TARGET_NOT_FOUND",
    ERROR_INVALID_CFI: "INVALID_CFI",
    ERROR_INVALID_PACKAGE_DOCUMENT: "INVALID_PACKAGE_DOCUMENT",
    ERROR_INVALID_RANGE: "INVALID_RANGE",
    ERROR_MOVEMENT_ANCHOR_UNAVAILABLE: "MOVEMENT_ANCHOR_UNAVAILABLE",
    ERROR_PACKAGE_TARGET_MISMATCH: "PACKAGE_TARGET_MISMATCH",
    ERROR_PACKAGE_TARGET_NOT_FOUND: "PACKAGE_TARGET_NOT_FOUND",
    ERROR_RESULT_TOO_LARGE: "RESULT_TOO_LARGE",
    ERROR_SELECTION_UNAVAILABLE: "SELECTION_UNAVAILABLE",
    ERROR_UNSUPPORTED_CFI_FEATURE: "UNSUPPORTED_CFI_FEATURE",
    ERROR_UNSUPPORTED_FIXED_LAYOUT: "UNSUPPORTED_FIXED_LAYOUT",
    ERROR_UNSUPPORTED_SCROLL_MODE: "UNSUPPORTED_SCROLL_MODE",
    ERROR_UNSUPPORTED_WRITING_MODE: "UNSUPPORTED_WRITING_MODE",
    ERROR_VISIBLE_POSITION_UNAVAILABLE: "VISIBLE_POSITION_UNAVAILABLE",
    FIELD_AFTER: "after",
    FIELD_BEFORE: "before",
    FIELD_CFI: "cfi",
    FIELD_CODE: "code",
    FIELD_CONTENT_CFI: "contentCfi",
    FIELD_ERROR: "error",
    FIELD_EXACT: "exact",
    FIELD_HAS_INDIRECTION: "hasIndirection",
    FIELD_ID: "id",
    FIELD_IDREF: "idref",
    FIELD_ITEMREF_ID: "itemrefId",
    FIELD_KIND: "kind",
    FIELD_MOVEMENT_ANCHOR: "movementAnchor",
    FIELD_OK: "ok",
    FIELD_PREFIX: "prefix",
    FIELD_SELECTED_TEXT: "selectedText",
    FIELD_SEMANTIC_MATCH: "semanticMatch",
    FIELD_SPINE_INDEX: "spineIndex",
    FIELD_SUFFIX: "suffix",
    FIELD_VALUE: "value",
    FIELD_VISIBLE: "visible",
    GLOBAL: "__secondPassEpubCfi",
    KIND_POINT: "point",
    KIND_RANGE: "range",
    MAX_SELECTED_TEXT_LENGTH: 65536,
    METHOD_COMPOSE_FULL_CFI: "composeFullCfi",
    METHOD_GENERATE_PACKAGE: "generatePackage",
    METHOD_GENERATE_SELECTION_CONTENT_CFI: "generateSelectionContentCfi",
    METHOD_GENERATE_VISIBLE_POSITION_CONTENT_CFI: "generateVisiblePositionContentCfi",
    METHOD_IS_DOCUMENT_READY: "isDocumentReady",
    METHOD_PARSE: "parse",
    METHOD_RESOLVE_CONTENT: "resolveContent",
    METHOD_RESOLVE_PACKAGE: "resolvePackage",
    METHOD_RESOLVE_PACKAGE_CANDIDATES: "resolvePackageCandidates",
    METHOD_RUNTIME_VERSION: "runtimeVersion",
    METHOD_VERIFY_CONTENT_TARGET: "verifyContentTarget",
    METHOD_VISIBLE_POINT_TARGETS: "visiblePointTargets",
    MOVEMENT_QUOTE_LENGTH: 128,
    RUNTIME_VERSION: "1.12.10",
    SELECTION_CONTEXT_LENGTH: 2e3
  };

  // src/protocol.ts
  var {
    RUNTIME_VERSION,
    CONTEXT_LENGTH,
    SELECTION_CONTEXT_LENGTH,
    MOVEMENT_QUOTE_LENGTH,
    MAX_SELECTED_TEXT_LENGTH
  } = Protocol;
  var MIN_VISIBLE_EXTENT_PIXELS = 0.5;
  function safely(operation) {
    try {
      return { [Protocol.FIELD_OK]: true, [Protocol.FIELD_VALUE]: operation() };
    } catch (error) {
      return { [Protocol.FIELD_OK]: false, [Protocol.FIELD_ERROR]: { [Protocol.FIELD_CODE]: classifyError(error) } };
    }
  }
  function classifyError(error) {
    const code = error instanceof Error ? error.message : "";
    switch (code) {
      case Protocol.ERROR_INVALID_CFI:
      case Protocol.ERROR_UNSUPPORTED_CFI_FEATURE:
      case Protocol.ERROR_INVALID_PACKAGE_DOCUMENT:
      case Protocol.ERROR_PACKAGE_TARGET_NOT_FOUND:
      case Protocol.ERROR_PACKAGE_TARGET_MISMATCH:
      case Protocol.ERROR_DOM_TARGET_NOT_FOUND:
      case Protocol.ERROR_INVALID_RANGE:
      case Protocol.ERROR_SELECTION_UNAVAILABLE:
      case Protocol.ERROR_VISIBLE_POSITION_UNAVAILABLE:
      case Protocol.ERROR_MOVEMENT_ANCHOR_UNAVAILABLE:
      case Protocol.ERROR_UNSUPPORTED_FIXED_LAYOUT:
      case Protocol.ERROR_UNSUPPORTED_SCROLL_MODE:
      case Protocol.ERROR_UNSUPPORTED_WRITING_MODE:
      case Protocol.ERROR_RESULT_TOO_LARGE:
        return code;
      default:
        return Protocol.ERROR_CFI_RUNTIME_FAILURE;
    }
  }
  function parseCfi(source) {
    if (typeof source !== "string") throw new Error(Protocol.ERROR_INVALID_CFI);
    let root;
    try {
      root = colibrio.EpubCfiParser.parse(source);
      colibrio.EpubCfiValidator.runAllValidations(root);
    } catch (_error) {
      throw new Error(Protocol.ERROR_INVALID_CFI);
    }
    if (root.errors.length > 0 || !root.parentPath) throw new Error(Protocol.ERROR_INVALID_CFI);
    return root;
  }
  function targetKind(root) {
    return root.rangeStartPath && root.rangeEndPath ? Protocol.KIND_RANGE : Protocol.KIND_POINT;
  }
  function validateSupportedFullCfi(source) {
    var _a, _b;
    const root = parseCfi(source);
    const indexes = root.parentPath.localPaths.reduce((result, path, index) => {
      if (path.indirection) result.push(index);
      return result;
    }, []);
    if (indexes.length !== 1 || indexes[0] === 0) throw new Error(Protocol.ERROR_UNSUPPORTED_CFI_FEATURE);
    const kind = targetKind(root);
    if (kind === Protocol.KIND_POINT && !isCharacterOffset(root.parentPath.offset)) {
      throw new Error(Protocol.ERROR_UNSUPPORTED_CFI_FEATURE);
    }
    if (kind === Protocol.KIND_RANGE && (!isCharacterOffset((_a = root.rangeStartPath) == null ? void 0 : _a.offset) || !isCharacterOffset((_b = root.rangeEndPath) == null ? void 0 : _b.offset))) {
      throw new Error(Protocol.ERROR_UNSUPPORTED_CFI_FEATURE);
    }
    if (hasSideBias(root.parentPath) || hasSideBias(root.rangeStartPath) || hasSideBias(root.rangeEndPath)) {
      throw new Error(Protocol.ERROR_UNSUPPORTED_CFI_FEATURE);
    }
    return root;
  }
  function isCharacterOffset(offset) {
    return (offset == null ? void 0 : offset.type) === "CHARACTER";
  }
  function hasSideBias(path) {
    var _a;
    if (!path) return false;
    if (assertionHasSideBias((_a = path.offset) == null ? void 0 : _a.assertion)) return true;
    return path.localPaths.some(
      (localPath) => localPath.steps.some((step) => assertionHasSideBias(step.assertion))
    );
  }
  function assertionHasSideBias(assertion) {
    return (assertion == null ? void 0 : assertion.parameters.some((parameter) => parameter.name === "s")) ?? false;
  }

  // src/package-cfi.ts
  function resolvePackageCandidates(serializedCandidates, packageDocumentXml, packagePath) {
    const candidates = JSON.parse(serializedCandidates);
    if (!Array.isArray(candidates) || candidates.length > 1e3) {
      throw new Error("INVALID_CFI");
    }
    return candidates.map(function(candidate) {
      try {
        if (!isPackageCandidate(candidate)) {
          return null;
        }
        const target = resolvePackageTarget(candidate.cfi, packageDocumentXml, packagePath);
        return {
          id: candidate.id,
          itemrefId: target.itemref.getAttribute("id"),
          idref: target.idref,
          spineIndex: target.spineIndex,
          kind: targetKind(parseCfi(candidate.cfi))
        };
      } catch (_error) {
        return null;
      }
    }).filter(function(result) {
      return result !== null;
    });
  }
  function isPackageCandidate(value) {
    return typeof value === "object" && value !== null && typeof value.id === "string" && typeof value.cfi === "string";
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
    const resolver = new colibrio.EpubCfiResolver(validateSupportedFullCfi(fullCfi), {
      processTextAssertions: true,
      textAssertionSearchDistance: 1e4
    });
    const indirection = resolver.continueResolving(
      packageDocument,
      packageUrl(packagePath)
    );
    if (!indirection || indirection.element.localName !== "itemref") {
      throw new Error("PACKAGE_TARGET_NOT_FOUND");
    }
    const resolvedTarget = resolver.getResolvedTarget();
    if (resolvedTarget.getParserErrors().length > 0 || resolvedTarget.getResolverErrors().length > 0 || resolvedTarget.getTargetElement() !== indirection.element) {
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
      packageDocument,
      resolver,
      itemref: indirection.element,
      idref,
      spineIndex
    };
  }
  function verifiedContentResourceUrl(packageTarget, packagePath, expectedSpineIndex, expectedIdref, expectedItemrefId, expectedResourceHref, _geometryOnly) {
    const itemrefId = packageTarget.itemref.getAttribute("id") || null;
    if (packageTarget.spineIndex !== expectedSpineIndex || packageTarget.idref !== expectedIdref || itemrefId !== expectedItemrefId) {
      throw new Error("PACKAGE_TARGET_MISMATCH");
    }
    if (typeof expectedResourceHref !== "string" || expectedResourceHref.length === 0) {
      throw new Error("PACKAGE_TARGET_MISMATCH");
    }
    const manifestItems = Array.from(
      packageTarget.packageDocument.getElementsByTagNameNS("*", "item")
    ).filter(function(item) {
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
    return Array.from(spines[0].children).filter(function(element) {
      return element.localName === "itemref";
    });
  }
  function verifiedPackageItemref(packageDocument, spineIndex, expectedIdref, expectedItemrefId) {
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
  function generatePackageCfi(packageDocumentXml, packagePath, spineIndex, expectedIdref, expectedItemrefId) {
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
      textAssertionSearchDistance: 1e4
    });
    resolver.continueResolving(packageDocument, packageUrl(packagePath));
    const resolvedTarget = resolver.getResolvedTarget();
    if (resolvedTarget.getParserErrors().length > 0 || resolvedTarget.getResolverErrors().length > 0 || resolvedTarget.getTargetElement() !== itemref) {
      throw new Error("PACKAGE_TARGET_MISMATCH");
    }
    return packageCfi;
  }
  function serializeComponent(source) {
    const root = parseCfi(source);
    return colibrio.EpubCfiStringifier.stringifyRootNode(root).slice(8, -1);
  }

  // src/publication-dom.ts
  var READIUM_DECORATION_ROOT_ID = /^r2-decoration-[0-9]+$/;
  var READIUM_REFLOWABLE_SCRIPT_PATH = "/readium/scripts/readium-reflowable.js";
  var READIUM_STYLESHEET_PATH = "/readium/readium-css/";
  function isOwnedRuntimeNode(node, liveDocument) {
    return isReadiumDecorationRoot(node, liveDocument) || isReadiumVirtualPage(node, liveDocument) || isReadiumHeadResource(node, liveDocument);
  }
  function isReadiumDecorationRoot(node, liveDocument) {
    if (!(node instanceof Element)) return false;
    const element = node;
    return element.parentNode === liveDocument.body && element.localName === "div" && READIUM_DECORATION_ROOT_ID.test(element.id) && element.hasAttribute("data-group") && element.style.pointerEvents === "none";
  }
  function isReadiumVirtualPage(node, liveDocument) {
    if (!(node instanceof Element)) return false;
    const element = node;
    return element.parentNode === liveDocument.body && element.localName === "div" && element.id === "readium-virtual-page" && element.style.breakBefore === "column" && element.textContent === "​";
  }
  function isReadiumHeadResource(node, liveDocument) {
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
    const liveToSnapshot = /* @__PURE__ */ new WeakMap();
    const snapshotToLive = /* @__PURE__ */ new WeakMap();
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
  function clonePublicationNode(liveNode, snapshotParent, snapshotDocument, liveDocument, liveToSnapshot, snapshotToLive) {
    if (isOwnedRuntimeNode(liveNode, liveDocument)) {
      return;
    }
    const snapshotNode = snapshotDocument.importNode(liveNode, false);
    snapshotParent.appendChild(snapshotNode);
    liveToSnapshot.set(liveNode, snapshotNode);
    snapshotToLive.set(snapshotNode, liveNode);
    Array.from(liveNode.childNodes).forEach(function(liveChild) {
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
    const targetOffset = sourceKind === "live" ? liveBoundaryOffset(sourceChildren, sourceOffset, nodeMap, targetContainer) : snapshotBoundaryOffset(sourceChildren, sourceOffset, nodeMap, targetContainer);
    return { container: targetContainer, offset: targetOffset };
  }
  function isCharacterData(node) {
    return node.nodeType === Node.TEXT_NODE || node.nodeType === Node.CDATA_SECTION_NODE || node.nodeType === Node.COMMENT_NODE;
  }
  function liveBoundaryOffset(liveChildren, liveOffset, liveToSnapshot, snapshotParent) {
    return liveChildren.slice(0, liveOffset).reduce(function(offset, liveChild) {
      const snapshotChild = liveToSnapshot.get(liveChild);
      return snapshotChild && snapshotChild.parentNode === snapshotParent ? offset + 1 : offset;
    }, 0);
  }
  function snapshotBoundaryOffset(snapshotChildren, snapshotOffset, snapshotToLive, liveParent) {
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
    const liveIndex = liveChild ? Array.prototype.indexOf.call(liveParent.childNodes, liveChild) : -1;
    if (liveIndex < 0) {
      throw new Error("DOM_TARGET_NOT_FOUND");
    }
    return liveIndex;
  }
  function validateTextBoundary(container, offset) {
    if (!isCharacterData(container) || container.nodeType === Node.COMMENT_NODE) {
      return;
    }
    const text = container.data;
    if (offset > 0 && offset < text.length && isHighSurrogate(text.charCodeAt(offset - 1)) && isLowSurrogate(text.charCodeAt(offset))) {
      throw new Error("INVALID_RANGE");
    }
  }
  function isHighSurrogate(codeUnit) {
    return codeUnit >= 55296 && codeUnit <= 56319;
  }
  function isLowSurrogate(codeUnit) {
    return codeUnit >= 56320 && codeUnit <= 57343;
  }

  // src/quote-context.ts
  function textContext(range, publicationDocument, contextLength) {
    contextLength = contextLength || CONTEXT_LENGTH;
    validateTextBoundary(range.startContainer, range.startOffset);
    validateTextBoundary(range.endContainer, range.endOffset);
    const body = publicationBody(publicationDocument);
    if (!body || !body.contains(range.startContainer) || !body.contains(range.endContainer)) {
      throw new Error("DOM_TARGET_NOT_FOUND");
    }
    const selectedText = range.collapsed ? null : range.toString();
    if (selectedText && selectedText.length > MAX_SELECTED_TEXT_LENGTH) {
      throw new Error("RESULT_TOO_LARGE");
    }
    return {
      selectedText,
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
  function textBeforePosition(container, offset, body, publicationDocument, limit) {
    const walker = publicationDocument.createTreeWalker(body, NodeFilter.SHOW_TEXT);
    let result = "";
    let anchor = container;
    if (isCharacterData(container)) {
      result = takeLastCodeUnitSafe(container.data.slice(0, offset), limit) || "";
    } else if (offset > 0) {
      anchor = container.childNodes[offset - 1];
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
      result = (takeLastCodeUnitSafe(node.data, remaining) || "") + result;
      node = walker.previousNode();
    }
    return result || null;
  }
  function textAfterPosition(container, offset, body, publicationDocument, limit) {
    const walker = publicationDocument.createTreeWalker(body, NodeFilter.SHOW_TEXT);
    let result = "";
    let anchor = container;
    if (isCharacterData(container)) {
      result = takeFirstCodeUnitSafe(container.data.slice(offset), limit) || "";
    } else if (offset < container.childNodes.length) {
      anchor = container.childNodes[offset];
      const text = firstTextDescendant(anchor, publicationDocument);
      if (text) {
        anchor = text;
        result = takeFirstCodeUnitSafe(text.data, limit) || "";
      }
    } else if (offset > 0) {
      anchor = container.childNodes[offset - 1];
      const text = lastTextDescendant(anchor, publicationDocument);
      if (text) {
        anchor = text;
      }
    }
    walker.currentNode = anchor;
    let node = walker.nextNode();
    while (node && result.length < limit) {
      const remaining = limit - result.length;
      result += takeFirstCodeUnitSafe(node.data, remaining) || "";
      node = walker.nextNode();
    }
    return result || null;
  }
  function firstTextDescendant(root, publicationDocument) {
    if (isCharacterData(root)) {
      return root;
    }
    return publicationDocument.createTreeWalker(root, NodeFilter.SHOW_TEXT).nextNode();
  }
  function lastTextDescendant(root, publicationDocument) {
    if (isCharacterData(root)) {
      return root;
    }
    const walker = publicationDocument.createTreeWalker(root, NodeFilter.SHOW_TEXT);
    let last = null;
    let node = walker.nextNode();
    while (node) {
      last = node;
      node = walker.nextNode();
    }
    return last;
  }
  function publicationBody(publicationDocument) {
    return publicationDocument.getElementsByTagNameNS("*", "body")[0] || null;
  }
  function takeLastCodeUnitSafe(value, limit) {
    let start = Math.max(0, value.length - limit);
    if (start > 0 && isLowSurrogate(value.charCodeAt(start)) && isHighSurrogate(value.charCodeAt(start - 1))) {
      start += 1;
    }
    return value.slice(start) || null;
  }
  function takeFirstCodeUnitSafe(value, limit) {
    let end = Math.min(value.length, limit);
    if (end < value.length && end > 0 && isHighSurrogate(value.charCodeAt(end - 1)) && isLowSurrogate(value.charCodeAt(end))) {
      end -= 1;
    }
    return value.slice(0, end) || null;
  }
  function containsDurableText(value) {
    return /[^\s\u200b\u200c\u200d\ufeff]/u.test(value);
  }

  // src/visible-position.ts
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
    if (!snapshotRange.collapsed || !isTextPosition(snapshotRange.startContainer, snapshotRange.startOffset)) {
      throw new Error("VISIBLE_POSITION_UNAVAILABLE");
    }
    validateTextBoundary(snapshotRange.startContainer, snapshotRange.startOffset);
    const builder = new colibrio.EpubCfiBuilder();
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
    if (style.getPropertyValue("--USER__view").trim() === "readium-scroll-on" || style.getPropertyValue("--USER__scroll").trim() === "readium-scroll-on") {
      throw new Error("UNSUPPORTED_SCROLL_MODE");
    }
    const rootStyle = window.getComputedStyle(root);
    const writingMode = rootStyle.getPropertyValue("writing-mode").trim();
    if (writingMode !== "horizontal-tb") {
      throw new Error("UNSUPPORTED_WRITING_MODE");
    }
    const bodyDirection = window.getComputedStyle(document.body).getPropertyValue("direction").trim().toLowerCase();
    if (bodyDirection !== "ltr" && bodyDirection !== "rtl") {
      throw new Error("UNSUPPORTED_WRITING_MODE");
    }
    return { direction: bodyDirection };
  }
  function requireReflowableCfiDocument() {
    if (!window.readium || window.readium.isFixedLayout === true || window.readium.isReflowable !== true) {
      throw new Error("UNSUPPORTED_FIXED_LAYOUT");
    }
  }
  function firstVisibleTextBoundary(liveDocument, direction) {
    const body = liveDocument.body;
    if (!body || publicationViewportWidth(liveDocument) <= 0 || publicationViewportHeight(liveDocument) <= 0) {
      return null;
    }
    const walker = liveDocument.createTreeWalker(
      body,
      NodeFilter.SHOW_TEXT,
      null
    );
    let textNode = walker.nextNode();
    while (textNode) {
      if (!isInsideOwnedRuntimeNode(textNode, liveDocument) && nodeIntersectsViewport(textNode, liveDocument, direction)) {
        const offset = firstVisibleCharacterOffset(
          textNode,
          liveDocument,
          direction
        );
        if (offset !== null) {
          return { container: textNode, offset };
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
    if (!textNode.data || !containsDurableText(textNode.data) || !isRenderedTextNode(textNode)) {
      return false;
    }
    const range = liveDocument.createRange();
    range.selectNodeContents(textNode);
    return Array.from(range.getClientRects()).some(function(rect) {
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
      const intersects = Array.from(prefix.getClientRects()).some(function(rect) {
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
      const characterLength = codePoint > 65535 ? 2 : 1;
      const character = textNode.data.slice(offset, offset + characterLength);
      if (containsDurableText(character)) {
        const range = liveDocument.createRange();
        range.setStart(textNode, offset);
        range.setEnd(textNode, offset + characterLength);
        const isVisible = Array.from(range.getClientRects()).some(function(rect) {
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
    return offset > 0 && isLowSurrogate(value.charCodeAt(offset)) && isHighSurrogate(value.charCodeAt(offset - 1)) ? offset - 1 : offset;
  }
  function isRenderedTextNode(textNode) {
    let element = textNode.parentElement;
    while (element) {
      const style = window.getComputedStyle(element);
      if (element.hidden || style.display === "none" || style.visibility === "hidden" || style.visibility === "collapse" || style.opacity === "0" || style.contentVisibility === "hidden") {
        return false;
      }
      if (element === document.body) {
        break;
      }
      element = element.parentElement;
    }
    return true;
  }
  function hasPositiveViewportIntersection(rect, direction) {
    const viewportWidth = publicationViewportWidth(document);
    const viewportHeight = publicationViewportHeight(document);
    const blockStart = Math.max(0, rect.top);
    const blockEnd = Math.min(viewportHeight, rect.bottom);
    const inlineStart = direction === "rtl" ? Math.min(viewportWidth, rect.right) : Math.max(0, rect.left);
    const inlineEnd = direction === "rtl" ? Math.max(0, rect.left) : Math.min(viewportWidth, rect.right);
    const inlineExtent = direction === "rtl" ? inlineStart - inlineEnd : inlineEnd - inlineStart;
    return blockEnd - blockStart > MIN_VISIBLE_EXTENT_PIXELS && inlineExtent > MIN_VISIBLE_EXTENT_PIXELS && rect.width > MIN_VISIBLE_EXTENT_PIXELS && rect.height > MIN_VISIBLE_EXTENT_PIXELS;
  }
  function publicationViewportWidth(publicationDocument) {
    return publicationDocument.documentElement.clientWidth || window.innerWidth;
  }
  function publicationViewportHeight(publicationDocument) {
    return publicationDocument.documentElement.clientHeight || window.innerHeight;
  }

  // src/content-target.ts
  var lastResolvedContentTarget = null;
  function resolveContentTargetDetails(fullCfi, packageDocumentXml, packagePath, expectedSpineIndex, expectedIdref, expectedItemrefId, expectedResourceHref, geometryOnly) {
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
    } catch (_error) {
      throw new Error("DOM_TARGET_NOT_FOUND");
    }
    if (remainingIndirection) {
      throw new Error("UNSUPPORTED_CFI_FEATURE");
    }
    const resolvedTarget = packageTarget.resolver.getResolvedTarget();
    if (resolvedTarget.getParserErrors().length > 0 || resolvedTarget.getResolverErrors().length > 0 || resolvedTarget.indirectionErrors.length > 0 || resolvedTarget.hasErrors() || !resolvedTarget.isEveryIndirectionResolved() || !resolvedTarget.isEveryStepAndOffsetParsed() || !resolvedTarget.isEveryStepResolved() || !resolvedTarget.isOwnedBySingleDocument()) {
      throw new Error("DOM_TARGET_NOT_FOUND");
    }
    let snapshotRange;
    try {
      snapshotRange = resolvedTarget.createDomRange();
    } catch (_error) {
      throw new Error(expectedKind === "range" ? "INVALID_RANGE" : "DOM_TARGET_NOT_FOUND");
    }
    const snapshotBody = publicationBody(snapshot.document);
    if (!snapshotRange || !snapshotBody || !snapshotBody.contains(snapshotRange.startContainer) || !snapshotBody.contains(snapshotRange.endContainer)) {
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
        liveRange,
        resolution: { kind: expectedKind }
      };
    }
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
      liveRange,
      resolution: {
        kind: expectedKind,
        selectedText: context.selectedText,
        prefix: context.prefix,
        suffix: context.suffix,
        movementAnchor
      }
    };
  }
  function resolveContentTarget(fullCfi, packageDocumentXml, packagePath, expectedSpineIndex, expectedIdref, expectedItemrefId, expectedResourceHref) {
    const details = resolveContentTargetDetails(
      fullCfi,
      packageDocumentXml,
      packagePath,
      expectedSpineIndex,
      expectedIdref,
      expectedItemrefId,
      expectedResourceHref
    );
    rememberResolvedContentTarget(
      fullCfi,
      packagePath,
      expectedSpineIndex,
      expectedIdref,
      expectedItemrefId,
      expectedResourceHref,
      details
    );
    return details.resolution;
  }
  function rememberResolvedContentTarget(fullCfi, packagePath, spineIndex, idref, itemrefId, resourceHref, details) {
    lastResolvedContentTarget = {
      fullCfi,
      packagePath,
      spineIndex,
      idref,
      itemrefId,
      resourceHref,
      details
    };
  }
  function resolvedContentTargetForVerification(fullCfi, expectedResourceHref) {
    const cached = lastResolvedContentTarget;
    if (cached && cached.fullCfi === fullCfi && cached.resourceHref === expectedResourceHref && rangeBelongsToDocument(cached.details.liveRange, document)) {
      return cached.details;
    }
    throw new Error("DOM_TARGET_NOT_FOUND");
  }
  function rangeBelongsToDocument(range, publicationDocument) {
    const body = publicationBody(publicationDocument);
    return Boolean(body && body.contains(range.startContainer) && body.contains(range.endContainer));
  }
  function verifyContentTarget(fullCfi, expectedResourceHref, expectedKind, expectedSelectedText, expectedPrefix, expectedSuffix, expectedExact, expectedBefore, expectedAfter) {
    const details = resolvedContentTargetForVerification(
      fullCfi,
      expectedResourceHref
    );
    const resolution = details.resolution;
    const anchor = resolution.movementAnchor;
    const semanticMatch = anchor !== void 0 && resolution.kind === expectedKind && resolution.selectedText === expectedSelectedText && resolution.prefix === expectedPrefix && resolution.suffix === expectedSuffix && anchor.exact === expectedExact && anchor.before === expectedBefore && anchor.after === expectedAfter;
    return {
      semanticMatch,
      visible: semanticMatch && isTargetRangeVisible(details.liveRange, document)
    };
  }
  function isTargetRangeVisible(range, publicationDocument) {
    const probe = visibilityProbeRange(range, publicationDocument);
    const viewportWidth = publicationViewportWidth(publicationDocument);
    const viewportHeight = publicationViewportHeight(publicationDocument);
    const rectangles = Array.from(probe.getClientRects());
    if (rectangles.length === 0) {
      rectangles.push(probe.getBoundingClientRect());
    }
    return rectangles.some(function(rectangle) {
      const visibleWidth = Math.min(rectangle.right, viewportWidth) - Math.max(rectangle.left, 0);
      const visibleHeight = Math.min(rectangle.bottom, viewportHeight) - Math.max(rectangle.top, 0);
      return visibleWidth > 0.5 && visibleHeight > 0.5 && rectangle.width > 0 && rectangle.height > 0;
    });
  }
  function hasDurableCharacter(node) {
    return isCharacterData(node) && /\S/u.test(node.data);
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
    if (!isTextPosition(range.startContainer, range.startOffset) || !isTextPosition(range.endContainer, range.endOffset)) {
      throw new Error("DOM_TARGET_NOT_FOUND");
    }
    validateTextBoundary(range.startContainer, range.startOffset);
    validateTextBoundary(range.endContainer, range.endOffset);
    if (expectedKind === "range") {
      if (!resolvedTarget.hasRangePaths() || !resolvedTarget.isDomRange() || range.collapsed || range.toString().length === 0) {
        throw new Error("INVALID_RANGE");
      }
    } else if (resolvedTarget.hasRangePaths() || !range.collapsed) {
      throw new Error("INVALID_RANGE");
    }
  }
  function createMovementAnchor(range, publicationDocument, context) {
    const exactSource = range.collapsed ? textFollowingRange(
      range,
      publicationDocument,
      MOVEMENT_QUOTE_LENGTH + CONTEXT_LENGTH
    ) : context.selectedText ?? "";
    const exact = takeFirstCodeUnitSafe(exactSource, MOVEMENT_QUOTE_LENGTH);
    if (!exact || !containsDurableText(exact)) {
      throw new Error(
        range.collapsed ? "UNSUPPORTED_CFI_FEATURE" : "MOVEMENT_ANCHOR_UNAVAILABLE"
      );
    }
    const trailingText = range.collapsed ? exactSource.slice(exact.length) : (context.selectedText ?? "").slice(exact.length) + textFollowingRange(range, publicationDocument, CONTEXT_LENGTH);
    return {
      exact,
      before: context.prefix === null ? null : takeLastCodeUnitSafe(context.prefix, CONTEXT_LENGTH),
      after: takeFirstCodeUnitSafe(trailingText, CONTEXT_LENGTH)
    };
  }
  function textFollowingRange(range, publicationDocument, limit) {
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
  function isTextPosition(container, offset) {
    if (container.nodeType !== Node.TEXT_NODE && container.nodeType !== Node.CDATA_SECTION_NODE) {
      return false;
    }
    return Number.isInteger(offset) && offset >= 0 && offset <= container.length;
  }

  // src/bookmark-visibility.ts
  function visiblePointTargets(serializedCandidates, packageDocumentXml, packagePath, expectedSpineIndex, expectedIdref, expectedItemrefId, expectedResourceHref) {
    const candidates = JSON.parse(serializedCandidates);
    if (!Array.isArray(candidates) || candidates.length > 1e3) {
      throw new Error("INVALID_CFI");
    }
    return candidates.map(function(candidate) {
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
          visible: details.resolution.kind === "point" && isTargetRangeVisible(details.liveRange, document)
        };
      } catch (_error) {
        return { id: isPointCandidate(candidate) ? candidate.id : null, visible: false };
      }
    }).filter(function(result) {
      return result !== null && typeof result.id === "string";
    });
  }
  function isPointCandidate(value) {
    return typeof value === "object" && value !== null && typeof value.id === "string" && typeof value.cfi === "string";
  }
  function visibilityProbeRange(range, publicationDocument) {
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
  function nextCodeUnitBoundary(text, offset) {
    const first = text.charCodeAt(offset);
    const second = text.charCodeAt(offset + 1);
    return offset + (isHighSurrogate(first) && isLowSurrogate(second) ? 2 : 1);
  }
  function previousCodeUnitBoundary(text, offset) {
    const previous = text.charCodeAt(offset - 1);
    const beforePrevious = text.charCodeAt(offset - 2);
    return offset - (isLowSurrogate(previous) && isHighSurrogate(beforePrevious) ? 2 : 1);
  }

  // src/selection.ts
  function generateSelectionContentCfi() {
    requireReflowableCfiDocument();
    const selection = window.getSelection();
    if (!selection || selection.rangeCount === 0 || selection.isCollapsed) {
      return null;
    }
    if (selection.rangeCount !== 1) {
      throw new Error("INVALID_RANGE");
    }
    const liveRange = rangeWithTextBoundaries(selection.getRangeAt(0), document);
    if (liveRange.collapsed) {
      return null;
    }
    const snapshot = createPublicationSnapshot(document);
    const snapshotRange = snapshot.toSnapshotRange(liveRange);
    if (snapshotRange.collapsed) {
      throw new Error("SELECTION_UNAVAILABLE");
    }
    const context = textContext(
      snapshotRange,
      snapshot.document,
      SELECTION_CONTEXT_LENGTH
    );
    if (context.selectedText === null) {
      throw new Error("SELECTION_UNAVAILABLE");
    }
    const builder = new colibrio.EpubCfiBuilder();
    builder.appendTerminalDomRange(rangeForCfiBuilder(snapshotRange));
    const contentCfi = builder.toString();
    parseCfi(contentCfi);
    return {
      contentCfi,
      selectedText: context.selectedText,
      prefix: context.prefix,
      suffix: context.suffix
    };
  }
  function rangeWithTextBoundaries(range, publicationDocument) {
    const body = publicationBody(publicationDocument);
    if (!body) {
      throw new Error("SELECTION_UNAVAILABLE");
    }
    const start = textBoundaryAtOrAfter(
      range.startContainer,
      range.startOffset,
      body
    );
    const end = textBoundaryAtOrBefore(
      range.endContainer,
      range.endOffset,
      body
    );
    if (!start || !end) {
      throw new Error("SELECTION_UNAVAILABLE");
    }
    const normalized = publicationDocument.createRange();
    try {
      normalized.setStart(start.container, start.offset);
      normalized.setEnd(end.container, end.offset);
    } catch (error) {
      throw new Error("SELECTION_UNAVAILABLE");
    }
    if (normalized.collapsed || normalized.toString() !== range.toString()) {
      throw new Error("SELECTION_UNAVAILABLE");
    }
    return normalized;
  }
  function textBoundaryAtOrAfter(container, offset, root) {
    if (isCharacterData(container)) {
      return { container, offset };
    }
    let candidate = container.childNodes[offset] || nodeAfter(container, root);
    while (candidate) {
      if (isCharacterData(candidate)) {
        return { container: candidate, offset: 0 };
      }
      candidate = candidate.firstChild || nodeAfter(candidate, root);
    }
    return null;
  }
  function textBoundaryAtOrBefore(container, offset, root) {
    if (isCharacterData(container)) {
      return { container, offset };
    }
    let candidate = offset > 0 ? container.childNodes[offset - 1] : nodeBefore(container, root);
    while (candidate) {
      if (isCharacterData(candidate)) {
        return { container: candidate, offset: candidate.length };
      }
      candidate = candidate.lastChild || nodeBefore(candidate, root);
    }
    return null;
  }
  function nodeAfter(node, root) {
    let candidate = node;
    while (candidate && candidate !== root) {
      if (candidate.nextSibling) {
        return candidate.nextSibling;
      }
      candidate = candidate.parentNode;
    }
    return null;
  }
  function nodeBefore(node, root) {
    let candidate = node;
    while (candidate && candidate !== root) {
      if (candidate.previousSibling) {
        return candidate.previousSibling;
      }
      candidate = candidate.parentNode;
    }
    return null;
  }
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

  // src/runtime.ts
  var existing = window[Protocol.GLOBAL];
  if (!existing || existing[Protocol.METHOD_RUNTIME_VERSION]() !== Protocol.RUNTIME_VERSION) {
    const runtime = {
      [Protocol.METHOD_IS_DOCUMENT_READY]: () => safely(() => {
        var _a, _b;
        if (((_a = window.readium) == null ? void 0 : _a.isFixedLayout) === true) {
          throw new Error(Protocol.ERROR_UNSUPPORTED_FIXED_LAYOUT);
        }
        return document.readyState !== "loading" && document.documentElement !== null && publicationBody(document) !== null && Boolean(window.readium) && ((_b = window.readium) == null ? void 0 : _b.isReflowable) === true;
      }),
      [Protocol.METHOD_RUNTIME_VERSION]: () => Protocol.RUNTIME_VERSION,
      [Protocol.METHOD_PARSE]: (cfi) => safely(() => {
        const root = parseCfi(cfi);
        return {
          kind: targetKind(root),
          hasIndirection: root.parentPath.localPaths.some((path) => path.indirection)
        };
      }),
      [Protocol.METHOD_RESOLVE_PACKAGE]: (cfi, packageXml, packagePath) => safely(() => {
        const target = resolvePackageTarget(cfi, packageXml, packagePath);
        return {
          itemrefId: target.itemref.getAttribute("id"),
          idref: target.idref,
          spineIndex: target.spineIndex,
          kind: targetKind(parseCfi(cfi))
        };
      }),
      [Protocol.METHOD_RESOLVE_PACKAGE_CANDIDATES]: (serializedCandidates, packageXml, packagePath) => safely(() => resolvePackageCandidates(serializedCandidates, packageXml, packagePath)),
      [Protocol.METHOD_GENERATE_PACKAGE]: (packageXml, packagePath, spineIndex, idref, itemrefId) => safely(() => generatePackageCfi(packageXml, packagePath, spineIndex, idref, itemrefId)),
      [Protocol.METHOD_RESOLVE_CONTENT]: (cfi, packageXml, packagePath, spineIndex, idref, itemrefId, resourceHref) => safely(() => resolveContentTarget(
        cfi,
        packageXml,
        packagePath,
        spineIndex,
        idref,
        itemrefId,
        resourceHref
      )),
      [Protocol.METHOD_VERIFY_CONTENT_TARGET]: (cfi, resourceHref, kind, selectedText, prefix, suffix, exact, before, after) => safely(() => verifyContentTarget(
        cfi,
        resourceHref,
        kind,
        selectedText,
        prefix,
        suffix,
        exact,
        before,
        after
      )),
      [Protocol.METHOD_VISIBLE_POINT_TARGETS]: (serializedCandidates, packageXml, packagePath, spineIndex, idref, itemrefId, resourceHref) => safely(() => visiblePointTargets(
        serializedCandidates,
        packageXml,
        packagePath,
        spineIndex,
        idref,
        itemrefId,
        resourceHref
      )),
      [Protocol.METHOD_GENERATE_SELECTION_CONTENT_CFI]: () => safely(generateSelectionContentCfi),
      [Protocol.METHOD_GENERATE_VISIBLE_POSITION_CONTENT_CFI]: () => safely(generateVisiblePositionContentCfi),
      [Protocol.METHOD_COMPOSE_FULL_CFI]: (packageCfi, contentCfi) => safely(() => {
        const full = `epubcfi(${serializeComponent(packageCfi)}!${serializeComponent(contentCfi)})`;
        parseCfi(full);
        return full;
      })
    };
    window[Protocol.GLOBAL] = Object.freeze(runtime);
  }
})();
