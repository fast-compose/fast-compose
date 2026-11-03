package ma7moud3ly.fast.compose.interpreter

import ma7moud3ly.fast.compose.parser.Diagnostic
import ma7moud3ly.fast.compose.parser.KotlinParser
import ma7moud3ly.fast.compose.parser.ast.ClassDecl
import ma7moud3ly.fast.compose.parser.ast.Decl
import ma7moud3ly.fast.compose.parser.ast.FunDecl
import ma7moud3ly.fast.compose.parser.ast.Program
import ma7moud3ly.fast.compose.parser.ast.PropertyDecl
import ma7moud3ly.fast.compose.parser.ast.externalNames
import ma7moud3ly.fast.compose.parser.ast.memberNames

/** A Kotlin source file of a project. */
public data class SourceFile(val path: String, val text: String)

/** Finds the files of a project for previews that use declarations from other files, e.g. in an IDE plugin. */
public fun interface SourceProvider {
    /** The file in package [packageName] that declares the top-level [name], or null if the project has none. */
    public suspend fun find(packageName: String, name: String): SourceFile?

    /**
     * True if [packageName] belongs to the project, false for library packages. Names imported from library packages
     * are never looked up with [find] and never reported as missing: they come from bindings, or from [findLibrary].
     * By default every package counts as the project's, except [SourceLoader.DEFAULT_LIBRARY_PACKAGES].
     */
    public suspend fun isProjectPackage(packageName: String): Boolean = true

    /**
     * The file in package [packageName] with the `expect` declaration of [name], when [find] gave its `actual`; null
     * if unknown. Default values of an `expect fun` are written only on the `expect`, so calls that rely on them need
     * it.
     */
    public suspend fun findExpect(packageName: String, name: String): SourceFile? = null

    /**
     * The file declaring [name] in library package [packageName], e.g. from the library's sources jar, or null.
     * Only for libraries without bindings (not [SourceLoader.DEFAULT_LIBRARY_PACKAGES]); a missing one is not
     * reported and runs as a stand-in. Hosts should serve data declarations (classes, objects, values) rather than
     * functions that need the library at runtime.
     */
    public suspend fun findLibrary(packageName: String, name: String): SourceFile? = null
}

/** A name imported from a project package that no file declares; [file] is the path of the file that imports it. */
public data class MissingReference(val name: String, val packageName: String, val file: String?) {
    override fun toString(): String = "Unresolved reference '$name' (imported from $packageName)"
}

/**
 * Files loaded for a previewed file: [dependencies] are the files it reaches, [missing] are names imported from
 * project packages that no file declares, and [diagnostics] holds the parse errors of dependencies by path.
 */
public class LoadedSources(
    public val dependencies: List<Program>,
    public val missing: List<MissingReference>,
    public val diagnostics: Map<String, List<Diagnostic>>,
)

