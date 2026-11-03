package ma7moud3ly.fast.compose.project

import ma7moud3ly.fast.compose.interpreter.SourceFile
import ma7moud3ly.fast.compose.parser.Diagnostic
import ma7moud3ly.fast.compose.parser.KotlinParser
import ma7moud3ly.fast.compose.parser.ast.ClassDecl
import ma7moud3ly.fast.compose.parser.ast.FunDecl
import ma7moud3ly.fast.compose.parser.ast.PropertyDecl
import kotlin.concurrent.Volatile

/**
 * A parsed Kotlin file of a project: its [packageName], non-private top-level [declarations] by name and [text], valid
 * for [version] of [ProjectFiles].
 */
public class KotlinFile internal constructor(
    public val path: String,
    public val version: Long,
    public val packageName: String,
    public val declarations: Map<String, KotlinDeclaration>,
    public val text: String,
    /** Text ranges of its `expect` declarations, by name. */
    internal val expects: Map<String, List<IntRange>>,
)

/**
 * A top-level declaration: [hasBody] is false for functions without a body and for `expect` declarations
 * ([isExpect]); [isFunction] for functions.
 */
public class KotlinDeclaration(public val hasBody: Boolean, public val isExpect: Boolean, public val isFunction: Boolean)

/**
 * Finds the Kotlin files of a project that declare a top-level name, for multi-file previews. Each file is parsed once
 * and cached until its [ProjectFiles.version] changes.
 *
 * Lookups read a package's folder under source roots given in order, e.g. those of the previewed file's module and the
 * modules it depends on, or next to the previewed file ([sourceRootOf]) without them. Kotlin Multiplatform `expect`
 * declarations are replaced by their `actual` in the same module's other source sets, by
 * [GradleLayout.platformRank]; a file with such an `expect` is served with it blanked out (lines kept), so previews see
 * only the `actual`.
 *
 * Safe to call from several threads; concurrent parses of one file may both run.
 */
public class KotlinSources(private val files: ProjectFiles) {

    @Volatile
    private var cache: Map<String, KotlinFile> = emptyMap()

    /** The parsed file at [path], or null when it can't be read. */
    public fun file(path: String): KotlinFile? {
        val version = files.version(path)
        cache[path]?.takeIf { it.version == version }?.let { return it }
        val text = files.read(path) ?: return null
        return parse(path, version, text).also { cache = cache + (path to it) }
    }

    /** True when [path] was parsed for a lookup, so its changes may affect previews. */
    public fun isKnown(path: String): Boolean = path in cache

    /** Drops [path] from the cache, e.g. after it was moved or deleted. */
    public fun forget(path: String) {
        cache = cache - path
    }

    /** Drops every parsed file. */
    public fun clear() {
        cache = emptyMap()
    }

    /**
     * The file in [packageName] under [roots] that declares the top-level [name], preferring one with a body (an
     * `actual fun` over its `expect fun`), or the `actual` of an `expect`. [from], the previewed file, is skipped;
     * when no root has the package, [from]'s own source root is searched.
     */
    public fun find(packageName: String, name: String, roots: List<String>, from: String? = null): SourceFile? {
        val matches = packageFiles(packageName, roots, from).filter { name in it.declarations }
        val match = matches.firstOrNull { it.declarations.getValue(name).hasBody } ?: matches.firstOrNull() ?: return null
        if (match.declarations.getValue(name).isExpect) actualOf(match.path, packageName, name)?.let { return serve(it) }
        return serve(match)
    }

    /** The file in [packageName] under [roots] with the `expect` declaration of [name]; see [find]. */
    public fun findExpect(packageName: String, name: String, roots: List<String>, from: String? = null): SourceFile? {
        val match = packageFiles(packageName, roots, from).firstOrNull { it.declarations[name]?.isExpect == true } ?: return null
        return serve(match)
    }

    /**
     * The file declaring [name] in library package [packageName] under [roots], e.g. a library's sources jar. Only
     * classes, objects and values are served; functions and composables need the library's own code at runtime.
     */
    public fun findLibrary(packageName: String, name: String, roots: List<String>): SourceFile? {
        val match = packageFiles(packageName, roots, from = null)
            .firstOrNull { file -> file.declarations[name]?.let { !it.isFunction } == true } ?: return null
        return SourceFile(match.path, match.text)
    }

    /** True when [packageName] has a folder under one of [roots]. */
    public fun hasPackage(packageName: String, roots: List<String>): Boolean =
        roots.any { files.isFolder(packageFolder(it, packageName)) }

    /**
     * The source root of the file at [path], found from its package and folders: `app/src/main/kotlin` for
     * `app/src/main/kotlin/app/ui/Card.kt` in package `app.ui`; null when the folders don't match the package.
     */
    public fun sourceRootOf(path: String): String? {
        val ownPackage = file(path)?.packageName ?: return null
        var root = parentOf(path) ?: return null
        if (ownPackage.isNotEmpty()) {
            for (segment in ownPackage.split('.').reversed()) {
                if (nameOf(root) != segment) return null
                root = parentOf(root) ?: return null
            }
        }
        return root
    }

