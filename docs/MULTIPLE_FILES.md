# Multiple files

By default a preview runs one file. Imports are ignored, so the file must declare everything it uses besides bindings.

With a `SourceProvider`, a file can use functions, classes, objects and properties from other files of a project:

```kotlin
val code = rememberComposeCode(source, path = "ui/ProfileScreen.kt", sources = provider)
FastComposePreview(code)
```

## SourceProvider

```kotlin
fun interface SourceProvider {
    suspend fun find(packageName: String, name: String): SourceFile?
}
```

`find` returns the file in `packageName` that declares the top-level `name`, or null. Hosts implement it, e.g. an
IntelliJ plugin with its symbol index, or a VS Code extension by searching the workspace.

## Loading

After each parse, `SourceLoader` collects the names the file uses but does not declare and looks each one up:

1. an explicit import (or its alias)
2. the file's own package
3. star imports

Found files are parsed and searched the same way, until no new file is found. Only reached files are loaded.

- Names imported from library packages (`androidx`, `kotlin`, `kotlinx`, `java`, …) are not looked up; they come from
  bindings.
- Files are cached by path and text. A file with parse errors keeps its last version without errors.
- `ComposeCode.reloadSources()` loads the files again, e.g. after another file changed.
- `ComposeCode.dependencies` holds the loaded files.

## Running

Each file sees declarations in Kotlin's order: its own, then explicit imports, then its package, then star imports.
Private declarations stay in their file, and code always runs with the declarations of the file it is written in.
Only the previewed file's `@Preview` functions are shown.

## Errors

- Names imported from project packages that no file declares are logged as warnings.
- Parse errors and runtime errors in other files are logged with their file, e.g. `ProfileCard.kt:12: …`.
  `LogLine.file` and `RuntimeError.file` hold the path; they are null for the previewed file.
