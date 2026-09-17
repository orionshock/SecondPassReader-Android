# Reader CFI runtime

TypeScript is the authoritative source for the browser runtime injected by the Android Reader.
Android ships a readable development asset from `app/src/main/assets` and a minified,
same-path override from `app/src/release/assets`. Runtime builds do not require Node.

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
