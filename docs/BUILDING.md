# Building and testing

## Requirements

- **JDK 17 or newer.** Gradle downloads a matching JDK for its toolchain if needed.

## Project layout

| Folder | What it is | Published |
|---|---|---|
| `fast-compose` | The library: parser, interpreter, bindings and preview UI, one package each | Yes |
| `binding-check` | Checks bindings against the AndroidX API files | No |
| `app/shared` | The demo app's UI: editor, toolbar, preview and logs | No |
| `app/android`, `app/desktop`, `app/web` | Launchers of the demo app for each platform | No |

The library targets Android, iOS (`iosArm64`, `iosSimulatorArm64`), JVM, JS and Wasm. The demo app has no iOS
version. iOS targets compile only on macOS; on other machines Gradle skips them.

## Running the demo app

| Platform | Command |
|---|---|
| Desktop | `./gradlew :app:desktop:run` |
| Web (Wasm) | `./gradlew :app:web:wasmJsBrowserDevelopmentRun` |
| Web (JS, older browsers) | `./gradlew :app:web:jsBrowserDevelopmentRun` |
| Android | `./gradlew :app:android:installDebug` |

## Tests

Run every JVM test, including the binding check:

```bash
./gradlew jvmTest
```

Or one part at a time, filtered by package:

| Command | Covers |
|---|---|
| `./gradlew :fast-compose:jvmTest --tests "*.parser.*"` | Lexer and parser: syntax, declarations, `@Preview`, imports, referenced names |
| `./gradlew :fast-compose:jvmTest --tests "*.interpreter.*"` | Language features, overloads, multiple files, time limits, benchmarks |
| `./gradlew :fast-compose:jvmTest --tests "*.bindings.*"` | Bindings evaluated in plain code, resource providers |
| `./gradlew :fast-compose:jvmTest --tests "*.ui.*"` | `@Preview` settings |
| `./gradlew :binding-check:jvmTest` | Binding signatures, unread parameters, coverage |

Run a single test class with `--tests`:

```bash
./gradlew :fast-compose:jvmTest --tests "ma7moud3ly.fast.compose.interpreter.MultiFileTest"
```

`./gradlew allTests` runs the tests on every target, including JS and Wasm in a headless browser.

## Binding check

```bash
./gradlew :binding-check:jvmTest
```

The check compares every binding with the AndroidX API files in `binding-check/api` and fails on:

- unknown parameters, missing required parameters, a different parameter order, or a trailing lambda that fills a
  different parameter than in Compose
- parameters that a binding declares but never reads

The full report, with warnings and coverage, is written to `binding-check/build/reports/binding-check.txt`.
See [COVERAGE.md](COVERAGE.md) for how to read it.

| File | Purpose |
|---|---|
| `binding-check/allowlist.txt` | Findings to ignore, one per line, exactly as the report prints them |
| `binding-check/excluded.txt` | APIs left out of the coverage numbers, such as Android-only APIs |
| `binding-check/unused-allowlist.txt` | Parameters that are not read on purpose, as `Binding.parameter` |
| `binding-check/update-api.sh` | Downloads the API files; update its versions when the Compose version changes |

## Benchmarks

```bash
./gradlew :fast-compose:jvmTest --tests "*InterpreterBenchmarkTest*" -i
```

Prints the median time of recursion, loops, binding calls and collection code. See
[PERFORMANCE.md](PERFORMANCE.md#workloads) for what each benchmark measures.

## Continuous integration

[`.github/workflows/build.yml`](../.github/workflows/build.yml) runs:

| Event | Jobs |
|---|---|
| Pull request to `master` | All JVM tests, then compiles the desktop, web and Android demo apps; compiles the libraries for iOS on macOS |
| Tag `v*` | The same checks, then publishes to Maven Central from macOS, with the iOS artifacts, and creates a GitHub release |
| Started by hand | [`benchmark.yml`](../.github/workflows/benchmark.yml) runs the interpreter benchmark on a GitHub-hosted runner |

