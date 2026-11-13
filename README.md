# bsp4k

[![CI](https://github.com/albertocavalcante/bsp4k/actions/workflows/ci.yml/badge.svg)](https://github.com/albertocavalcante/bsp4k/actions/workflows/ci.yml)
[![License](https://img.shields.io/github/license/albertocavalcante/bsp4k)](LICENSE)

Idiomatic Kotlin Multiplatform library for the [Build Server Protocol (BSP) 2.2](https://build-server-protocol.github.io/docs/specification).

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
    serverInfo("my-build-server", "1.0.0")

    capabilities {
        compileProvider = CompileProvider(listOf("kotlin", "java"))
        testProvider = TestProvider(listOf("kotlin", "java"))
        dependencySourcesProvider = true
        resourcesProvider = true
    }

    workspace {
        buildTargets {
            WorkspaceBuildTargetsResult(targets = discoverTargets())
        }
    }

    buildTarget {
        sources { params ->
            SourcesResult(items = resolveSourcesFor(params.targets))
        }

        compile { params ->
            CompileResult(statusCode = StatusCode.Ok)
        }

        test { params ->
            TestResult(statusCode = StatusCode.Ok)
        }
    }

    jvm {
        runEnvironment { params ->
            JvmEnvironmentResult(items = resolveJvmEnv(params.targets))
        }
    }
}

val transport = TransportFactory.stdio()
val server = config.start(transport)
```

### Client

```kotlin
val config = buildClient {
    clientInfo("my-ide", "2.0.0") // bspVersion defaults to BSP_VERSION (2.2.0)
    rootUri("file:///path/to/project")
    capabilities(languageIds = listOf("kotlin", "java"))

    onLogMessage { params -> println("[${params.type}] ${params.message}") }
    onTaskStart { params -> println("Task started: ${params.message}") }
    onTaskFinish { params -> println("Task finished: ${params.message}") }
    onPrintStdout { params -> print(params.message) }
}

val transport = TransportFactory.stdio()
val session = config.start(transport)

// Validates the server result, then sends build/initialized.
val init = session.initialize()
println(init.capabilities)

// Calls throw SessionStateException before initialize() and after shutdown()/close().
val targets = session.server.workspaceBuildTargets()
val compiled = session.server.compile(CompileParams(targets = targets.targets.map { it.id }))

// Per-call timeout (default 30 s) and extension namespaces.
val javac = session.server.java.javacOptions(JavacOptionsParams(listOf(myTarget)), timeout = 5.seconds)
session.server.cargo.setFeatures(SetCargoFeaturesParams("pkg", setOf("serde")))

session.shutdown()
```

Every BSP 2.2 client-to-server request and notification has a call on `session.server`
(core, plus `jvm`, `java`, `scala`, `python`, `rust`, `cargo`, `cpp` namespaces and
`readStdin`). Every server-to-client notification has an `on...` hook in `buildClient { }`.

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
