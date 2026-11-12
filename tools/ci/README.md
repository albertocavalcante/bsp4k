# Java 17 artifact consumer

`java17-consumer.sh` publishes the pinned JSON-RPC source and the BSP root and JVM variants to a private temporary Maven repository. It disables composite substitution for BSP, inspects the generated POMs, module metadata and class versions, then runs separate Java 17 consumers for the root and explicit JVM coordinates. Java 16 compatibility is checked through variant resolution; no Java 16 runtime is installed or executed. The fixture repository is removed on exit.

Before any remote upload, the publication workflow runs the same publication tasks against a private file repository with composite substitution disabled and dependencies refreshed. If a dependency needed by the Linux publication task graph cannot be resolved, preflight fails before remote publication begins. POM and module metadata generation alone does not resolve every runtime or native variant. This check cannot make remote publication atomic or guarantee registry availability after it completes.

The publication workflow also disables composite substitution. It therefore requires the declared JSON-RPC artifacts to be independently resolvable; publication can fail until those artifacts and registry access are available. The fixture does not publish packages, prove registry availability, or execute native targets.
