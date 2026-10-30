package ma7moud3ly.fast.compose.interpreter

import ma7moud3ly.fast.compose.parser.Diagnostic
import ma7moud3ly.fast.compose.parser.KotlinParser
import ma7moud3ly.fast.compose.parser.ast.Program
import ma7moud3ly.fast.compose.parser.ast.externalNames

/** A Kotlin source file of a project. */
public data class SourceFile(val path: String, val text: String)

/** Finds the files of a project for previews that use declarations from other files, e.g. in an IDE plugin. */
public fun interface SourceProvider {
    /** The file in package [packageName] that declares the top-level [name], or null if the project has none. */
    public suspend fun find(packageName: String, name: String): SourceFile?
}

/**
 * Files loaded for a previewed file: [dependencies] are the files it reaches, [missing] describes names imported from
 * project packages that no file declares, and [diagnostics] holds the parse errors of dependencies by path.
 */
public class LoadedSources(
    public val dependencies: List<Program>,
    public val missing: List<String>,
    public val diagnostics: Map<String, List<Diagnostic>>,
)

/**
 * Loads the files a previewed file depends on, for [Interpreter]'s `dependencies`.
 *
 * Starting from the previewed file, every name it uses but does not declare is looked up as Kotlin does: an explicit
 * import first, then the file's own package, then star imports. Found files are parsed and searched the same way,
 * until no new file is found. Names from [libraryPackages] are never looked up; they come from bindings.
 *
 * Parsed files are cached by path and text, and a file with parse errors keeps its last version without errors.
 * Not thread-safe: call [load] from one coroutine at a time.
 */
public class SourceLoader(
    private val provider: SourceProvider,
    private val libraryPackages: List<String> = DEFAULT_LIBRARY_PACKAGES,
) {
    private class Parsed(val text: String, val program: Program, val diagnostics: List<Diagnostic>)

    private val parsed = mutableMapOf<String, Parsed>()
    private val lastGood = mutableMapOf<String, Program>()

    public suspend fun load(main: Program): LoadedSources {
        val found = LinkedHashMap<String, Program>()
        val missing = LinkedHashSet<String>()
        val diagnostics = mutableMapOf<String, List<Diagnostic>>()
        val lookups = mutableMapOf<Pair<String, String>, SourceFile?>()
        val queue = ArrayDeque(listOf(main))
        while (queue.isNotEmpty()) {
            val program = queue.removeFirst()
            for (name in program.externalNames().sorted()) {
                for (candidate in candidates(program, name)) {
                    val key = candidate.packageName to candidate.name
                    val file = if (key in lookups) lookups[key] else provider.find(candidate.packageName, candidate.name).also { lookups[key] = it }
                    if (file == null || file.path == program.path) {
                        if (candidate.isImport) missing += "Unresolved reference '$name' (imported from ${candidate.packageName})"
                        continue
                    }
                    if (file.path != main.path && file.path !in found) {
                        val (dependency, errors) = parse(file)
                        found[file.path] = dependency
                        if (errors.isNotEmpty()) diagnostics[file.path] = errors
                        queue += dependency
                    }
                    break
                }
            }
        }
        return LoadedSources(found.values.toList(), missing.toList(), diagnostics)
    }

    private class Candidate(val packageName: String, val name: String, val isImport: Boolean)

    /** Where [name] may be declared, in Kotlin's lookup order; empty for names from library packages. */
    private fun candidates(program: Program, name: String): List<Candidate> {
        val import = program.imports.firstOrNull { !it.isStar && it.visibleName == name }
        if (import != null) {
            return if (isLibrary(import.packageName)) emptyList() else listOf(Candidate(import.packageName, import.importedName!!, true))
        }
        return buildList {
            add(Candidate(program.packageName, name, false))
            program.imports.filter { it.isStar && !isLibrary(it.path) }.forEach { add(Candidate(it.path, name, false)) }
        }
    }

    private fun isLibrary(packageName: String) = libraryPackages.any { packageName == it || packageName.startsWith("$it.") }

    private fun parse(file: SourceFile): Pair<Program, List<Diagnostic>> {
        val cached = parsed[file.path]?.takeIf { it.text == file.text } ?: run {
            val result = KotlinParser.parse(file.text, file.path)
            Parsed(file.text, result.program, result.diagnostics).also { parsed[file.path] = it }
        }
        val errors = cached.diagnostics.filter { it.severity == Diagnostic.Severity.ERROR }
        if (errors.isEmpty()) lastGood[file.path] = cached.program
        return (if (errors.isEmpty()) cached.program else lastGood[file.path] ?: cached.program) to errors
    }

    public companion object {
        /** Packages of libraries, whose declarations come from bindings rather than project files. */
        public val DEFAULT_LIBRARY_PACKAGES: List<String> = listOf(
            "androidx", "android", "kotlin", "kotlinx", "java", "javax", "org.jetbrains.compose", "coil3", "coil",
        )
    }
}
