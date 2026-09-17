// GENERATED from tools/reader-cfi-runtime/protocol.json; do not edit.
package com.secondpasslibrary.reader.reader.readium.cfi

internal object CfiProtocol {
    const val ARG_AFTER = "after"
    const val ARG_BEFORE = "before"
    const val ARG_CFI = "cfi"
    const val ARG_CONTENT_CFI = "contentCfi"
    const val ARG_EXACT = "exact"
    const val ARG_IDREF = "idref"
    const val ARG_ITEMREF_ID = "itemrefId"
    const val ARG_KIND = "kind"
    const val ARG_PACKAGE_CFI = "packageCfi"
    const val ARG_PACKAGE_PATH = "packagePath"
    const val ARG_PACKAGE_XML = "packageXml"
    const val ARG_PREFIX = "prefix"
    const val ARG_RESOURCE_HREF = "resourceHref"
    const val ARG_SELECTED_TEXT = "selectedText"
    const val ARG_SERIALIZED_CANDIDATES = "serializedCandidates"
    const val ARG_SPINE_INDEX = "spineIndex"
    const val ARG_SUFFIX = "suffix"
    const val CONTEXT_LENGTH = 64
    const val ERROR_CFI_RUNTIME_FAILURE = "CFI_RUNTIME_FAILURE"
    const val ERROR_DOM_TARGET_NOT_FOUND = "DOM_TARGET_NOT_FOUND"
    const val ERROR_INVALID_CFI = "INVALID_CFI"
    const val ERROR_INVALID_PACKAGE_DOCUMENT = "INVALID_PACKAGE_DOCUMENT"
    const val ERROR_INVALID_RANGE = "INVALID_RANGE"
    const val ERROR_MOVEMENT_ANCHOR_UNAVAILABLE = "MOVEMENT_ANCHOR_UNAVAILABLE"
    const val ERROR_PACKAGE_TARGET_MISMATCH = "PACKAGE_TARGET_MISMATCH"
    const val ERROR_PACKAGE_TARGET_NOT_FOUND = "PACKAGE_TARGET_NOT_FOUND"
    const val ERROR_RESULT_TOO_LARGE = "RESULT_TOO_LARGE"
    const val ERROR_SELECTION_UNAVAILABLE = "SELECTION_UNAVAILABLE"
    const val ERROR_UNSUPPORTED_CFI_FEATURE = "UNSUPPORTED_CFI_FEATURE"
    const val ERROR_UNSUPPORTED_FIXED_LAYOUT = "UNSUPPORTED_FIXED_LAYOUT"
    const val ERROR_UNSUPPORTED_SCROLL_MODE = "UNSUPPORTED_SCROLL_MODE"
    const val ERROR_UNSUPPORTED_WRITING_MODE = "UNSUPPORTED_WRITING_MODE"
    const val ERROR_VISIBLE_POSITION_UNAVAILABLE = "VISIBLE_POSITION_UNAVAILABLE"
    const val FIELD_AFTER = "after"
    const val FIELD_BEFORE = "before"
    const val FIELD_CFI = "cfi"
    const val FIELD_CODE = "code"
    const val FIELD_CONTENT_CFI = "contentCfi"
    const val FIELD_ERROR = "error"
    const val FIELD_EXACT = "exact"
    const val FIELD_HAS_INDIRECTION = "hasIndirection"
    const val FIELD_ID = "id"
    const val FIELD_IDREF = "idref"
    const val FIELD_ITEMREF_ID = "itemrefId"
    const val FIELD_KIND = "kind"
    const val FIELD_MOVEMENT_ANCHOR = "movementAnchor"
    const val FIELD_OK = "ok"
    const val FIELD_PREFIX = "prefix"
    const val FIELD_SELECTED_TEXT = "selectedText"
    const val FIELD_SEMANTIC_MATCH = "semanticMatch"
    const val FIELD_SPINE_INDEX = "spineIndex"
    const val FIELD_SUFFIX = "suffix"
    const val FIELD_VALUE = "value"
    const val FIELD_VISIBLE = "visible"
    const val GLOBAL = "__secondPassEpubCfi"
    const val KIND_POINT = "point"
    const val KIND_RANGE = "range"
    const val MAX_SELECTED_TEXT_LENGTH = 65536
    const val METHOD_COMPOSE_FULL_CFI = "composeFullCfi"
    const val METHOD_GENERATE_PACKAGE = "generatePackage"
    const val METHOD_GENERATE_SELECTION_CONTENT_CFI = "generateSelectionContentCfi"
    const val METHOD_GENERATE_VISIBLE_POSITION_CONTENT_CFI = "generateVisiblePositionContentCfi"
    const val METHOD_IS_DOCUMENT_READY = "isDocumentReady"
    const val METHOD_PARSE = "parse"
    const val METHOD_RESOLVE_CONTENT = "resolveContent"
    const val METHOD_RESOLVE_PACKAGE = "resolvePackage"
    const val METHOD_RESOLVE_PACKAGE_CANDIDATES = "resolvePackageCandidates"
    const val METHOD_RUNTIME_VERSION = "runtimeVersion"
    const val METHOD_VERIFY_CONTENT_TARGET = "verifyContentTarget"
    const val METHOD_VISIBLE_POINT_TARGETS = "visiblePointTargets"
    const val MOVEMENT_QUOTE_LENGTH = 128
    const val RUNTIME_VERSION = "1.12.9"
    const val SELECTION_CONTEXT_LENGTH = 2000
}

