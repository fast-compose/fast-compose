package ma7moud3ly.fast.compose.interpreter

import ma7moud3ly.fast.compose.parser.ast.ClassDecl
import ma7moud3ly.fast.compose.parser.ast.Decl
import ma7moud3ly.fast.compose.parser.ast.FunDecl
import ma7moud3ly.fast.compose.parser.ast.Program
import ma7moud3ly.fast.compose.parser.ast.PropertyDecl

/**
 * One file of a running project: its top-level state and the top-level declarations visible in it.
 *
 * Visible declarations follow Kotlin's order, each level hiding the ones before it: star imports, then the file's
 * package, then explicit imports (under their alias, if any), then the file's own declarations. Private declarations
 * of other files are not visible. With a single file only its own declarations are visible and imports are ignored.
 */
internal class FileScope(val program: Program) {

    /** Root of the variable scopes of code declared in this file. */
    val root = Env(null, scope = this)

    var functions: Map<String, List<FunDecl>> = emptyMap()
        private set
    var classes: Map<String, ClassDecl> = emptyMap()
        private set
    var properties: Map<String, PropertyDecl> = emptyMap()
        private set

    /** Values of top-level properties, by name. */
    val propertyValues = mutableMapOf<String, Any?>()

    /** Instances of objects declared in this file, by declaration id. */
    val objects = mutableMapOf<Int, ObjectValue>()

    /** Entries of enum classes declared in this file, by declaration id. */
    val enumValues = mutableMapOf<Int, List<EnumValue>>()

    /** Every declaration of the file by name, class members included, to find the file a declaration belongs to. */
    private val owned: Map<String, List<Decl>> = buildMap<String, MutableList<Decl>> {
        fun add(decls: List<Decl>) {
            for (decl in decls) {
                getOrPut(nameOf(decl)) { mutableListOf() } += decl
                if (decl is ClassDecl) add(decl.members)
            }
        }
        add(program.declarations)
    }

    fun owns(decl: Decl): Boolean = owned[nameOf(decl)]?.any { it === decl } == true

    /** Fills the visible declarations from this file and the other files of the project, [all]. */
    fun link(all: List<FileScope>) {
        val others = all.filter { it !== this }
        val visible = Visible()
        for ((path) in program.imports.filter { it.isStar }) {
            others.filter { it.program.packageName == path }.forEach { visible.add(it.public()) }
        }
        visible.add(others.filter { it.program.packageName == program.packageName }.flatMap { it.public() })
        for (import in program.imports.filter { !it.isStar }) {
            val decls = others.filter { it.program.packageName == import.packageName }
                .flatMap { it.public() }.filter { nameOf(it) == import.importedName }
            visible.add(decls, import.visibleName)
        }
        visible.add(program.declarations)
        functions = visible.functions
        classes = visible.classes
        properties = visible.properties
    }

    private fun public(): List<Decl> = program.declarations.filter { !isPrivate(it) }

    /** Visible declarations; each [add] replaces names added before. */
    private class Visible {
        val functions = mutableMapOf<String, List<FunDecl>>()
        val classes = mutableMapOf<String, ClassDecl>()
        val properties = mutableMapOf<String, PropertyDecl>()

        fun add(decls: List<Decl>, alias: String? = null) {
            val level = decls.filterIsInstance<FunDecl>().groupBy { alias ?: it.name }
            functions.putAll(level)
            for (decl in decls) {
                when (decl) {
                    is ClassDecl -> classes[alias ?: decl.name] = decl
                    is PropertyDecl -> if (decl.receiver == null) properties[alias ?: decl.name] = decl
                    is FunDecl -> Unit
                }
            }
        }
    }

    private companion object {
        fun nameOf(decl: Decl): String = when (decl) {
            is FunDecl -> decl.name
            is ClassDecl -> decl.name
            is PropertyDecl -> decl.name
        }

        fun isPrivate(decl: Decl): Boolean = when (decl) {
            is FunDecl -> decl.isPrivate
            is ClassDecl -> decl.isPrivate
            is PropertyDecl -> decl.isPrivate
        }
    }
}
