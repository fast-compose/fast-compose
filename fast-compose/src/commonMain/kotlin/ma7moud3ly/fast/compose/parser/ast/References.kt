package ma7moud3ly.fast.compose.parser.ast

/**
 * Names this file uses but does not declare: identifiers, called functions and classes, callable references (`::name`)
 * and type names. Names declared anywhere in the file are left out: top-level declarations, members, parameters, local
 * variables and lambda parameters. For a qualified name such as `a.b.C` only `a` is a reference; member names after
 * a `.` are not. Annotations are not walked.
 */
public fun Program.externalNames(): Set<String> {
    val walker = ReferenceWalker()
    declarations.forEach { walker.stmt(it) }
    return walker.used - walker.declared
}

private class ReferenceWalker {
    val used = mutableSetOf<String>()
    val declared = mutableSetOf<String>()

    fun stmt(stmt: Stmt) {
        when (stmt) {
            is FunDecl -> {
                declared += stmt.name
                type(stmt.receiver)
                type(stmt.returnType)
                stmt.params.forEach(::param)
                stmt.body?.let(::block)
            }
            is ClassDecl -> {
                declared += stmt.name
                stmt.constructorParams.forEach { param(it.param) }
                stmt.superTypes.forEach(::type)
                stmt.enumEntries.forEach { entry ->
                    declared += entry.name
                    entry.args.forEach { expr(it.value) }
                }
                stmt.members.forEach(::stmt)
                stmt.initBlocks.forEach(::block)
            }
            is PropertyDecl -> {
                declared += stmt.name
                type(stmt.type)
                type(stmt.receiver)
                stmt.initializer?.let(::expr)
                stmt.delegate?.let(::expr)
                stmt.getter?.let(::block)
            }
            is ExprStmt -> expr(stmt.expr)
            is Assign -> {
                expr(stmt.target)
                expr(stmt.value)
            }
            is ForStmt -> {
                declared += stmt.variable
                stmt.destructured?.let { declared += it }
                expr(stmt.iterable)
                block(stmt.body)
            }
            is DestructuringDecl -> {
                declared += stmt.names
                expr(stmt.initializer)
            }
            is WhileStmt -> {
                expr(stmt.condition)
                block(stmt.body)
            }
            is ReturnStmt -> stmt.value?.let(::expr)
            is BreakStmt, is ContinueStmt -> Unit
        }
    }

    fun block(block: Block) = block.statements.forEach(::stmt)

    fun param(param: Param) {
        declared += param.name
        type(param.type)
        param.default?.let(::expr)
    }

    /** First segment of each name in a type, e.g. `Map` and `User` in `Map<String, User?>`. */
    fun type(type: TypeRef?) {
        if (type == null) return
        TYPE_NAME.findAll(type.text).forEach { used += it.groupValues[1] }
    }

    fun expr(e: Expr) {
        when (e) {
            is Ident -> used += e.name
            is Member -> expr(e.receiver)
            is CallableRef -> if (e.receiver == null) used += e.name else expr(e.receiver)
            is Call -> {
                expr(e.callee)
                e.args.forEach { expr(it.value) }
                e.trailingLambda?.let(::expr)
            }
            is Index -> {
                expr(e.receiver)
                e.indices.forEach(::expr)
            }
            is Lambda -> {
                declared += e.params
                e.destructured.values.forEach { declared += it }
                block(e.body)
            }
            is TryExpr -> {
                block(e.body)
                e.catches.forEach { catch ->
                    declared += catch.name
                    type(catch.type)
                    block(catch.body)
                }
                e.finallyBlock?.let(::block)
            }
            is ThrowExpr -> expr(e.value)
            is Binary -> {
                expr(e.left)
                expr(e.right)
            }
            is Unary -> expr(e.operand)
            is TypeCheck -> {
                expr(e.expr)
                type(e.type)
            }
            is Cast -> {
                expr(e.expr)
                type(e.type)
            }
            is IfExpr -> {
                expr(e.condition)
                block(e.then)
                e.otherwise?.let(::block)
            }
            is WhenExpr -> {
                e.subject?.let(::expr)
                e.branches.forEach { branch ->
                    branch.conditions.forEach { condition ->
                        when (condition) {
                            is WhenCondition.Value -> expr(condition.expr)
                            is WhenCondition.Is -> type(condition.type)
                            is WhenCondition.In -> expr(condition.expr)
                        }
                    }
                    block(branch.body)
                }
            }
            is StringTemplate -> e.parts.forEach { if (it is StringPart.Template) expr(it.expr) }
            is ReturnStmt -> e.value?.let(::expr)
            is BreakStmt, is ContinueStmt, is IntLit, is LongLit, is FloatLit, is DoubleLit, is CharLit, is BoolLit,
            is NullLit, is ThisExpr, is ErrorExpr -> Unit
        }
    }

    private companion object {
        /** A possibly qualified name; group 1 is its first segment. */
        val TYPE_NAME = Regex("""([A-Za-z_]\w*)(?:\.[A-Za-z_]\w*)*""")
    }
}
