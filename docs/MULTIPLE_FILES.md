# Multiple files

By default a preview runs one file. Imports are ignored, so the file must declare everything it uses besides bindings.
Imports of members, such as `import androidx.compose.ui.tooling.preview.Devices.DESKTOP`, still work for bound
objects.

With a `SourceProvider`, a file can use functions, classes, objects and properties from other files of a project:

```kotlin
val code = rememberComposeCode(source, path = "ui/ProfileScreen.kt", sources = provider)
FastComposePreview(code)
```

## SourceProvider

```kotlin
fun interface SourceProvider {
    suspend fun find(packageName: String, name: String): SourceFile?
    suspend fun isProjectPackage(packageName: String): Boolean = true
    suspend fun findExpect(packageName: String, name: String): SourceFile? = null
    suspend fun findLibrary(packageName: String, name: String): SourceFile? = null
}
```

- `find` returns the file in `packageName` that declares the top-level `name`, or null. Hosts implement it, e.g. an
  IntelliJ plugin with its symbol index, or a VS Code extension by searching the workspace.
- `isProjectPackage` tells project packages from library packages, e.g. from the project's source folders. Names
  imported from library packages are never looked up and never reported as missing. By default every package counts
  as the project's, except the libraries in `SourceLoader.DEFAULT_LIBRARY_PACKAGES` (`androidx`, `kotlin`,
  `kotlinx`, `java`, `org.jetbrains.compose`, Coil, …).
- `findExpect` returns the file with the `expect` declaration of `name` when `find` gave its `actual`, so calls get
  the default arguments written on the `expect`.
- `findLibrary` returns the file declaring `name` in a library package without bindings, e.g. from the library's
  sources jar. A miss is not reported; the name runs as library code or a stand-in.

## Project folders

Hosts that read a project from its folders, such as a CLI, don't need to write a `SourceProvider`:

```kotlin
val sources = KotlinSources(DiskFiles) // keep one per project: it caches parsed files
val provider = FolderSources(sources, DiskFiles.pathOf(file))
val code = rememberComposeCode(source, path = DiskFiles.pathOf(file), sources = provider)
```

- `ProjectFiles` is the file access: `list`, `isFolder`, `read` and `version`, by `/`-separated paths. `DiskFiles`
  reads the disk on the JVM and Android; an IDE can implement it over its own file tree, with unsaved editor text.
- `KotlinSources` finds the file in a package that declares a top-level name, under source roots given in order,
  or under the previewed file's own source root (found from its package) when no root has the package. Private
  declarations are skipped, a declaration with a body wins over one without, and an `expect` is replaced by its
  `actual` from the module's other source sets: `jvmMain`/`desktopMain` first, then `androidMain`, then the others.
  The file of a replaced `expect` is served with it blanked out, keeping lines. Each file is parsed once per version.
- `FolderSources(sources, from, roots, libraryRoots)` is the `SourceProvider`: by default its roots are the file's own
  source root and its module's `commonMain`/`main` Kotlin roots, and packages with a folder there are the project's.
  `libraryRoots`, e.g. extracted sources jars, serve library classes and objects.
- `GradleLayout` holds the layout rules: `sourceSetOf(path)`, `isPreviewRoot(path)` (generated sources count, except
  Compose resource accessors), `platformRank(sourceSet)` and `resourceFolders` (see [RESOURCES.md](RESOURCES.md)).

## Loading

After each parse, `SourceLoader` follows every declaration of the previewed file, and in other files only the
declarations it reaches. Each name a declaration uses but does not declare is looked up:

1. the same file
2. an explicit import (or its alias)
3. the file's own package
4. star imports

Found files are parsed and searched the same way, until no new declaration is reached.

- **Members after a `.`:** extension functions and properties such as `card` in `Modifier.card()` are looked up
  in explicit imports, the file's own package and star imports. A name that no file declares is a member and is not
  reported.
- **Member imports:** `import app.Settings.isDark` or `import app.Mode.DARK` is looked up through the class or
  object that declares the member, by Kotlin's naming conventions (the first capitalized segment).
- **Resources:** `Res` and `R` are never looked up; the resource bindings provide them, whatever their package.
- **Libraries:** names from library packages come from bindings, or on the JVM from the library's jars (see
  [Library code on the JVM](LIBRARY.md#library-code-on-the-jvm)).
- **Caching:** files are cached by path and text. A file with parse errors keeps its last version without errors.
- **Reloading:** `ComposeCode.reloadSources()` loads the files again, e.g. after another file changed.
- `ComposeCode.dependencies` holds the loaded files.

## Running

Each file sees declarations in Kotlin's order: its own, then explicit imports, then its package, then star imports.
Private declarations stay in their file, and code always runs with the declarations of the file it is written in.
Only the previewed file's `@Preview` functions are shown.

## Errors

- Names imported from project packages that no file declares are logged as warnings, only when a reached
  declaration uses them. Warnings for other files carry their path in `LogLine.file`.
- Parse errors and runtime errors in other files are logged with their file, e.g. `ProfileCard.kt:12: …`.
  `LogLine.file` and `RuntimeError.file` hold the path; they are null for the previewed file.
- A name imported from a package with no binding, and not found in library code, runs as a stand-in, with one
  warning naming its package:
  `'getTimeMillis' (io.ktor.util.date) has no binding; a stand-in is used, so the preview may differ`. Names that
  are not imported stay unresolved errors.

## Checking for previews

`KotlinParser.hasPreviews(source)` tells whether a file has a `@Preview` annotation without parsing it, using only
the lexer. Hosts can call it on every change to decide whether a file is worth previewing.
