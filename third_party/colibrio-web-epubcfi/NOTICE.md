# colibrio-web-epubcfi

- Source: https://bitbucket.org/colibrio/colibrio-web-epubcfi
- Pinned revision: `ce0649234f76cc71ab0ed63ea344566f8a083466`
- Upstream version at revision: `1.1.0`
- License: MIT; see `LICENSE`
- Browser bundle: `app/src/main/assets/reader/cfi/colibrio-epubcfi-1.1.0.min.js`
- Bundle SHA-256: `661515025940C5D1AD2F2238FE03455D4616A3B00BD58978EC59823D818B24E0`

The checked-in browser bundle is generated from the pinned upstream source by
`tools/reader-cfi/rebuild-colibrio-bundle.ps1`. Node and npm are development-only
inputs to that explicit rebuild; Android builds consume the checked-in asset.
The upstream lockfile constrains package dependencies, but Node and npm versions
are not pinned here, so the audited bundle hash is the authoritative build input
rather than a claim that every development toolchain reproduces identical bytes.
Normal offline validation verifies that hash and that the complete MIT license is
packaged with the Android asset.
