export interface CfiAssertion {
  readonly parameters: readonly { readonly name: string }[];
}
export interface CfiOffset { readonly type: string; readonly assertion?: CfiAssertion | null; }
export interface CfiLocalPath {
  readonly indirection: boolean;
  readonly steps: readonly { readonly assertion?: CfiAssertion | null }[];
}
export interface CfiPath {
  readonly offset?: CfiOffset | null;
  readonly localPaths: readonly CfiLocalPath[];
}
export interface CfiRoot {
  readonly errors: readonly unknown[];
  readonly parentPath: CfiPath;
  readonly rangeStartPath?: CfiPath | null;
  readonly rangeEndPath?: CfiPath | null;
}
export interface ColibrioResolvedTarget {
  readonly indirectionErrors: readonly unknown[];
  getParserErrors(): readonly unknown[]; getResolverErrors(): readonly unknown[];
  getTargetElement(): Element | null; createDomRange(): Range;
  hasErrors(): boolean; hasRangePaths(): boolean; isDomRange(): boolean;
  isEveryIndirectionResolved(): boolean; isEveryStepAndOffsetParsed(): boolean;
  isEveryStepResolved(): boolean; isOwnedBySingleDocument(): boolean;
}
export interface ColibrioResolver {
  continueResolving(document: Document, url: URL): { readonly element: Element } | null;
  getResolvedTarget(): ColibrioResolvedTarget;
}
export interface ColibrioBuilder {
  appendLocalPathTo(element: Element): void;
  appendTerminalDomPosition(node: Node, offset: number): void;
  appendTerminalDomRange(range: CfiDomRange): void;
  setTextAssertionOptions(options: {
    readonly preLength: number; readonly postLength: number;
    readonly snapToWordBoundaries: boolean;
  }): void;
  toString(): string;
}
export interface CfiDomRange {
  readonly collapsed: boolean;
  readonly startContainer: Node; readonly startOffset: number;
  readonly endContainer: Node; readonly endOffset: number;
  readonly commonAncestorContainer: Node;
}
interface ColibrioGlobal {
  readonly EpubCfiParser: { parse(source: string): CfiRoot };
  readonly EpubCfiValidator: { runAllValidations(root: CfiRoot): void };
  readonly EpubCfiStringifier: { stringifyRootNode(root: CfiRoot): string };
  readonly EpubCfiResolver: new (
    root: CfiRoot | string,
    options: { readonly processTextAssertions: boolean; readonly textAssertionSearchDistance: number }
  ) => ColibrioResolver;
  readonly EpubCfiBuilder: new () => ColibrioBuilder;
}
declare global {
  interface Window {
    SecondPassColibrio?: ColibrioGlobal;
    readium?: { readonly isFixedLayout?: boolean; readonly isReflowable?: boolean };
  }
}
export const colibrio: ColibrioGlobal = new Proxy({} as ColibrioGlobal, {
  get(_target, property: keyof ColibrioGlobal) {
    const installed = window.SecondPassColibrio;
    if (!installed) throw new Error("COLIBRIO_UNAVAILABLE");
    return installed[property];
  }
});
