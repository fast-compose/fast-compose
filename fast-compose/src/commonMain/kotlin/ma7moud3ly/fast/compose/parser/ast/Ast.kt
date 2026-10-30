package ma7moud3ly.fast.compose.parser.ast

import ma7moud3ly.fast.compose.parser.Span

/** Base of all syntax tree nodes. [id] is unique within a parse and unchanged across re-parses as long as no nodes are added or removed before it. */
public sealed interface Node {
    public val id: Int
    public val span: Span
}

/**
 * Root of a parsed file: its [declarations], the [packageName] and [imports] of its header, and the [path] it was
 * parsed from, if any.
 */
public data class Program(
    val declarations: List<Decl>,
    val packageName: String = "",
    val imports: List<Import> = emptyList(),
    val path: String? = null,
) {

    public val functions: List<FunDecl> get() = declarations.filterIsInstance<FunDecl>()

    /**
     * Every `@Preview` annotation of a `@Composable` function with its function, in source order; a function with
     * several `@Preview`s appears once per annotation.
     */
    public val previews: List<Pair<FunDecl, AnnotationRef>>
        get() = functions.filter { it.hasAnnotation("Composable") }
            .flatMap { fn -> fn.annotations.filter { it.name == "Preview" }.map { fn to it } }
}

/** `import a.b.C`, `import a.b.C as D` ([alias] is `D`) or `import a.b.*` ([isStar]; [path] is `a.b`). */
public data class Import(val path: String, val alias: String? = null, val isStar: Boolean = false) {
    /** Package of an imported declaration, or the imported package of a star import. */
    public val packageName: String get() = if (isStar) path else path.substringBeforeLast('.', "")

    /** Imported declaration name, e.g. `C`; null for star imports. */
    public val importedName: String? get() = if (isStar) null else path.substringAfterLast('.')

    /** Name the import makes visible in the file: the alias or the imported name; null for star imports. */
    public val visibleName: String? get() = alias ?: importedName
}

// ---------- Declarations ----------

/** Declarations can appear at top level, in class bodies, and as local statements. */
public sealed interface Decl : Stmt

/** Annotation usage; [name] is the last segment of a qualified name. */
public data class AnnotationRef(val name: String, val args: List<Arg>)

/** Type as written in source; the interpreter uses it only for hints and error messages. */
public data class TypeRef(val text: String)

public data class Param(val name: String, val type: TypeRef?, val default: Expr?, val isVararg: Boolean = false)

/** Function declaration; [body] is null for functions without a body (e.g. in interfaces). Expression bodies become a block with one [ReturnStmt]. */
public data class FunDecl(
    override val id: Int,
    override val span: Span,
    val name: String,
    val annotations: List<AnnotationRef>,
    val receiver: TypeRef?,
    val params: List<Param>,
    val returnType: TypeRef?,
    val body: Block?,
    val isSuspend: Boolean = false,
    val isPrivate: Boolean = false,
) : Decl {
    public fun hasAnnotation(name: String): Boolean = annotations.any { it.name == name }
}

public enum class ClassKind { CLASS, DATA_CLASS, OBJECT, ENUM, INTERFACE }

public data class ClassDecl(
    override val id: Int,
    override val span: Span,
    val name: String,
    val kind: ClassKind,
    val constructorParams: List<PropertyParam>,
    val superTypes: List<TypeRef>,
    val enumEntries: List<EnumEntry>,
    val members: List<Decl>,
    val initBlocks: List<Block>,
    val isPrivate: Boolean = false,
) : Decl

public data class EnumEntry(val name: String, val args: List<Arg>)

/** Constructor parameter; [isProperty] is true for `val`/`var` parameters. */
public data class PropertyParam(val param: Param, val isProperty: Boolean, val isVar: Boolean)

/** `val`/`var` at top level, in a class, or as a local statement. [getter] is a custom `get()` body. */
public data class PropertyDecl(
    override val id: Int,
    override val span: Span,
    val name: String,
    val isVar: Boolean,
    val type: TypeRef?,
    val initializer: Expr?,
    val delegate: Expr?,
    val receiver: TypeRef? = null,
    val getter: Block? = null,
    val isPrivate: Boolean = false,
) : Decl

// ---------- Statements ----------

public sealed interface Stmt : Node

public data class Block(override val id: Int, override val span: Span, val statements: List<Stmt>) : Node

public data class ExprStmt(override val id: Int, override val span: Span, val expr: Expr) : Stmt

/** Assignment; [op] is `=`, `+=`, `-=`, `*=`, `/=` or `%=`. */
public data class Assign(
    override val id: Int,
    override val span: Span,
    val target: Expr,
    val op: String,
    val value: Expr,
) : Stmt

/** `for (x in items)`, or `for ((a, b) in items)` with [destructured] names and an empty [variable]. */
public data class ForStmt(
    override val id: Int,
    override val span: Span,
    val variable: String,
    val iterable: Expr,
    val body: Block,
    val destructured: List<String>? = null,
) : Stmt

/** `val (a, b) = value`; `_` skips a component. */
public data class DestructuringDecl(
    override val id: Int,
    override val span: Span,
    val names: List<String>,
    val isVar: Boolean,
    val initializer: Expr,
) : Stmt

