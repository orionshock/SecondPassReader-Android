// GENERATED from protocol.json; do not edit.
export const Protocol = {
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
  SELECTION_CONTEXT_LENGTH: 2000
} as const;
export type TargetKind = "point" | "range";
export type RuntimeErrorCode = "INVALID_CFI" | "UNSUPPORTED_CFI_FEATURE" | "INVALID_PACKAGE_DOCUMENT" | "PACKAGE_TARGET_NOT_FOUND" | "PACKAGE_TARGET_MISMATCH" | "DOM_TARGET_NOT_FOUND" | "INVALID_RANGE" | "SELECTION_UNAVAILABLE" | "VISIBLE_POSITION_UNAVAILABLE" | "MOVEMENT_ANCHOR_UNAVAILABLE" | "UNSUPPORTED_FIXED_LAYOUT" | "UNSUPPORTED_SCROLL_MODE" | "UNSUPPORTED_WRITING_MODE" | "RESULT_TOO_LARGE" | "CFI_RUNTIME_FAILURE";
export type RuntimeResult<T> =
  | { readonly ok: true; readonly value: T }
  | { readonly ok: false; readonly error: { readonly code: RuntimeErrorCode } };
export interface ContentResolution {
  readonly kind: TargetKind;
  readonly movementAnchor?: MovementAnchor;
  readonly prefix?: string | null;
  readonly selectedText?: string | null;
  readonly suffix?: string | null;
}
export interface MovementAnchor {
  readonly after: string | null;
  readonly before: string | null;
  readonly exact: string;
}
export interface PackageCandidate {
  readonly cfi: string;
  readonly id: string;
}
export interface PackageResolution {
  readonly id: string;
  readonly idref: string;
  readonly itemrefId: string | null;
  readonly kind: TargetKind;
  readonly spineIndex: number;
}
export interface PackageTarget {
  readonly idref: string;
  readonly itemrefId: string | null;
  readonly kind: TargetKind;
  readonly spineIndex: number;
}
export interface Parsed {
  readonly hasIndirection: boolean;
  readonly kind: TargetKind;
}
export interface Selection {
  readonly contentCfi: string;
  readonly prefix: string | null;
  readonly selectedText: string;
  readonly suffix: string | null;
}
export interface Verification {
  readonly semanticMatch: boolean;
  readonly visible: boolean;
}
export interface Visibility {
  readonly id: string;
  readonly visible: boolean;
}
export interface RuntimeFacade {
  [Protocol.METHOD_COMPOSE_FULL_CFI](packageCfi: string, contentCfi: string): RuntimeResult<string>;
  [Protocol.METHOD_GENERATE_PACKAGE](packageXml: string, packagePath: string, spineIndex: number, idref: string, itemrefId: string | null): RuntimeResult<string>;
  [Protocol.METHOD_GENERATE_SELECTION_CONTENT_CFI](): RuntimeResult<Selection | null>;
  [Protocol.METHOD_GENERATE_VISIBLE_POSITION_CONTENT_CFI](): RuntimeResult<string>;
  [Protocol.METHOD_IS_DOCUMENT_READY](): RuntimeResult<boolean>;
  [Protocol.METHOD_PARSE](cfi: string): RuntimeResult<Parsed>;
  [Protocol.METHOD_RESOLVE_CONTENT](cfi: string, packageXml: string, packagePath: string, spineIndex: number, idref: string, itemrefId: string | null, resourceHref: string): RuntimeResult<ContentResolution>;
  [Protocol.METHOD_RESOLVE_PACKAGE](cfi: string, packageXml: string, packagePath: string): RuntimeResult<PackageTarget>;
  [Protocol.METHOD_RESOLVE_PACKAGE_CANDIDATES](serializedCandidates: string, packageXml: string, packagePath: string): RuntimeResult<PackageResolution[]>;
  [Protocol.METHOD_RUNTIME_VERSION](): string;
  [Protocol.METHOD_VERIFY_CONTENT_TARGET](cfi: string, resourceHref: string, kind: TargetKind, selectedText: string | null, prefix: string | null, suffix: string | null, exact: string, before: string | null, after: string | null): RuntimeResult<Verification>;
  [Protocol.METHOD_VISIBLE_POINT_TARGETS](serializedCandidates: string, packageXml: string, packagePath: string, spineIndex: number, idref: string, itemrefId: string | null, resourceHref: string): RuntimeResult<Visibility[]>;
}
