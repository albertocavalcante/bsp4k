# bsp4k

[![CI](https://github.com/albertocavalcante/bsp4k/actions/workflows/ci.yml/badge.svg)](https://github.com/albertocavalcante/bsp4k/actions/workflows/ci.yml)
[![License](https://img.shields.io/github/license/albertocavalcante/bsp4k)](LICENSE)

Kotlin Multiplatform implementation of the
[Build Server Protocol (BSP) **2.2.0**](https://build-server-protocol.github.io/docs/specification):
the protocol model, a DSL for build servers and a typed client, on top of
[jsonrpc4k](https://github.com/albertocavalcante/jsonrpc4k). bsp4k has no backward-compatibility
promise before 1.0: APIs change when the protocol or the design requires it.

## Modules

| Module | Description |
|--------|-------------|
| `bsp4k-protocol` | Every BSP 2.2.0 type, method name, capability and `dataKind` payload, as `@Serializable` classes |
| `bsp4k-server` | `buildServer { }` DSL, session lifecycle state machine, task helpers and the server-to-client notification API |
| `bsp4k-client` | `buildClient { }` DSL, `BuildClientSession` with a typed proxy for every server request |
| `example` | A runnable build server (`./gradlew :example:run`) |

## Coverage

BSP 2.2.0 plus all extensions published with it. "Server" means `buildServer { }` has a handler
or lifecycle hook for the method and `server.client` (also `client` in handlers) can send it;
"Client" means `session.server` calls it or `buildClient { }` has an `on...` hook for it.
Typed `dataKind` payloads are available as `BspDataKind` values (`TaskStartDataKind`,
`TaskFinishDataKind`, ...).

| Area | Methods | Server | Client |
|------|---------|:------:|:------:|
| Lifecycle | `build/initialize`, `build/initialized`, `build/shutdown`, `build/exit` | yes | yes |
| Workspace | `workspace/buildTargets`, `workspace/reload` | yes | yes |
| Build target requests | `buildTarget/sources`, `inverseSources`, `dependencySources`, `dependencyModules`, `resources`, `outputPaths`, `cleanCache`, `destinations` | yes | yes |
| Build, test, run, debug | `buildTarget/compile`, `test`, `run`, `debugSession/start` | yes | yes |
| Run I/O (unstable) | `run/printStdout`, `run/printStderr` (server to client), `run/readStdin` (client to server) | yes | yes |
| Notifications to the client | `build/showMessage`, `logMessage`, `publishDiagnostics`, `taskStart`, `taskProgress`, `taskFinish`, `buildTarget/didChange` | yes | yes |
| JVM extension | `buildTarget/jvmRunEnvironment`, `jvmTestEnvironment`, `jvmCompileClasspath` | yes | yes |
| Java extension | `buildTarget/javacOptions` | yes | yes |
| Scala extension | `buildTarget/scalacOptions`, `scalaTestClasses`, `scalaMainClasses` | yes | yes |
| Python extension | `buildTarget/pythonOptions` | yes | yes |
| Rust extension | `buildTarget/rustWorkspace` | yes | yes |
| Cargo extension | `workspace/cargoFeaturesState`, `workspace/setCargoFeatures` | yes | yes |
| C++ extension | `buildTarget/cppOptions` | yes | yes |
| Cancellation | `$/cancelRequest` | handled by jsonrpc4k | handled by jsonrpc4k |

Methods outside this table can be served with `onRequest`/`onNotification` in `buildServer { }`
and `onNotification` in `buildClient { }`.

## Targets and transports

- **Targets:** JVM (toolchain and bytecode level 17), JS (browser and Node.js), macOS x64 and
  arm64, Linux x64 and arm64, Windows (mingwX64).
- **Transports:** a session runs on any jsonrpc4k `Transport`: `TransportFactory.stdio()`,
  `SocketTransport.connect(host, port)`, `ProcessTransport.start(command)` or your own implementation.
  Which of them a target provides is decided by jsonrpc4k. Messages use `Content-Length` framing.
- **Discovery and launch (JVM):** `BspConnectionDiscovery` finds connection files in the workspace
  `.bsp` directory and then in the user and system `bsp` data directories, skipping invalid files with
  a reported reason. `BuildClientConfig.launch(details, workspaceRoot)` starts the server through
  `ProcessTransport` and returns a `LaunchedBuildServer` that owns the child process.
- **Sessions:** `start(transport)` runs requests concurrently within bounds (default 16 running,
  256 queued; override with `policy`, `inboundLimits` and `outboundLimits`) and honors `$/cancelRequest`:
  a cancelled request is answered with RequestCancelled (-32800) and its handler coroutine is cancelled.
  Notifications are ordering barriers, so `build/initialized` runs before later requests and
  task notifications arrive in order.

## Quick start

bsp4k is not published yet (`0.1.0-SNAPSHOT`); build it from source with `./gradlew build`.

### Server

```kotlin
val config = buildServer {
    serverInfo("my-build-server", "1.0.0") // bspVersion defaults to BSP_VERSION (2.2.0)

    // Every provider needs its handler and every capability-gated handler needs its provider,
    // otherwise buildServer { } fails fast.
    capabilities {
        compileProvider = CompileProvider(languageIds = listOf("kotlin"))
        runProvider = RunProvider(languageIds = listOf("kotlin"))
    }

    lifecycle {
        onInitialized { client.logMessage(MessageType.Info, "ready") }
        onExit { exitCode -> exitProcess(exitCode) } // 0 after build/shutdown, otherwise 1
    }

    workspace {
        buildTargets { WorkspaceBuildTargetsResult(targets = discoverTargets()) }
    }

    buildTarget {
        sources { params -> SourcesResult(items = params.targets.map(::sourcesOf)) }

        // withTask sends build/taskStart, always ends the task, and echoes the request's originId.
        compile { params ->
            val target = params.targets.single()
            val start = requestTaskStart("compile", params.originId)
                .withData(TaskStartDataKind.compileTask, CompileTask(target))
            client.withTask(start) {
                finish(StatusCode.Ok, TaskFinishDataKind.compileReport, CompileReport(target, originId, errors = 0, warnings = 0))
                CompileResult(originId = params.originId, statusCode = StatusCode.Ok)
            }
        }

        // run/printStdout needs the originId of the run request.
        run { params ->
            client.withTask(requestTaskStart("run", params.originId)) { printStdout("hello") }
            RunResult(originId = params.originId, statusCode = StatusCode.Ok)
        }
    }
}

val server = config.start(TransportFactory.stdio())
server.awaitTermination() // keeps the process alive until the client exits or the transport ends
```

Handler hooks exist for every client-to-server request and notification: `workspace`,
`buildTarget`, `debugSession`, `run` (`readStdin`) and the `jvm`, `java`, `scala`, `python`,
`rust`, `cargo` and `cpp` extensions. The server owns the lifecycle and publishes it as
`server.state`: requests before `build/initialized` fail with ServerNotInitialized (-32002),
requests after `build/shutdown` and a second `build/initialize` fail with InvalidRequest
(-32600), notifications outside the running state are dropped, and malformed params fail with
InvalidParams (-32602). See `example/` for a complete server with destinations, `run/print`
output and the JVM extension.

Requests run concurrently, so a long `buildTarget/compile` does not block `workspace/buildTargets`,
and `$/cancelRequest` cancels the running handler (the client gets RequestCancelled, -32800).
Tune it with `config.start(transport, scope, policy = BoundedDispatchPolicy(4, 64))`; the
`inboundLimits` and `outboundLimits` parameters bound frame and retained message sizes.

### Client

```kotlin
val config = buildClient {
    clientInfo("my-ide", "2.0.0") // bspVersion defaults to BSP_VERSION (2.2.0)
    rootUri("file:///path/to/project")
    capabilities(languageIds = listOf("kotlin"))

    onLogMessage { params -> println("[${params.type}] ${params.message}") }
    onTaskStart { params -> println("started ${params.taskId.id} (origin ${params.originId})") }
    onTaskFinish { params -> println("finished ${params.taskId.id}: ${params.status}") }
    onPrintStdout { params -> print(params.message) }
}

val session = config.start(transport) // any jsonrpc4k Transport

// Validates the server's result, then sends build/initialized.
val init = session.initialize()

// Calls throw SessionStateException before initialize() and after shutdown() or close().
val targets = session.server.workspaceBuildTargets().targets.map { it.id }
val compiled = session.server.compile(CompileParams(targets = targets, originId = "compile-1"))

// Per-call timeout (default 30 s) and one namespace per extension.
val javac = session.server.java.javacOptions(JavacOptionsParams(targets), timeout = 5.seconds)

session.shutdown() // build/shutdown, then build/exit
```

## Testing

`bsp4k-server` runs framed end-to-end tests (`BspEndToEndTest`): a real `buildServer { }`
session and a real `buildClient { }` session joined by an in-memory pipe that delivers
`Content-Length` frames in small chunks. They cover the handshake, targets, sources,
compile with task notifications, run with `run/printStdout` and `run/readStdin`, debug sessions,
destinations, an extension request, `buildTarget/didChange` and shutdown.

## Known limitations

- `run/readStdin` cannot reach a `buildTarget/run` that is still running: jsonrpc4k dispatches a
  notification only after earlier requests finish.
- `run/printStdout`, `run/printStderr` and `run/readStdin` are unstable in BSP 2.2.0 and may change.
- Handlers must cooperate with coroutine cancellation (suspend or `ensureActive`); a blocking handler
  keeps its concurrency slot until it returns, although its request is answered as cancelled.
- Per-target transport support (stdio, sockets) is whatever jsonrpc4k provides on that target.
- Not published to Maven Central yet; the API is unstable.

## License

Apache License 2.0