/**
 * Loads the files a previewed file depends on, for [Interpreter]'s `dependencies`.
 *
 * Every declaration of the previewed file is followed, and in other files only the declarations it reaches. For each,
 * the names it uses but does not declare are looked up as Kotlin does: the same file first, then an explicit import,
 * then the file's own package, then star imports. Members used after a `.` are looked up too when they are imported
 * explicitly, e.g. an extension function `card` in `Modifier.card()`. An import of a member, such as
 * `import a.b.Settings.isDark`, is looked up through the class or object that declares it.
 *
 * Never looked up: names from library packages ([libraryPackages], or packages [SourceProvider.isProjectPackage]
 * rejects), which come from bindings, and the generated resource classes `Res` and `R`, which the resource bindings
 * provide whatever their package. Names imported from the project that no file declares are reported in
 * [LoadedSources.missing], only when a reached declaration uses them.
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
    private val projectPackages = mutableMapOf<String, Boolean>()

    public suspend fun load(main: Program): LoadedSources {
        val found = LinkedHashMap<String, Program>()
        val missing = LinkedHashSet<MissingReference>()
        val diagnostics = mutableMapOf<String, List<Diagnostic>>()
        val lookups = mutableMapOf<Pair<String, String>, SourceFile?>()
        val reached = mutableSetOf<Pair<String?, Int>>()
        val queue = ArrayDeque<Pair<Program, Decl>>()

        fun reach(program: Program, decls: List<Decl>) {
            for (decl in decls) if (reached.add(program.path to decl.id)) queue += program to decl
        }

        reach(main, main.declarations)
        while (queue.isNotEmpty()) {
            val (program, decl) = queue.removeFirst()
            if (decl is FunDecl && decl.isActual) {
                // The defaults of an actual function's parameters are on its expect declaration.
                val file = provider.findExpect(program.packageName, decl.name)
                if (file != null && file.path != program.path && file.path != main.path) {
                    val expect = found[file.path] ?: run {
                        val (fileProgram, errors) = parse(file)
                        found[file.path] = fileProgram
                        if (errors.isNotEmpty()) diagnostics[file.path] = errors
                        fileProgram
                    }
                    reach(expect, expect.declarations.filter { it is FunDecl && it.isExpect && it.name == decl.name })
                }
            }
            for (name in namesToFind(program, decl)) {
                val own = program.declarations.filter { nameOf(it) == name }
                if (own.isNotEmpty()) {
                    reach(program, own)
                    continue
                }
                if (name in RESOURCE_CLASSES) continue
                for (candidate in candidates(program, name)) {
                    val key = candidate.packageName to candidate.name
                    val file = if (key in lookups) {
                        lookups[key]
                    } else if (candidate.isLibrary) {
                        provider.findLibrary(candidate.packageName, candidate.name).also { lookups[key] = it }
                    } else {
                        provider.find(candidate.packageName, candidate.name).also { lookups[key] = it }
                    }
                    if (file == null || file.path == program.path) {
                        if (candidate.isImport && !candidate.isLibrary) missing += MissingReference(name, candidate.packageName, program.path)
                        continue
                    }
                    val dependency = if (file.path == main.path) main else found[file.path] ?: run {
                        val (fileProgram, errors) = parse(file)
                        found[file.path] = fileProgram
                        if (errors.isNotEmpty()) diagnostics[file.path] = errors
                        fileProgram
                    }
                    reach(dependency, dependency.declarations.filter { nameOf(it) == candidate.name })
                    break
                }
            }
        }
        return LoadedSources(found.values.toList(), missing.toList(), diagnostics)
    }

    /**
     * Names [decl] uses but does not declare, plus names it uses after a `.`: these may be extensions declared in another
     * file of the package or imported (`background.toColor()` with `fun Long.toColor()` in `Colors.kt`). A name after a
     * `.` that no file declares is a member and is never reported missing.
     */
    private fun namesToFind(program: Program, decl: Decl): List<String> =
        (decl.externalNames() + decl.memberNames()).sorted()

    /** A place [name] may be declared; [isLibrary] for a library without bindings, looked up with `findLibrary`. */
    private class Candidate(val packageName: String, val name: String, val isImport: Boolean, val isLibrary: Boolean = false)

    /** Where [name] may be declared, in Kotlin's lookup order; empty for names from library packages. */
    private suspend fun candidates(program: Program, name: String): List<Candidate> {
        val import = program.imports.firstOrNull { !it.isStar && it.visibleName == name }
        if (import != null) {
            val packageName = import.declarationPackage
            if (isBound(packageName)) return emptyList()
            return listOf(Candidate(packageName, import.declarationName, true, isLibrary = isLibrary(packageName)))
        }
        return buildList {
            add(Candidate(program.packageName, name, false))
            for (star in program.imports.filter { it.isStar }) {
                if (!isLibrary(star.path)) add(Candidate(star.path, name, false))
            }
        }
    }

    /** True for packages whose APIs come from bindings, never from files: [libraryPackages]. */
    private fun isBound(packageName: String): Boolean =
        libraryPackages.any { packageName == it || packageName.startsWith("$it.") }

    private suspend fun isLibrary(packageName: String): Boolean {
        if (isBound(packageName)) return true
        val isProject = projectPackages[packageName]
            ?: provider.isProjectPackage(packageName).also { projectPackages[packageName] = it }
        return !isProject
    }

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

        /** Resource classes generated by the build (`Res` of Compose Multiplatform, Android's `R`). */
        private val RESOURCE_CLASSES = setOf("Res", "R")

        private fun nameOf(decl: Decl): String = when (decl) {
            is FunDecl -> decl.name
            is ClassDecl -> decl.name
            is PropertyDecl -> decl.name
        }
    }
}
