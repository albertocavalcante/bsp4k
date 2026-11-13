# bsp4k

[![CI](https://github.com/albertocavalcante/bsp4k/actions/workflows/ci.yml/badge.svg)](https://github.com/albertocavalcante/bsp4k/actions/workflows/ci.yml)
[![License](https://img.shields.io/github/license/albertocavalcante/bsp4k)](LICENSE)

Idiomatic Kotlin Multiplatform library for the [Build Server Protocol (BSP) 2.1](https://build-server-protocol.github.io/docs/specification).

## Features

- **Pure Kotlin** - No Java dependencies, designed for Kotlin from the ground up
- **Multiplatform** - JVM, Native (macOS, Linux, Windows), and JS targets
- **kotlinx-serialization** - Type-safe JSON serialization with `@Serializable` data classes
- **Coroutines** - Async-first API with `suspend` functions and `Flow`
- **DSL-based** - Intuitive builder DSL for server and client configuration
- **JVM Extensions** - Built-in support for BSP JVM extensions (run/test environment, compile classpath)

## Modules

| Module | Description |
|--------|-------------|
| `bsp4k-protocol` | BSP 2.1 type definitions (`BuildTarget`, `CompileResult`, `Diagnostic`, etc.) |
| `bsp4k-server` | Server-side abstractions and DSL |
| `bsp4k-client` | Client-side abstractions and DSL |

## Quick Start

### Server

```kotlin
val config = buildServer {
    serverInfo("my-build-server", "1.0.0") // bspVersion defaults to BSP_VERSION (2.2.0)

    // Each provider needs its handler and each capability-gated handler needs its provider,
    // otherwise buildServer { } fails fast.
    capabilities {
        compileProvider = CompileProvider(listOf("kotlin", "java"))
        jvmRunEnvironmentProvider = true
    }

    lifecycle {
        onInitialize { params -> data(MyKinds.workspace, describe(params.rootUri)) }
        onInitialized { client.logMessage(MessageType.Info, "ready") }
        onExit { exitCode -> exitProcess(exitCode) } // 0 after build/shutdown, otherwise 1
    }

    workspace {
        buildTargets { WorkspaceBuildTargetsResult(targets = discoverTargets()) }
    }

    buildTarget {
        sources { params -> SourcesResult(items = resolveSourcesFor(params.targets)) }

        // Handlers run with a context whose client sends notifications; withTask always
        // ends the task and echoes the request's originId.
        compile { params ->
            val target = params.targets.single()
            val start = requestTaskStart("compile", params.originId)
                .withData(TaskStartDataKind.compileTask, CompileTask(target))
            client.withTask(start) {
                val report = CompileReport(target, originId, errors = 0, warnings = 0)
                finish(StatusCode.Ok, TaskFinishDataKind.compileReport, report)
                CompileResult(originId = params.originId, statusCode = StatusCode.Ok)
            }
        }
    }

    jvm {
        runEnvironment { params -> JvmEnvironmentResult(items = resolveJvmEnv(params.targets)) }
    }
}

val server = config.start(TransportFactory.stdio())
server.awaitTermination()
```

Every BSP 2.2 client-to-server request and notification has a hook in `buildServer { }`:
`workspace`, `buildTarget`, `debugSession`, `run` (`run/readStdin`) and the `jvm`, `java`,
`scala`, `python`, `rust`, `cargo` and `cpp` extensions, plus `onRequest`/`onNotification`
for other methods. Every server-to-client notification has a call on `server.client`
(also `client` inside handlers), including `printStdout`/`printStderr`.

The server owns the lifecycle and exposes it as `server.state`: requests before
`build/initialized` fail with ServerNotInitialized (-32002), requests after `build/shutdown`
and a second `build/initialize` fail with InvalidRequest (-32600), notifications outside the
running state are dropped, and malformed params fail with InvalidParams (-32602). Inbound
`$/cancelRequest` is handled by jsonrpc4k: a bounded session answers RequestCancelled (-32800)
and cancels the handler coroutine, while an unbounded session ignores it. jsonrpc4k dispatches
a notification only after earlier requests finish, so `run/readStdin` cannot yet reach a run
that is still in progress.

### Client

```kotlin
val config = buildClient {
    clientInfo("my-ide", "2.0.0")
    rootUri("file:///path/to/project")
    capabilities(languageIds = listOf("kotlin", "java"))

    onLogMessage { type, message ->
        println("[$type] $message")
    }

    onTaskStart { params ->
        println("Task started: ${params.message}")
    }

    onTaskFinish { params ->
        println("Task finished: ${params.message}")
    }
}

val transport = TransportFactory.stdio()
val session = config.start(transport)
val initResult = session.initialize()

// Query build targets
val targets = session.server.workspaceBuildTargets()

// Compile
val compileResult = session.server.compile(
    CompileParams(targets = listOf(myTarget))
)

session.shutdown()
session.close()
```

## Installation

Add to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.github.albertocavalcante:bsp4k-server:0.1.0")
    // or
    implementation("io.github.albertocavalcante:bsp4k-client:0.1.0")
}
```

## Building

```bash
./gradlew build
```

## License

Apache License 2.0
