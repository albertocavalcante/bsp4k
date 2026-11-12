# Java 17 artifact consumer

`java17-consumer.sh` publishes the pinned JSON-RPC source and the BSP root and JVM variants to a private temporary Maven repository. It disables composite substitution for BSP, inspects the generated POMs, module metadata and class versions, then runs separate Java 17 consumers for the root and explicit JVM coordinates. Java 16 compatibility is checked through variant resolution; no Java 16 runtime is installed or executed. The fixture repository is removed on exit.

Before any remote upload, the publication workflow runs the same publication tasks against a private file repository with composite substitution disabled and dependencies refreshed. If a dependency needed by the Linux publication task graph cannot be resolved, preflight fails before remote publication begins. POM and module metadata generation alone does not resolve every runtime or native variant. This check cannot make remote publication atomic or guarantee registry availability after it completes.

The publication workflow also disables composite substitution. It therefore requires the declared JSON-RPC artifacts to be independently resolvable; publication can fail until those artifacts and registry access are available. The fixture does not publish packages, prove registry availability, or execute native targets.


## Opt-in JSON-RPC registry

By default BSP uses its existing dependency repositories. Set `-Pjsonrpc4k.repository=github` to route only `jsonrpc4k-core`, `jsonrpc4k-transport`, and their published variants exclusively to the fixed JSON-RPC GitHub Packages repository. Set `-Pbsp4k.useLocalJsonrpc4k=false` to consume binaries rather than an available sibling composite. Any other explicit repository selector is rejected.

The route reuses `gpr.user` and `gpr.key` Gradle user properties, falling back to `GITHUB_ACTOR` and `GITHUB_TOKEN`. Keep credentials in private user configuration or the environment, not source or command arguments. GitHub Packages requires an authorized read path; availability of the declared snapshot, cross-repository access, and immutable release coordinates remain unverified. The test fixture redirects the selected repository to private local files; it does not authenticate to GitHub or prove registry access.