    /**
     * Source roots for the file at [path] by folder layout alone, for hosts without a project model: its own root,
     * then the Kotlin roots of its module's shared source sets ([GradleLayout.SHARED_SOURCE_SETS]).
     */
    public fun folderRoots(path: String): List<String> {
        val src = GradleLayout.sourceSetOf(path)?.let(::parentOf)
        val shared = src?.let { GradleLayout.sharedSourceSets(files, it) }.orEmpty()
            .flatMap { sourceSet -> GradleLayout.SOURCE_ROOTS.map { "$sourceSet/$it" } }
            .filter(files::isFolder)
        return (listOfNotNull(sourceRootOf(path)) + shared).distinct()
    }

    /**
     * Parsed Kotlin files of [packageName] under [roots] in root order, or under [from]'s own root when no root has the
     * package.
     */
    private fun packageFiles(packageName: String, roots: List<String>, from: String?): List<KotlinFile> {
        val folders = roots.map { packageFolder(it, packageName) }.distinct().filter(files::isFolder)
            .ifEmpty { listOfNotNull(from?.let(::sourceRootOf)?.let { packageFolder(it, packageName) }) }
        return folders
            .flatMap { kotlinFilesIn(it) }
            .filter { it != from }
            .mapNotNull(::file)
            .filter { it.packageName == packageName }
    }

    private fun kotlinFilesIn(folder: String): List<String> =
        files.list(folder).filter { !it.isFolder && it.name.endsWith(".kt") && it.size <= MAX_FILE_BYTES }.map { childOf(folder, it.name) }

    /**
     * The `actual` of [name] in [packageName] for an `expect` declared in [expectPath]: searched in the other source
     * sets of its module (`src/<sourceSet>/kotlin` or `java`), in [GradleLayout.platformRank] order.
     */
    private fun actualOf(expectPath: String, packageName: String, name: String): KotlinFile? {
        val ownSourceSet = GradleLayout.sourceSetOf(expectPath) ?: return null
        val src = parentOf(ownSourceSet) ?: return null
        val sourceSets = files.list(src)
            .filter { it.isFolder && it.name.endsWith("Main") && "$src/${it.name}" != ownSourceSet }
            .sortedWith(compareBy({ GradleLayout.platformRank(it.name) }, { it.name }))
        for (sourceSet in sourceSets) {
            for (rootName in GradleLayout.SOURCE_ROOTS) {
                val folder = packageFolder("$src/${sourceSet.name}/$rootName", packageName)
                for (path in kotlinFilesIn(folder)) {
                    val file = file(path) ?: continue
                    val declaration = file.declarations[name] ?: continue
                    if (file.packageName == packageName && !declaration.isExpect) return file
                }
            }
        }
        return null
    }

    /**
     * [file] as previews get it: `expect` declarations whose `actual` is found are blanked out, keeping line and column
     * positions, so only the `actual` is visible.
     */
    private fun serve(file: KotlinFile): SourceFile {
        val replaced = file.expects.filterKeys { actualOf(file.path, file.packageName, it) != null }.values.flatten()
        if (replaced.isEmpty()) return SourceFile(file.path, file.text)
        val chars = file.text.toCharArray()
        for (range in replaced) {
            for (i in range) if (chars[i] != '\n' && chars[i] != '\r') chars[i] = ' '
        }
        return SourceFile(file.path, chars.concatToString())
    }

    private fun parse(path: String, version: Long, text: String): KotlinFile {
        val result = KotlinParser.parse(text, path)
        val program = result.program
        // After a parse error the parser recovers, and may read a class's remaining members as top-level declarations.
        // Indented declarations after the first error are such members and are skipped, so their names don't pull this
        // file into previews; real top-level declarations start at column 1 and are kept.
        val firstError = result.diagnostics.filter { it.severity == Diagnostic.Severity.ERROR }.minOfOrNull { it.span.start }
        val declarations = HashMap<String, KotlinDeclaration>()
        val expects = HashMap<String, MutableList<IntRange>>()
        for (declaration in program.declarations) {
            if (firstError != null && declaration.span.start > firstError && declaration.span.column > 1) continue
            val (name, hasBody, isPrivate) = when (declaration) {
                is FunDecl -> Triple(declaration.name, declaration.body != null, declaration.isPrivate)
                is ClassDecl -> Triple(declaration.name, true, declaration.isPrivate)
                is PropertyDecl -> Triple(declaration.name, true, declaration.isPrivate)
            }
            val range = declaration.span.start until declaration.span.end.coerceAtMost(text.length)
            val isExpect = isExpect(text, range)
            if (isExpect) expects.getOrPut(name) { mutableListOf() } += range
            // Private declarations are not visible to other files. Overloads count as having a body if any has one.
            if (!isPrivate && declarations[name]?.hasBody != true) {
                declarations[name] = KotlinDeclaration(hasBody && !isExpect, isExpect, declaration is FunDecl)
            }
        }
        return KotlinFile(path, version, program.packageName, declarations, text, expects)
    }

    /** True when the declaration at [range] of [text] has the `expect` modifier, before its parameters or body. */
    private fun isExpect(text: String, range: IntRange): Boolean {
        if (range.isEmpty()) return false
        val header = text.substring(range.first, range.last + 1).takeWhile { it != '(' && it != '{' && it != '=' }
        return EXPECT.containsMatchIn(header)
    }

    private companion object {
        /** Larger files are skipped. */
        const val MAX_FILE_BYTES = 2L * 1024 * 1024
        val EXPECT = Regex("""\bexpect\b""")

        fun packageFolder(root: String, packageName: String): String =
            if (packageName.isEmpty()) root else childOf(root, packageName.replace('.', '/'))
    }
}
