# Reference material

`secondpass-client/` is a source snapshot of the working React/web
`@secondpass/client` package from:

```text
C:\projects\SecondPassReaderClient\packages\secondpass-client
```

It exists only to inform the independent Kotlin implementation in
`:spl-client`. It is not part of the Gradle build, an npm workspace, or Android
runtime code. Do not import or execute it from `:app` or `:spl-client`; translate
the public contract and behavior deliberately across the platform boundary.

Generated source-package state (`dist/`, `node_modules/`, and
`tsconfig.tsbuildinfo`) is intentionally excluded.
