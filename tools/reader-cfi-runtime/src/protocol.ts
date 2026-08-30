import { colibrio, type CfiAssertion, type CfiOffset, type CfiPath, type CfiRoot } from "./colibrio";

export const RUNTIME_VERSION = "1.12.8";
export const CONTEXT_LENGTH = 64;
export const SELECTION_CONTEXT_LENGTH = 2000;
export const MOVEMENT_QUOTE_LENGTH = 128;
export const MAX_SELECTED_TEXT_LENGTH = 64 * 1024;
export const MIN_VISIBLE_EXTENT_PIXELS = 0.5;

export type TargetKind = "point" | "range";
export type RuntimeErrorCode =
  | "INVALID_CFI" | "UNSUPPORTED_CFI_FEATURE" | "INVALID_PACKAGE_DOCUMENT"
  | "PACKAGE_TARGET_NOT_FOUND" | "PACKAGE_TARGET_MISMATCH" | "DOM_TARGET_NOT_FOUND"
  | "INVALID_RANGE" | "SELECTION_UNAVAILABLE" | "VISIBLE_POSITION_UNAVAILABLE"
  | "MOVEMENT_ANCHOR_UNAVAILABLE" | "UNSUPPORTED_FIXED_LAYOUT"
  | "UNSUPPORTED_SCROLL_MODE" | "UNSUPPORTED_WRITING_MODE" | "RESULT_TOO_LARGE"
  | "CFI_RUNTIME_FAILURE";

export type RuntimeResult<T> =
  | { readonly ok: true; readonly value: T }
  | { readonly ok: false; readonly error: { readonly code: RuntimeErrorCode } };

export function safely<T>(operation: () => T): RuntimeResult<T> {
  try {
    return { ok: true, value: operation() };
  } catch (error: unknown) {
    return { ok: false, error: { code: classifyError(error) } };
  }
}

function classifyError(error: unknown): RuntimeErrorCode {
  const code = error instanceof Error ? error.message : "";
  switch (code) {
    case "INVALID_CFI": case "UNSUPPORTED_CFI_FEATURE": case "INVALID_PACKAGE_DOCUMENT":
    case "PACKAGE_TARGET_NOT_FOUND": case "PACKAGE_TARGET_MISMATCH":
    case "DOM_TARGET_NOT_FOUND": case "INVALID_RANGE": case "SELECTION_UNAVAILABLE":
    case "VISIBLE_POSITION_UNAVAILABLE": case "MOVEMENT_ANCHOR_UNAVAILABLE":
    case "UNSUPPORTED_FIXED_LAYOUT": case "UNSUPPORTED_SCROLL_MODE":
    case "UNSUPPORTED_WRITING_MODE": case "RESULT_TOO_LARGE":
      return code;
    default:
      return "CFI_RUNTIME_FAILURE";
  }
}

export function parseCfi(source: unknown): CfiRoot {
  if (typeof source !== "string") throw new Error("INVALID_CFI");
  let root: CfiRoot;
  try {
    root = colibrio.EpubCfiParser.parse(source);
    colibrio.EpubCfiValidator.runAllValidations(root);
  } catch (_error: unknown) {
    throw new Error("INVALID_CFI");
  }
  if (root.errors.length > 0 || !root.parentPath) throw new Error("INVALID_CFI");
  return root;
}

export function targetKind(root: CfiRoot): TargetKind {
  return root.rangeStartPath && root.rangeEndPath ? "range" : "point";
}

export function validateSupportedFullCfi(source: unknown): CfiRoot {
  const root = parseCfi(source);
  const indexes = root.parentPath.localPaths.reduce<number[]>((result, path, index) => {
    if (path.indirection) result.push(index);
    return result;
  }, []);
  if (indexes.length !== 1 || indexes[0] === 0) throw new Error("UNSUPPORTED_CFI_FEATURE");
  const kind = targetKind(root);
  if (kind === "point" && !isCharacterOffset(root.parentPath.offset)) {
    throw new Error("UNSUPPORTED_CFI_FEATURE");
  }
  if (kind === "range" &&
      (!isCharacterOffset(root.rangeStartPath?.offset) ||
       !isCharacterOffset(root.rangeEndPath?.offset))) {
    throw new Error("UNSUPPORTED_CFI_FEATURE");
  }
  if (hasSideBias(root.parentPath) || hasSideBias(root.rangeStartPath) ||
      hasSideBias(root.rangeEndPath)) {
    throw new Error("UNSUPPORTED_CFI_FEATURE");
  }
  return root;
}

function isCharacterOffset(offset: CfiOffset | null | undefined): boolean {
  return offset?.type === "CHARACTER";
}

/* Readium text-quote navigation cannot preserve CFI side-bias assertions. */
function hasSideBias(path: CfiPath | null | undefined): boolean {
  if (!path) return false;
  if (assertionHasSideBias(path.offset?.assertion)) return true;
  return path.localPaths.some((localPath) =>
    localPath.steps.some((step) => assertionHasSideBias(step.assertion))
  );
}

function assertionHasSideBias(assertion: CfiAssertion | null | undefined): boolean {
  return assertion?.parameters.some((parameter) => parameter.name === "s") ?? false;
}