internal enum class CfiRuntimeMethod(val wireName: String, vararg val argumentNames: String) {
    COMPOSE_FULL_CFI(
        CfiProtocol.METHOD_COMPOSE_FULL_CFI,
        CfiProtocol.ARG_PACKAGE_CFI,
        CfiProtocol.ARG_CONTENT_CFI
    ),
    GENERATE_PACKAGE(
        CfiProtocol.METHOD_GENERATE_PACKAGE,
        CfiProtocol.ARG_PACKAGE_XML,
        CfiProtocol.ARG_PACKAGE_PATH,
        CfiProtocol.ARG_SPINE_INDEX,
        CfiProtocol.ARG_IDREF,
        CfiProtocol.ARG_ITEMREF_ID
    ),
    GENERATE_SELECTION_CONTENT_CFI(
        CfiProtocol.METHOD_GENERATE_SELECTION_CONTENT_CFI
    ),
    GENERATE_VISIBLE_POSITION_CONTENT_CFI(
        CfiProtocol.METHOD_GENERATE_VISIBLE_POSITION_CONTENT_CFI
    ),
    IS_DOCUMENT_READY(
        CfiProtocol.METHOD_IS_DOCUMENT_READY
    ),
    PARSE(
        CfiProtocol.METHOD_PARSE,
        CfiProtocol.ARG_CFI
    ),
    RESOLVE_CONTENT(
        CfiProtocol.METHOD_RESOLVE_CONTENT,
        CfiProtocol.ARG_CFI,
        CfiProtocol.ARG_PACKAGE_XML,
        CfiProtocol.ARG_PACKAGE_PATH,
        CfiProtocol.ARG_SPINE_INDEX,
        CfiProtocol.ARG_IDREF,
        CfiProtocol.ARG_ITEMREF_ID,
        CfiProtocol.ARG_RESOURCE_HREF
    ),
    RESOLVE_PACKAGE(
        CfiProtocol.METHOD_RESOLVE_PACKAGE,
        CfiProtocol.ARG_CFI,
        CfiProtocol.ARG_PACKAGE_XML,
        CfiProtocol.ARG_PACKAGE_PATH
    ),
    RESOLVE_PACKAGE_CANDIDATES(
        CfiProtocol.METHOD_RESOLVE_PACKAGE_CANDIDATES,
        CfiProtocol.ARG_SERIALIZED_CANDIDATES,
        CfiProtocol.ARG_PACKAGE_XML,
        CfiProtocol.ARG_PACKAGE_PATH
    ),
    RUNTIME_VERSION(
        CfiProtocol.METHOD_RUNTIME_VERSION
    ),
    VERIFY_CONTENT_TARGET(
        CfiProtocol.METHOD_VERIFY_CONTENT_TARGET,
        CfiProtocol.ARG_CFI,
        CfiProtocol.ARG_RESOURCE_HREF,
        CfiProtocol.ARG_KIND,
        CfiProtocol.ARG_SELECTED_TEXT,
        CfiProtocol.ARG_PREFIX,
        CfiProtocol.ARG_SUFFIX,
        CfiProtocol.ARG_EXACT,
        CfiProtocol.ARG_BEFORE,
        CfiProtocol.ARG_AFTER
    ),
    VISIBLE_POINT_TARGETS(
        CfiProtocol.METHOD_VISIBLE_POINT_TARGETS,
        CfiProtocol.ARG_SERIALIZED_CANDIDATES,
        CfiProtocol.ARG_PACKAGE_XML,
        CfiProtocol.ARG_PACKAGE_PATH,
        CfiProtocol.ARG_SPINE_INDEX,
        CfiProtocol.ARG_IDREF,
        CfiProtocol.ARG_ITEMREF_ID,
        CfiProtocol.ARG_RESOURCE_HREF
    )
}
