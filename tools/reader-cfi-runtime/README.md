# Reader CFI runtime

TypeScript is the authoritative source for the browser runtime injected by the Android Reader.
Android ships one generated asset at
`app/src/main/assets/reader/cfi/secondpass-epub-cfi-runtime.js` and does not require Node.

Requires Node 24 LTS and npm 11.

```text
npm ci
npm run typecheck
npm run build
npm run check
```

`build` regenerates the committed IIFE asset. `check` typechecks, rebuilds in memory, and fails if
the committed asset differs. The root Gradle `readerCfiRuntimeCheck` verifies the embedded source
digest offline without invoking Node.
