# Vendored BSP 2.2.0 Smithy specification

These files are the machine-readable definition of Build Server Protocol 2.2.0, used only by the
conformance tests in `bsp4k-protocol` (`SmithyConformanceTest`). They are test resources and are never
published.

- Upstream: https://github.com/build-server-protocol/build-server-protocol
- Commit: `9097c78224910a4a285ba3207e55dd33c9e9da97`
- Upstream paths: `spec/src/main/resources/META-INF/smithy/` (`bsp/bsp.smithy`, `bsp/tags.smithy`,
  `bsp/version`, `bsp/extensions/*.smithy`, `traits/traits.smithy`)
- Local layout: `bsp.smithy`, `tags.smithy`, `version`, `extensions/*.smithy` come from upstream `bsp/`;
  `traits/traits.smithy` comes from upstream `traits/` and defines the custom traits (`data`, `dataKind`,
  `enumKind`, `jsonRequest`, `jsonNotification`, `jsonRPC`, `untaggedUnion`, `set`, `docsPriority`) that the
  other files use.
- License: Apache-2.0, as the upstream repository.
- Checksums: `PINS.txt` (sha256), verified by the test. The files are byte-for-byte copies; `bsp.smithy`
  contains one trailing space, hence `../.gitattributes`.

To bump the pin, replace every file from the new upstream commit, regenerate `PINS.txt`, update this note,
and fix or allow-list (with a reason) whatever `SmithyConformanceTest` then reports.