public data class WhileStmt(
    override val id: Int,
    override val span: Span,
    val condition: Expr,
    val body: Block,
    val isDoWhile: Boolean,
) : Stmt

/** `return`, optionally labeled (`return@forEach`). Also usable as an expression (`x ?: return`). */
public data class ReturnStmt(override val id: Int, override val span: Span, val value: Expr?, val label: String? = null) : Stmt, Expr

public data class BreakStmt(override val id: Int, override val span: Span) : Stmt, Expr

public data class ContinueStmt(override val id: Int, override val span: Span) : Stmt, Expr

// ---------- Expressions ----------

public sealed interface Expr : Node

public data class IntLit(override val id: Int, override val span: Span, val value: Int) : Expr
public data class LongLit(override val id: Int, override val span: Span, val value: Long) : Expr
public data class FloatLit(override val id: Int, override val span: Span, val value: Float) : Expr
public data class DoubleLit(override val id: Int, override val span: Span, val value: Double) : Expr
public data class CharLit(override val id: Int, override val span: Span, val value: Char) : Expr
public data class BoolLit(override val id: Int, override val span: Span, val value: Boolean) : Expr
public data class NullLit(override val id: Int, override val span: Span) : Expr
public data class ThisExpr(override val id: Int, override val span: Span) : Expr

/** Placeholder for input the lexer already reported as invalid. */
public data class ErrorExpr(override val id: Int, override val span: Span) : Expr

/** String literal; each part is either literal text or an embedded expression. */
public data class StringTemplate(override val id: Int, override val span: Span, val parts: List<StringPart>) : Expr

public sealed interface StringPart {
    public data class Text(val value: String) : StringPart
    public data class Template(val expr: Expr) : StringPart
}

public data class Ident(override val id: Int, override val span: Span, val name: String) : Expr

/** `receiver.name` or `receiver?.name`. */
public data class Member(
    override val id: Int,
    override val span: Span,
    val receiver: Expr,
    val name: String,
    val isSafe: Boolean,
) : Expr

/** `::name`, `receiver::name`, or `Type::class` (name is `class`). */
public data class CallableRef(override val id: Int, override val span: Span, val receiver: Expr?, val name: String) : Expr

public data class Arg(val name: String?, val value: Expr, val isSpread: Boolean = false)

/** Call with optional trailing lambda: `callee(args) { … }`. Infix calls (`a to b`) become `Call(Member(a, "to"), [b])`. */
public data class Call(
    override val id: Int,
    override val span: Span,
    val callee: Expr,
    val args: List<Arg>,
    val trailingLambda: Lambda?,
) : Expr

public data class Index(override val id: Int, override val span: Span, val receiver: Expr, val indices: List<Expr>) : Expr

/** Lambda; a destructured parameter `(a, b)` is named `_` in [params] and its names are in [destructured] by index. */
public data class Lambda(
    override val id: Int,
    override val span: Span,
    val params: List<String>,
    val body: Block,
    val destructured: Map<Int, List<String>> = emptyMap(),
) : Expr

/** `try { } catch (e: Type) { } finally { }`; usable as an expression. */
public data class TryExpr(
    override val id: Int,
    override val span: Span,
    val body: Block,
    val catches: List<CatchClause>,
    val finallyBlock: Block?,
) : Expr

public data class CatchClause(val name: String, val type: TypeRef, val body: Block)

/** `throw value`; usable as an expression, e.g. `x ?: throw IllegalStateException()`. */
public data class ThrowExpr(override val id: Int, override val span: Span, val value: Expr) : Expr

/** Binary operator; [op] is the operator text, e.g. `+`, `==`, `&&`, `..`, `in`, `!in`, `?:`. */
public data class Binary(override val id: Int, override val span: Span, val op: String, val left: Expr, val right: Expr) : Expr

/** Unary operator; [op] is `-`, `+`, `!`, `++`, `--` or `!!`. */
public data class Unary(
    override val id: Int,
    override val span: Span,
    val op: String,
    val operand: Expr,
    val isPrefix: Boolean,
) : Expr

/** `expr is Type` / `expr !is Type`. */
public data class TypeCheck(
    override val id: Int,
    override val span: Span,
    val expr: Expr,
    val type: TypeRef,
    val isNegated: Boolean,
) : Expr

/** `expr as Type` / `expr as? Type`. */
public data class Cast(override val id: Int, override val span: Span, val expr: Expr, val type: TypeRef, val isSafe: Boolean) : Expr

/** `if`; single-statement branches are wrapped in a [Block]. */
public data class IfExpr(
    override val id: Int,
    override val span: Span,
    val condition: Expr,
    val then: Block,
    val otherwise: Block?,
) : Expr

public data class WhenExpr(
    override val id: Int,
    override val span: Span,
    val subject: Expr?,
    val branches: List<WhenBranch>,
) : Expr

/** A `when` branch; empty [conditions] means `else`. */
public data class WhenBranch(val conditions: List<WhenCondition>, val body: Block)

public sealed interface WhenCondition {
    /** Matches when equal to the subject, or when true if there is no subject. */
    public data class Value(val expr: Expr) : WhenCondition
    public data class Is(val type: TypeRef, val isNegated: Boolean) : WhenCondition
    public data class In(val expr: Expr, val isNegated: Boolean) : WhenCondition
}
