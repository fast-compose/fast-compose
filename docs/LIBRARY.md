# Fast Compose library

The `fast-compose` library renders Compose code at runtime in Kotlin Multiplatform apps on Android, iOS, desktop (JVM)
and the web (JS and Wasm).

```kotlin
dependencies {
    implementation("io.github.fast-compose:fast-compose:x.y.z")
}
```

Replace `x.y.z` with the latest version: [![Maven Central](https://img.shields.io/maven-central/v/io.github.fast-compose/fast-compose)](https://central.sonatype.com/artifact/io.github.fast-compose/fast-compose)

It includes the parser, the interpreter, and the foundation, Material 3, icons, resources and network image
bindings.

## Contents

- [ComposeCode](#composecode)
- [FastComposePreview](#fastcomposepreview)
- [BasicComposePreview](#basiccomposepreview)
- [FastComposeContent](#fastcomposecontent)
- [FastComposeLogs](#fastcomposelogs)
- [Registries](#registries)
- [Resources](#resources)
- [Network images](#network-images)
- [Multiple files](#multiple-files)
- [Library code on the JVM](#library-code-on-the-jvm)
- [Crashes while drawing](#crashes-while-drawing)

## ComposeCode

`ComposeCode` holds Kotlin Compose source code and its parsed form. Every composable below takes one.

```kotlin
val code = rememberComposeCode(initialSource)
```

```kotlin
fun rememberComposeCode(initialSource: String, path: String? = null, sources: SourceProvider? = null): ComposeCode
```

| Parameter | Description |
|---|---|
| `initialSource` | The first source code |
| `path` | Path of this file in a project; used with `sources` |
| `sources` | Finds other project files the code uses; see [Multiple files](#multiple-files) |

Call `update` with new source, e.g. on every change in an editor. It is parsed in the background after a 150 ms
pause in typing.

| Member | Description |
|---|---|
| `source` | The current source code |
| `program` | The last parsed program without errors; previews keep showing it while the code has errors |
| `diagnostics` | Parse errors and warnings of the current source |
| `logs` | `println` output, runtime errors and warnings, oldest first |
| `errorLines` | Lines with runtime errors, e.g. to highlight them in an editor |
| `dependencies` | Other project files the code uses, loaded through `sources` |
| `isLoading` | True while the files of the first program are being loaded |
| `update(source)` | Sets new source code |
| `reloadSources()` | Loads `dependencies` again, e.g. after another file changed |
| `clearLogs()` | Removes all log lines |
| `reportCrash(error)` | Logs an exception thrown outside the interpreter |

## FastComposePreview

Renders every `@Preview` function in the code, with the full API: foundation, Material 3, icons, resources and
network images.

```kotlin
FastComposePreview(code, Modifier.fillMaxSize())
```

```kotlin
@Composable
fun FastComposePreview(
    code: ComposeCode,
    modifier: Modifier = Modifier,
    registry: Registry = rememberFullRegistry(),
    resources: ResourceProvider = LocalResourceProvider.current,
)
```

Each preview is drawn in its own frame with a label, inside a light or dark Material theme. `@Preview` parameters
work as in Android Studio:

| Parameter | Effect |
|---|---|
| `name` | Label above the preview |
| `widthDp`, `heightDp` | Size of the frame |
| `device` | Size of a device, e.g. `Devices.PIXEL_4` |
| `showBackground`, `backgroundColor` | Background of the frame |
| `fontScale` | Text size |
| `locale` | Language of string resources |
| `uiMode` | Dark theme with `Configuration.UI_MODE_NIGHT_YES` |

Only `@Composable` functions with `@Preview` are shown. A function with several `@Preview` annotations is shown once
for each.

## BasicComposePreview

Like `FastComposePreview`, but with the foundation API only: layouts, `Text`, state, modifiers, drawing and
animation. It is small and fast to set up, and suits apps that do not use Material. Material components, icons,
resources and network images show a hint instead.

```kotlin
BasicComposePreview(code)
```

```kotlin
@Composable
fun BasicComposePreview(
    code: ComposeCode,
    modifier: Modifier = Modifier,
    registry: Registry = rememberBasicRegistry(),
)
```

## FastComposeContent

Renders one composable as plain content, without preview frames, labels or `@Preview` settings. Use it for screens
written at runtime: server-driven UI, generated UI or live editors that show a single result.

```kotlin
FastComposeContent(code, entry = "ProfileScreen")
```

```kotlin
@Composable
fun FastComposeContent(
    code: ComposeCode,
    modifier: Modifier = Modifier,
    entry: String? = null,
    registry: Registry = rememberFullRegistry(),
    resources: ResourceProvider = LocalResourceProvider.current,
)
```

`entry` names a top-level `@Composable` function without parameters. When it is null, the first `@Preview` function
is rendered. Runtime errors are shown where they occur.

## FastComposeLogs

Shows parse errors, runtime errors, warnings and `println` output of the code, scrolled to the newest line.

```kotlin
FastComposeLogs(
    code = code,
    modifier = Modifier.height(180.dp),
    onLineClick = { line -> editor.moveTo(line) },
)
```

```kotlin
@Composable
fun FastComposeLogs(
    code: ComposeCode,
    modifier: Modifier = Modifier,
    title: String = "Logs",
    showDiagnostics: Boolean = true,
    onLineClick: ((line: Int) -> Unit)? = null,
)
```

| Parameter | Description |
|---|---|
| `title` | Title above the lines |
| `showDiagnostics` | Shows parse errors before the logs |
| `onLineClick` | Called with the source line of a clicked error |

Text is selectable, and colors come from `MaterialTheme`.

## Registries

A registry holds the bindings: the Compose APIs that code can call. Two presets are built once and shared:

| Function | APIs |
|---|---|
| `rememberFullRegistry()` | Foundation, Material 3, icons, resources, network images |
| `rememberBasicRegistry()` | Foundation |

Both take extra binding packs and inline extensions, layered on the preset without copying it:

```kotlin
val registry = rememberFullRegistry(MyDesignSystem) {
    composable("Logo(size)") { a -> Logo(a.dp("size", 48.dp)) }
}

FastComposePreview(code, registry = registry)
```

A pack wraps your own components:

```kotlin
object MyDesignSystem : BindingPack {
    override fun install(registry: Registry) = with(registry) {
        composable("BrandButton(text, onClick, modifier)") { a ->
            BrandButton(a.string("text"), a.action("onClick"), a.modifier())
        }
    }
}
```

See [BINDINGS.md](BINDINGS.md) for writing bindings.

## Resources

`painterResource(R.drawable.logo)`, `stringResource(Res.string.title)` and the other `R`/`Res` forms read files from a
`ResourceProvider` passed to the preview. See [RESOURCES.md](RESOURCES.md).

## Network images

The full API includes Coil's `AsyncImage` and `rememberAsyncImagePainter`, so code can show images from the web:

```kotlin
AsyncImage(model = "https://picsum.photos/200", contentDescription = null)
```

Images load with Coil's singleton `ImageLoader`, which the host app can configure with
`setSingletonImageLoaderFactory`. Images that fail to load draw a placeholder and are logged.

## Multiple files

By default the code must declare everything it uses besides bindings. With a `SourceProvider`, it can use functions
and classes from other files of a project, following its imports:

```kotlin
val code = rememberComposeCode(source, path = "ui/ProfileScreen.kt", sources = provider)
```

See [MULTIPLE_FILES.md](MULTIPLE_FILES.md).

## Library code on the JVM

Library APIs without a binding run as stand-ins. On the JVM, a `JarResolver` runs the real library code instead,
from the project's library jars (e.g. as Gradle resolved them):

```kotlin
val libraries = JarResolver(jars) // List<File>; close() it when the project changes

CompositionLocalProvider(LocalExternalResolver provides libraries) {
    FastComposePreview(code)
}
```

It finds classes and objects, a package's top-level functions and properties, and members of the values library code
returns, with named and default arguments. Library composables run too, with their default arguments, value class
parameters (`Color`, `Dp`) and content lambdas written in the preview. Kotlin, coroutines, Compose and the JDK always
come from the app, so values pass freely between library code and bindings; libraries need a Compose version the app
can link, and Android-only code doesn't run on the desktop. Any other host can plug in its own `ExternalResolver`;
without one (the web, iOS) library APIs stay stand-ins.

## Crashes while drawing

Errors in the interpreted code never crash the app; they are shown where they occur and logged. Exceptions thrown by
Compose itself while it measures or draws a preview can be caught on desktop and logged with `reportCrash`:

```kotlin
fun main() = application {
    val code = rememberComposeCode(source)
    val handler = WindowExceptionHandlerFactory { WindowExceptionHandler { code.reportCrash(it) } }
    CompositionLocalProvider(LocalWindowExceptionHandlerFactory provides handler) {
        Window(onCloseRequest = ::exitApplication) { FastComposePreview(code) }
    }
}
```
