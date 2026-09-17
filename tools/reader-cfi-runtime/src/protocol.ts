import { colibrio, type CfiAssertion, type CfiOffset, type CfiPath, type CfiRoot } from "./colibrio";

import { Protocol as P, type RuntimeErrorCode, type RuntimeResult, type TargetKind } from "./protocol.generated";
export type { RuntimeErrorCode, RuntimeResult, TargetKind } from "./protocol.generated";
export const { RUNTIME_VERSION, CONTEXT_LENGTH, SELECTION_CONTEXT_LENGTH,
  MOVEMENT_QUOTE_LENGTH, MAX_SELECTED_TEXT_LENGTH } = P;
export const MIN_VISIBLE_EXTENT_PIXELS = 0.5;

export function safely<T>(operation: () => T): RuntimeResult<T> {
  try {
    return { [P.FIELD_OK]: true, [P.FIELD_VALUE]: operation() };
  } catch (error: unknown) {
    return { [P.FIELD_OK]: false, [P.FIELD_ERROR]: { [P.FIELD_CODE]: classifyError(error) } };
  }
}

function classifyError(error: unknown): RuntimeErrorCode {
  const code = error instanceof Error ? error.message : "";
  switch (code) {
    case P.ERROR_INVALID_CFI: case P.ERROR_UNSUPPORTED_CFI_FEATURE: case P.ERROR_INVALID_PACKAGE_DOCUMENT:
    case P.ERROR_PACKAGE_TARGET_NOT_FOUND: case P.ERROR_PACKAGE_TARGET_MISMATCH:
    case P.ERROR_DOM_TARGET_NOT_FOUND: case P.ERROR_INVALID_RANGE: case P.ERROR_SELECTION_UNAVAILABLE:
    case P.ERROR_VISIBLE_POSITION_UNAVAILABLE: case P.ERROR_MOVEMENT_ANCHOR_UNAVAILABLE:
    case P.ERROR_UNSUPPORTED_FIXED_LAYOUT: case P.ERROR_UNSUPPORTED_SCROLL_MODE:
    case P.ERROR_UNSUPPORTED_WRITING_MODE: case P.ERROR_RESULT_TOO_LARGE:
      return code;
    default:
      return P.ERROR_CFI_RUNTIME_FAILURE;
  }
}

export function parseCfi(source: unknown): CfiRoot {
  if (typeof source !== "string") throw new Error(P.ERROR_INVALID_CFI);
  let root: CfiRoot;
  try {
    root = colibrio.EpubCfiParser.parse(source);
    colibrio.EpubCfiValidator.runAllValidations(root);
  } catch (_error: unknown) {
    throw new Error(P.ERROR_INVALID_CFI);
  }
  if (root.errors.length > 0 || !root.parentPath) throw new Error(P.ERROR_INVALID_CFI);
  return root;
}

export function targetKind(root: CfiRoot): TargetKind {
  return root.rangeStartPath && root.rangeEndPath ? P.KIND_RANGE : P.KIND_POINT;
}

export function validateSupportedFullCfi(source: unknown): CfiRoot {
  const root = parseCfi(source);
  const indexes = root.parentPath.localPaths.reduce<number[]>((result, path, index) => {
    if (path.indirection) result.push(index);
    return result;
  }, []);
  if (indexes.length !== 1 || indexes[0] === 0) throw new Error(P.ERROR_UNSUPPORTED_CFI_FEATURE);
  const kind = targetKind(root);
  if (kind === P.KIND_POINT && !isCharacterOffset(root.parentPath.offset)) {
    throw new Error(P.ERROR_UNSUPPORTED_CFI_FEATURE);
  }
  if (kind === P.KIND_RANGE &&
      (!isCharacterOffset(root.rangeStartPath?.offset) ||
       !isCharacterOffset(root.rangeEndPath?.offset))) {
    throw new Error(P.ERROR_UNSUPPORTED_CFI_FEATURE);
  }
  if (hasSideBias(root.parentPath) || hasSideBias(root.rangeStartPath) ||
      hasSideBias(root.rangeEndPath)) {
    throw new Error(P.ERROR_UNSUPPORTED_CFI_FEATURE);
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
