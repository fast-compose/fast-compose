package ma7moud3ly.fast.compose.parser

import ma7moud3ly.fast.compose.parser.TokenType.*
import ma7moud3ly.fast.compose.parser.ast.*

public data class ParseResult(val program: Program, val diagnostics: List<Diagnostic>) {
    public val hasErrors: Boolean get() = diagnostics.any { it.severity == Diagnostic.Severity.ERROR }
}

/**
 * Builds a [Program] from tokens using recursive descent, with Pratt parsing for expressions.
 * Never throws: errors are reported as diagnostics, and parsing resumes at the next statement or declaration.
 *
 * Newlines end statements inside braces and at top level, and are ignored inside parentheses, brackets and string templates.
 * A line starting with `.`, `?.`, `?:`, `&&` or `||` continues the previous expression.
 */
public class Parser(private val tokens: List<Token>) {

    private var pos = 0
    private var nextId = 0
    private var lastEnd = 0
    private val diagnostics = mutableListOf<Diagnostic>()

    /** Innermost last; true when newlines are significant. */
    private val newlineModes = ArrayDeque<Boolean>()

    private class ParseException : RuntimeException()

    public fun parse(path: String? = null): ParseResult {
        val declarations = mutableListOf<Decl>()
        var packageName = ""
        val imports = mutableListOf<Import>()
        while (true) {
            skipSeparators()
            if (at(EOF)) break
            try {
                if (isSoft("package")) {
                    advance()
                    packageName = readQualifiedLine().segments.joinToString(".")
                    continue
                }
                if (isSoft("import")) {
                    advance()
                    val line = readQualifiedLine()
                    imports += Import(line.segments.joinToString("."), line.alias, line.isStar)
                    continue
                }
                if (at(AT) && peekAfter().text == "file") {
                    parseAnnotation() // file annotations such as @file:OptIn(...)
                    continue
                }
                if (skipTypeAlias()) continue
                declarations += parseDeclarationOrNull() ?: throw error("Expected a declaration, found ${describe(cur())}")
            } catch (_: ParseException) {
                // Already reported as a diagnostic; parsing resumes at the next declaration.
                syncDeclaration()
            }
        }
        for (fn in declarations.filterIsInstance<FunDecl>()) {
            if (fn.hasAnnotation("Preview") && !fn.hasAnnotation("Composable")) {
                diagnostics += Diagnostic(Diagnostic.Severity.WARNING, "@Preview function '${fn.name}' must be @Composable", fn.span)
            }
        }
        return ParseResult(Program(declarations, packageName, imports, path), diagnostics.toList())
    }

    private class QualifiedLine(val segments: List<String>, val isStar: Boolean, val alias: String?)

    /** Reads the rest of a `package` or `import` line: `a.b.C`, `a.b.*` or `a.b.C as D`. */
    private fun readQualifiedLine(): QualifiedLine {
        val segments = mutableListOf<String>()
        var isStar = false
        var alias: String? = null
        while (tokens[pos].type != NEWLINE && tokens[pos].type != SEMICOLON && tokens[pos].type != EOF) {
            val t = advance()
            when {
                t.type == STAR -> isStar = true
                t.type == AS && tokens[pos].type == IDENTIFIER -> alias = advance().value
                t.type != DOT -> segments += t.value
            }
        }
        return QualifiedLine(segments, isStar, alias)
    }

    // ---------- Declarations ----------

    private class Modifiers(val annotations: List<AnnotationRef>, val words: Set<String>)

    private fun parseModifiers(): Modifiers {
        val annotations = mutableListOf<AnnotationRef>()
        val words = mutableSetOf<String>()
        while (true) {
            when {
                at(AT) -> {
                    annotations += parseAnnotation()
                    skipNewlines()
                }
                isModifierWord() -> words += advance().text
                else -> return Modifiers(annotations, words)
            }
        }
    }

    private fun isModifierWord(): Boolean {
        val t = cur()
        return t.type == IDENTIFIER && t.text in modifierWords &&
            peekAfter().type in setOf(IDENTIFIER, FUN, VAL, VAR, CLASS, OBJECT, AT)
    }

    private fun parseAnnotation(): AnnotationRef {
        expect(AT, "'@'")
        if (at(IDENTIFIER) && peekAfter().type == COLON) {
            advance() // use-site target, e.g. @file:
            advance()
        }
        var name = expect(IDENTIFIER, "an annotation name").value
        while (at(DOT) && peekAfter().type == IDENTIFIER) {
            advance()
            name = advance().value
        }
        // Arguments must touch the name: in `@Composable () -> Unit` the parentheses belong to the type.
        val args = if (tokens[pos].type == LPAREN && tokens[pos].start == lastEnd) parseCallArgs() else emptyList()
        return AnnotationRef(name, args)
    }

    /** Parses a declaration, or restores the position and returns null if none starts here. */
    private fun parseDeclarationOrNull(): Decl? {
        val start = cur()
        val saved = pos
        val modifiers = parseModifiers()
        return when {
            // `object : Type { … }` and `object { … }` are expressions, not declarations.
            at(OBJECT) && "companion" !in modifiers.words && peekAfter().type in setOf(COLON, LBRACE) -> {
                pos = saved
                null
            }
            at(FUN) -> parseFunction(start, modifiers)
            at(VAL) || at(VAR) -> parseProperty(start).copy(isPrivate = "private" in modifiers.words)
            at(CLASS) || at(OBJECT) || isSoft("interface") -> parseClass(start, modifiers)
            else -> {
                pos = saved
                null
            }
        }
    }

    private fun parseFunction(start: Token, modifiers: Modifiers): FunDecl {
        expect(FUN, "'fun'")
        skipTypeParameters()
        val (receiver, name) = parseReceiverAndName()
        val params = parseParams()
        val returnType = if (match(COLON)) parseType() else null
        skipWhereClause()
        if (nextNonNewline().type == LBRACE || nextNonNewline().type == ASSIGN) skipNewlines()
        val body = when {
            at(LBRACE) -> parseBlock()
            match(ASSIGN) -> expressionBody()
            else -> null
        }
        return FunDecl(
            id(), spanFrom(start), name, modifiers.annotations, receiver, params, returnType, body,
            isSuspend = "suspend" in modifiers.words,
            isPrivate = "private" in modifiers.words,
            isExpect = "expect" in modifiers.words,
            isActual = "actual" in modifiers.words,
        )
    }

    private fun expressionBody(): Block {
        skipNewlines()
        val expr = parseExpression()
        return Block(id(), expr.span, listOf(ReturnStmt(id(), expr.span, expr)))
    }

    private fun parseProperty(start: Token): PropertyDecl {
        val isVar = advance().type == VAR
        if (at(LPAREN)) throw error("Destructuring declarations are only supported inside functions")
        skipTypeParameters()
        val (receiver, name) = parseReceiverAndName()
        val type = if (match(COLON)) parseType() else null
        var initializer: Expr? = null
        var delegate: Expr? = null
        if (match(ASSIGN)) {
            skipNewlines()
            initializer = parseExpression()
        } else if (isSoft("by")) {
            advance()
            skipNewlines()
            delegate = parseExpression()
        }
        val getter = parseAccessors()
        return PropertyDecl(id(), spanFrom(start), name, isVar, type, initializer, delegate, receiver, getter)
    }

    /**
     * Parses optional `get()` / `set(value)` accessors, which may start with annotations and modifiers, e.g.
     * `@Composable get() = …` or `private set`. Returns the getter body; setters and accessors without a body are
     * parsed and dropped.
     */
    private fun parseAccessors(): Block? {
        var getter: Block? = null
        while (true) {
            val word = accessorAt(indexOfNextNonNewline()) ?: return getter
            skipNewlines()
            while (pos < word) {
                if (tokens[pos].type == AT) parseAnnotation() else advance()
                skipNewlines()
            }
            val isGetter = advance().text == "get"
            if (tokens[pos].type != LPAREN) continue
            parseParams()
            if (match(COLON)) parseType()
            val body = if (match(ASSIGN)) expressionBody() else parseBlock()
            if (isGetter) getter = body
        }
    }

    /**
     * Index of the `get` or `set` of an accessor starting at [start], after its annotations and modifiers, or null if
     * no accessor starts there. An accessor without parameters, such as `private set`, needs an annotation or a
     * modifier, so a plain `get` or `set` on the next line stays an expression.
     */
    private fun accessorAt(start: Int): Int? {
        var i = start
        while (true) {
            val t = tokens[i]
            i = when {
                t.type == NEWLINE -> i + 1
                t.type == AT -> annotationEnd(i) ?: return null
                t.type == IDENTIFIER && t.text in accessorModifiers -> i + 1
                else -> break
            }
        }
        val word = tokens[i]
        if (word.type != IDENTIFIER || (word.text != "get" && word.text != "set")) return null
        val next = tokens[i + 1]
        if (next.type == LPAREN) {
            val afterParens = parensEnd(i + 1) ?: return null
            return if (tokens[afterParens].type in setOf(ASSIGN, LBRACE, COLON)) i else null
        }
        val hasPrefix = (start until i).any { tokens[it].type != NEWLINE }
        return if (hasPrefix && next.type in setOf(NEWLINE, SEMICOLON, RBRACE, EOF)) i else null
    }

    /** Index right after the annotation starting with the `@` at [at], or null if none starts there. */
    private fun annotationEnd(at: Int): Int? {
        var i = at + 1
        if (tokens[i].type == IDENTIFIER && tokens[i + 1].type == COLON) i += 2 // use-site target
        if (tokens[i].type != IDENTIFIER) return null
        i++
        while (tokens[i].type == DOT && tokens[i + 1].type == IDENTIFIER) i += 2
        if (tokens[i].type == LPAREN && tokens[i].start == tokens[i - 1].end) return parensEnd(i)
        return i
    }

    /** Index right after the `)` that closes the `(` at [open], or null if it is never closed. */
    private fun parensEnd(open: Int): Int? {
        var depth = 0
        for (i in open until tokens.size) {
            when (tokens[i].type) {
                LPAREN -> depth++
                RPAREN -> if (--depth == 0) return i + 1
                EOF -> return null
                else -> Unit
            }
        }
        return null
    }

    /** Parses `Name` or `Receiver.Name` (receiver may have type arguments or `?`). */
    private fun parseReceiverAndName(): Pair<TypeRef?, String> {
        val from = pos
        var name = expect(IDENTIFIER, "a name")
        var receiverEnd = -1
        var nullable = false
        while (true) {
            if (at(LT)) skipTypeArguments()
            if (at(QUESTION) && peekAfter().type == DOT) advance()
            // `Type?.name`: the lexer reads `?.` as one token.
            if ((at(DOT) || at(SAFE_DOT)) && peekAfter().type == IDENTIFIER) {
                receiverEnd = pos
                nullable = at(SAFE_DOT)
                advance()
                name = advance()
            } else {
                break
            }
        }
        val receiver = if (receiverEnd >= 0) TypeRef(textOf(from, receiverEnd) + if (nullable) "?" else "") else null
        return receiver to name.value
    }

    private fun parseParams(): List<Param> = inParens {
        expect(LPAREN, "'('")
        val params = mutableListOf<Param>()
        while (!at(RPAREN)) {
            val modifiers = parseModifiers()
            params += parseParam(isVararg = "vararg" in modifiers.words)
            if (!match(COMMA)) break
        }
        expect(RPAREN, "')'")
        params
    }

    private fun parseParam(isVararg: Boolean): Param {
        val name = expect(IDENTIFIER, "a parameter name").value
        val type = if (match(COLON)) parseType() else null
        val default = if (match(ASSIGN)) parseExpression() else null
        return Param(name, type, default, isVararg)
    }

    /** Parses a class, object or interface; with [anonymous], the class of an `object : … { … }` expression. */
    private fun parseClass(start: Token, modifiers: Modifiers, anonymous: Boolean = false): ClassDecl {
        val kind = when {
            anonymous -> ClassKind.CLASS
            at(OBJECT) -> ClassKind.OBJECT
            isSoft("interface") -> ClassKind.INTERFACE
            "enum" in modifiers.words -> ClassKind.ENUM
            "data" in modifiers.words -> ClassKind.DATA_CLASS
            else -> ClassKind.CLASS
        }
        advance()
        val name = when {
            at(IDENTIFIER) -> advance().value
            "companion" in modifiers.words -> "Companion"
            anonymous -> "object"
            else -> throw error("Expected a class name, found ${describe(cur())}")
        }
        skipTypeParameters()

        // `constructor`, with annotations or a visibility: `internal constructor(…)`.
        if (isSoft("constructor") || (at(AT) && kind != ClassKind.OBJECT) ||
            (cur().text in visibilityWords && peekAfter().text == "constructor")
        ) {
            parseModifiers()
            if (isSoft("constructor")) advance()
        }
        val constructorParams = if (at(LPAREN)) parseConstructorParams() else emptyList()

        val superTypes = mutableListOf<TypeRef>()
        if (match(COLON)) {
            do {
                skipNewlines()
                superTypes += parseType()
                if (at(LPAREN)) parseCallArgs()
                if (isSoft("by")) {
                    advance()
                    parseExpression()
                }
            } while (match(COMMA))
        }
        skipWhereClause()

        var entries = emptyList<EnumEntry>()
        val members = mutableListOf<Decl>()
        val initBlocks = mutableListOf<Block>()
        val secondaryConstructors = mutableListOf<SecondaryConstructor>()
        if (nextNonNewline().type == LBRACE) {
            skipNewlines()
            expect(LBRACE, "'{'")
            withNewlines(true) {
                if (kind == ClassKind.ENUM) entries = parseEnumEntries()
                while (true) {
                    skipSeparators()
                    if (at(RBRACE) || at(EOF)) break
                    try {
                        if (isSoft("init") && peekAfter().type == LBRACE) {
                            advance()
                            initBlocks += parseBlock()
                        } else if (startsSecondaryConstructor()) {
                            secondaryConstructors += parseSecondaryConstructor()
                        } else {
                            members += parseDeclarationOrNull() ?: throw error("Expected a member declaration, found ${describe(cur())}")
                        }
                    } catch (_: ParseException) {
                        // Already reported as a diagnostic; parsing resumes at the next statement.
                        syncStatement()
                    }
                }
                expect(RBRACE, "'}'")
            }
        }
        return ClassDecl(
            id(), spanFrom(start), name, kind, constructorParams, superTypes, entries, members, initBlocks,
            "private" in modifiers.words, secondaryConstructors,
        )
    }

    /** True when a secondary `constructor(…)` starts here, after its annotations and modifiers. */
    private fun startsSecondaryConstructor(): Boolean {
        val saved = pos
        parseModifiers()
        val starts = isSoft("constructor") && peekAfter().type == LPAREN
        pos = saved
        return starts
    }

    /** Parses `constructor(…) : this(…) { … }`; the delegation and the body are optional. */
    private fun parseSecondaryConstructor(): SecondaryConstructor {
        parseModifiers()
        advance() // constructor
        val params = parseParams()
        var delegation: List<Arg>? = null
        if (nextNonNewline().type == COLON) {
            skipNewlines()
            advance()
            skipNewlines()
            val target = advance()
            val args = parseCallArgs()
            if (target.type == THIS) delegation = args
        }
        val body = if (nextNonNewline().type == LBRACE) {
            skipNewlines()
            parseBlock()
        } else {
            null
        }
        return SecondaryConstructor(params, delegation, body)
    }

    /**
     * Skips a top-level `typealias Name<T> = Type`, with its annotations and modifiers, and returns true; returns
     * false, at the same position, when none starts here. Types are not checked, so aliases need nothing at runtime.
     */
    private fun skipTypeAlias(): Boolean {
        val saved = pos
        parseModifiers()
        if (!isSoft("typealias")) {
            pos = saved
            return false
        }
        advance()
        expect(IDENTIFIER, "a type alias name")
        skipTypeParameters()
        expect(ASSIGN, "'='")
        skipNewlines()
        parseType()
        return true
    }

    private fun parseConstructorParams(): List<PropertyParam> = inParens {
        expect(LPAREN, "'('")
        val params = mutableListOf<PropertyParam>()
        while (!at(RPAREN)) {
            val modifiers = parseModifiers()
            val isProperty = at(VAL) || at(VAR)
            val isVar = at(VAR)
            if (isProperty) advance()
            params += PropertyParam(parseParam(isVararg = "vararg" in modifiers.words), isProperty, isVar)
            if (!match(COMMA)) break
        }
        expect(RPAREN, "')'")
        params
    }

    private fun parseEnumEntries(): List<EnumEntry> {
        val entries = mutableListOf<EnumEntry>()
        try {
            skipSeparators()
            while (true) {
                // An entry may have annotations, e.g. `@SerialName("busy") Busy`; if no entry follows, they belong to
                // the next member and are left for it.
                val beforeAnnotations = pos
                while (at(AT)) {
                    parseAnnotation()
                    skipNewlines()
                }
                if (!at(IDENTIFIER) || peekAfter().type !in setOf(COMMA, SEMICOLON, LPAREN, RBRACE, NEWLINE)) {
                    pos = beforeAnnotations
                    break
                }
                val name = advance().value
                val args = if (at(LPAREN)) parseCallArgs() else emptyList()
                entries += EnumEntry(name, args)
                skipNewlines()
                if (!match(COMMA)) break
                skipNewlines()
            }
            match(SEMICOLON)
        } catch (_: ParseException) {
            // Already reported as a diagnostic; parsing resumes at the next statement.
            syncStatement()
        }
        return entries
    }

    // ---------- Types ----------

    private fun parseType(): TypeRef {
        cur()
        val from = pos
        skipType()
        return TypeRef(textOf(from, pos))
    }

    private fun skipType() {
        while (at(AT)) parseAnnotation()
        // `suspend () -> Unit`, and with a receiver, `suspend CoroutineScope.() -> Unit`.
        if (isSoft("suspend") && peekAfter().type in setOf(LPAREN, IDENTIFIER)) advance()
        if (at(LPAREN)) {
            inParens {
                advance()
                while (!at(RPAREN)) {
                    if (at(IDENTIFIER) && peekAfter().type == COLON) {
                        advance()
                        advance()
                    }
                    skipType()
                    if (!match(COMMA)) break
                }
                expect(RPAREN, "')'")
            }
            if (match(ARROW)) skipType()
        } else {
            if (match(STAR)) return
            expect(IDENTIFIER, "a type")
            skipTypeArgumentsInType()
            while (at(DOT) && peekAfter().type == IDENTIFIER) {
                advance()
                advance()
                skipTypeArgumentsInType()
            }
            if (at(DOT) && peekAfter().type == LPAREN) {
                advance()
                skipType()
                return
            }
        }
        while (at(QUESTION)) advance()
    }

    private fun skipTypeArgumentsInType() {
        if (!match(LT)) return
        do {
            if (at(IN) || (isSoft("out") && peekAfter().type == IDENTIFIER)) advance()
            skipType()
        } while (match(COMMA))
        expect(GT, "'>'")
    }

    /** Skips `<T, R : Any>` after `fun`, `val` or a class name. */
    private fun skipTypeParameters() {
        if (!at(LT)) return
        val end = scanTypeArguments(pos)
        if (end < 0) throw error("Invalid type parameters")
        pos = end
    }

    /** Skips explicit type arguments in a call like `listOf<Int>()`. */
    private fun skipTypeArguments() {
        val end = scanTypeArguments(pos)
        if (end >= 0) pos = end
    }

    private fun skipWhereClause() {
        if (!isSoft("where")) return
        while (!at(LBRACE) && !at(ASSIGN) && !at(NEWLINE) && !at(EOF)) advance()
    }

    /** Returns the index after a balanced `<…>` starting at [start] if it looks like type arguments, else -1. Never reports errors. */
    private fun scanTypeArguments(start: Int): Int {
        if (tokens[start].type != LT) return -1
        var angles = 0
        var parens = 0
        var i = start
        while (i < tokens.size) {
            when (tokens[i].type) {
                LT -> angles++
                GT -> {
                    angles--
                    if (angles == 0) return i + 1
                }
                LPAREN -> parens++
                RPAREN -> {
                    if (parens == 0) return -1
                    parens--
                }
                IDENTIFIER, DOT, COMMA, QUESTION, STAR, ARROW, COLON, AT, IN -> Unit
                else -> return -1
            }
            i++
        }
        return -1
    }

    // ---------- Statements ----------

    private fun parseBlock(): Block {
        val start = expect(LBRACE, "'{'")
        val statements = withNewlines(true) {
            val list = parseStatementsUntilRBrace()
            expect(RBRACE, "'}'")
            list
        }
        return Block(id(), spanFrom(start), statements)
    }

    private fun parseStatementsUntilRBrace(): List<Stmt> {
        val statements = mutableListOf<Stmt>()
        while (true) {
            skipSeparators()
            if (at(RBRACE) || at(EOF)) break
            try {
                statements += parseStatement()
                if (tokens[pos].type !in statementEnds) throw error("Expected end of statement, found ${describe(tokens[pos])}")
            } catch (_: ParseException) {
                // Already reported as a diagnostic; parsing resumes at the next statement.
                syncStatement()
            }
        }
        return statements
    }

    private fun parseStatement(): Stmt {
        val start = cur()
        when (start.type) {
            FOR -> return parseFor()
            WHILE -> return parseWhile()
            DO -> return parseDoWhile()
            VAL, VAR -> if (peekAfter().type == LPAREN) return parseDestructuring() else parseDeclarationOrNull()?.let { return it }
            FUN, CLASS, OBJECT, AT -> parseDeclarationOrNull()?.let { return it }
            IDENTIFIER -> if (isModifierWord() || isSoft("interface")) parseDeclarationOrNull()?.let { return it }
            else -> Unit
        }
        val expr = parseExpression()
        if (cur().type in assignOps) {
            val op = advance()
            if (expr !is Ident && expr !is Member && expr !is Index) throw error("Invalid assignment target", op)
            skipNewlines()
            val value = parseExpression()
            return Assign(id(), spanFrom(start), expr, op.text, value)
        }
        return when (expr) {
            is ReturnStmt -> expr
            is BreakStmt -> expr
            is ContinueStmt -> expr
            else -> ExprStmt(id(), expr.span, expr)
        }
    }

    private fun parseFor(): ForStmt {
        val start = expect(FOR, "'for'")
        var destructured: List<String>? = null
        val (variable, iterable) = inParens {
            expect(LPAREN, "'('")
            val name = if (at(LPAREN)) {
                destructured = parseDestructuringNames()
                ""
            } else {
                expect(IDENTIFIER, "a loop variable").value.also { if (match(COLON)) parseType() }
            }
            expect(IN, "'in'")
            val iterable = parseExpression()
            expect(RPAREN, "')'")
            name to iterable
        }
        return ForStmt(id(), spanFrom(start), variable, iterable, parseControlBody(), destructured)
    }

    /** Parses `val (a, b) = value`. */
    private fun parseDestructuring(): DestructuringDecl {
        val start = cur()
        val isVar = advance().type == VAR
        val names = parseDestructuringNames()
        expect(ASSIGN, "'='")
        skipNewlines()
        return DestructuringDecl(id(), spanFrom(start), names, isVar, parseExpression())
    }

    /** Parses `(a, b: Type, _)`; types are skipped. */
    private fun parseDestructuringNames(): List<String> = inParens {
        expect(LPAREN, "'('")
        val names = mutableListOf<String>()
        while (!at(RPAREN)) {
            names += expect(IDENTIFIER, "a variable name").value
            if (match(COLON)) parseType()
            if (!match(COMMA)) break
        }
        expect(RPAREN, "')'")
        names
    }

    private fun parseWhile(): WhileStmt {
        val start = expect(WHILE, "'while'")
        val condition = parseCondition()
        return WhileStmt(id(), spanFrom(start), condition, parseControlBody(), isDoWhile = false)
    }

    private fun parseDoWhile(): WhileStmt {
        val start = expect(DO, "'do'")
        val body = parseControlBody()
        skipNewlines()
        expect(WHILE, "'while'")
        val condition = parseCondition()
        return WhileStmt(id(), spanFrom(start), condition, body, isDoWhile = true)
    }

    private fun parseCondition(): Expr = inParens {
        expect(LPAREN, "'('")
        val condition = parseExpression()
        expect(RPAREN, "')'")
        condition
    }

    /** Parses a `{ … }` block or a single statement wrapped in a block. */
    private fun parseControlBody(): Block {
        skipNewlines()
        if (at(LBRACE)) return parseBlock()
        val statement = parseStatement()
        return Block(id(), statement.span, listOf(statement))
    }

    // ---------- Expressions ----------

    private fun parseExpression(minPower: Int = 0): Expr {
        val start = cur()
        var left = parsePrefix()
        while (true) {
            continueOnNextLine(ELVIS, AND_AND, OR_OR)
            val op = cur()
            val power = bindingPower(op) ?: break
            if (power < minPower) break
            advance()
            left = when (op.type) {
                IS, NOT_IS -> TypeCheck(id(), spanFrom(start), left, parseType(), isNegated = op.type == NOT_IS)
                AS, AS_SAFE -> Cast(id(), spanFrom(start), left, parseType(), isSafe = op.type == AS_SAFE)
                IDENTIFIER -> {
                    skipNewlines()
                    val right = parseExpression(power + 1)
                    val callee = Member(id(), spanFrom(start), left, op.value, isSafe = false)
                    Call(id(), spanFrom(start), callee, listOf(Arg(null, right)), null)
                }
                else -> {
                    skipNewlines()
                    val right = parseExpression(power + 1)
                    Binary(id(), spanFrom(start), op.text, left, right)
                }
            }
        }
        return left
    }

    private fun bindingPower(t: Token): Int? = when (t.type) {
        OR_OR -> 2
        AND_AND -> 3
        EQ_EQ, NOT_EQ, EQ_EQ_EQ, NOT_EQ_EQ -> 4
        LT, GT, LT_EQ, GT_EQ -> 5
        IN, NOT_IN, IS, NOT_IS -> 6
        ELVIS -> 7
        IDENTIFIER -> 8
        RANGE, RANGE_UNTIL -> 9
        PLUS, MINUS -> 10
        STAR, SLASH, PERCENT -> 11
        AS, AS_SAFE -> 12
        else -> null
    }

    private fun parsePrefix(): Expr {
        val start = cur()
        return when (start.type) {
            MINUS, PLUS, NOT, PLUS_PLUS, MINUS_MINUS -> {
                advance()
                Unary(id(), spanFrom(start), start.text, parsePrefix(), isPrefix = true)
            }
            AT -> {
                parseAnnotation()
                skipNewlines()
                parsePrefix()
            }
            else -> parsePostfix(parsePrimary())
        }
    }

    private fun parsePostfix(primary: Expr): Expr {
        var expr = primary
        val start = expr.span
        fun span() = Span(start.start, maxOf(lastEnd, start.end), start.line, start.column)
        while (true) {
            continueOnNextLine(DOT, SAFE_DOT)
            val t = cur()
            expr = when (t.type) {
                DOT, SAFE_DOT -> {
                    advance()
                    skipNewlines()
                    Member(id(), span(), expr, expect(IDENTIFIER, "a member name").value, isSafe = t.type == SAFE_DOT)
                }
                LPAREN -> {
                    val args = parseCallArgs()
                    val lambda = when {
                        at(LBRACE) -> parseLambda()
                        labeledLambdaAt() -> parseLabeledLambda()
                        else -> null
                    }
                    Call(id(), span(), expr, args, lambda)
                }
                LBRACE -> {
                    if (!isCallable(expr)) return expr
                    Call(id(), span(), expr, emptyList(), parseLambda())
                }
                // A labeled trailing lambda, `items.forEach each@{ … }`.
                IDENTIFIER -> {
                    if (!isCallable(expr) || !labeledLambdaAt()) return expr
                    Call(id(), span(), expr, emptyList(), parseLabeledLambda())
                }
                LT -> {
                    if (expr !is Ident && expr !is Member) return expr
                    val end = scanTypeArguments(pos)
                    if (end < 0 || tokens[end].type !in setOf(LPAREN, LBRACE, DOUBLE_COLON)) return expr
                    pos = end
                    expr
                }
                LBRACKET -> {
                    val indices = inParens {
                        advance()
                        val list = mutableListOf<Expr>()
                        while (!at(RBRACKET)) {
                            list += parseExpression()
                            if (!match(COMMA)) break
                        }
                        expect(RBRACKET, "']'")
                        list
                    }
                    Index(id(), span(), expr, indices)
                }
                NOT_NULL, PLUS_PLUS, MINUS_MINUS -> {
                    advance()
                    Unary(id(), span(), t.text, expr, isPrefix = false)
                }
                DOUBLE_COLON -> {
                    advance()
                    val name = if (match(CLASS)) "class" else expect(IDENTIFIER, "a member name").value
                    CallableRef(id(), span(), expr, name)
                }
                else -> return expr
            }
        }
    }

    /** True when a labeled lambda, `name@{ … }`, starts at the current token. */
    private fun labeledLambdaAt(): Boolean {
        val t = cur()
        return t.type == IDENTIFIER && tokens[pos + 1].type == AT && tokens[pos + 1].start == t.end &&
            tokens[pos + 2].type == LBRACE
    }

    /** Parses `name@{ … }`, a lambda that `return@name` returns from. */
    private fun parseLabeledLambda(): Lambda {
        val label = advance().value
        advance()
        return parseLambda().copy(label = label)
    }

    /** A trailing lambda attaches only to names, member accesses and calls without one. */
    private fun isCallable(expr: Expr): Boolean =
        expr is Ident || expr is Member || (expr is Call && expr.trailingLambda == null)

    private fun parsePrimary(): Expr {
        val t = cur()
        return when (t.type) {
            INT -> {
                advance()
                integer(t, t.text)
            }
            LONG -> {
                advance()
                LongLit(id(), spanFrom(t), parseInteger(t, t.text.dropLast(1)))
            }
            FLOAT -> {
                advance()
                FloatLit(id(), spanFrom(t), t.value.replace("_", "").dropLast(1).toFloat())
            }
            DOUBLE -> {
                advance()
                DoubleLit(id(), spanFrom(t), t.value.replace("_", "").toDouble())
            }
            CHAR -> {
                advance()
                CharLit(id(), spanFrom(t), t.value[0])
            }
            TRUE, FALSE -> {
                advance()
                BoolLit(id(), spanFrom(t), t.type == TRUE)
            }
            NULL -> {
                advance()
                NullLit(id(), spanFrom(t))
            }
            THIS -> {
                advance()
                skipLabel()
                ThisExpr(id(), spanFrom(t))
            }
            STRING_START -> parseString()
            IDENTIFIER -> {
                when {
                    t.text == "try" && peekAfter().type == LBRACE -> parseTry()
                    t.text == "throw" -> {
                        advance()
                        ThrowExpr(id(), spanFrom(t), parseExpression())
                    }
                    labeledLambdaAt() -> parseLabeledLambda()
                    else -> {
                        advance()
                        Ident(id(), spanFrom(t), t.value)
                    }
                }
            }
            LPAREN -> inParens {
                advance()
                val expr = parseExpression()
                expect(RPAREN, "')'")
                expr
            }
            LBRACE -> parseLambda()
            IF -> parseIf()
            WHEN -> parseWhen()
            DOUBLE_COLON -> {
                advance()
                CallableRef(id(), spanFrom(t), null, expect(IDENTIFIER, "a function name").value)
            }
            RETURN -> {
                advance()
                val label = skipLabel()
                val value = if (cur().type in returnEnds) null else parseExpression()
                ReturnStmt(id(), spanFrom(t), value, label)
            }
            BREAK -> {
                advance()
                skipLabel()
                BreakStmt(id(), spanFrom(t))
            }
            CONTINUE -> {
                advance()
                skipLabel()
                ContinueStmt(id(), spanFrom(t))
            }
            ERROR -> {
                advance()
                ErrorExpr(id(), spanFrom(t))
            }
            OBJECT -> {
                val decl = parseClass(t, Modifiers(emptyList(), emptySet()), anonymous = true)
                ObjectExpr(id(), spanFrom(t), decl)
            }
            else -> throw error("Expected an expression, found ${describe(t)}")
        }
    }

    private fun integer(t: Token, text: String): Expr {
        val value = parseInteger(t, text)
        return if (value in Int.MIN_VALUE..Int.MAX_VALUE) IntLit(id(), spanFrom(t), value.toInt()) else LongLit(id(), spanFrom(t), value)
    }

    private fun parseInteger(t: Token, text: String): Long {
        val raw = text.replace("_", "")
        val value = when {
            raw.startsWith("0x", ignoreCase = true) -> raw.drop(2).toLongOrNull(16)
            raw.startsWith("0b", ignoreCase = true) -> raw.drop(2).toLongOrNull(2)
            else -> raw.toLongOrNull()
        }
        return value ?: throw error("Number is out of range", t)
    }

    /** Skips a `@label` directly attached to the previous token and returns its name. */
    private fun skipLabel(): String? {
        val t = tokens[pos]
        if (t.type != AT || t.start != lastEnd || tokens[pos + 1].type != IDENTIFIER) return null
        advance()
        return advance().value
    }

    private fun parseString(): StringTemplate {
        val start = advance()
        val parts = mutableListOf<StringPart>()
        while (true) {
            val t = tokens[pos]
            when (t.type) {
                STRING_TEXT -> {
                    advance()
                    parts += StringPart.Text(t.value)
                }
                TEMPLATE_IDENT -> {
                    advance()
                    parts += StringPart.Template(Ident(id(), spanFrom(t), t.value))
                }
                TEMPLATE_START -> {
                    advance()
                    parts += StringPart.Template(
                        inParens {
                            val expr = parseExpression()
                            expect(TEMPLATE_END, "'}'")
                            expr
                        },
                    )
                }
                STRING_END -> {
                    advance()
                    break
                }
                else -> break // unterminated; already reported by the lexer
            }
        }
        return StringTemplate(id(), spanFrom(start), parts)
    }

    private fun parseCallArgs(): List<Arg> = inParens {
        expect(LPAREN, "'('")
        val args = mutableListOf<Arg>()
        while (!at(RPAREN)) {
            val name = if (at(IDENTIFIER) && peekAfter().type == ASSIGN) {
                val n = advance().value
                advance()
                n
            } else {
                null
            }
            val isSpread = match(STAR)
            args += Arg(name, parseExpression(), isSpread)
            if (!match(COMMA)) break
        }
        expect(RPAREN, "')'")
        args
    }

    private fun parseLambda(): Lambda {
        val start = expect(LBRACE, "'{'")
        return withNewlines(true) {
            skipNewlines()
            val destructured = mutableMapOf<Int, List<String>>()
            val params = if (hasLambdaParams()) parseLambdaParams(destructured) else emptyList()
            val statements = parseStatementsUntilRBrace()
            expect(RBRACE, "'}'")
            val span = spanFrom(start)
            Lambda(id(), span, params, Block(id(), span, statements), destructured)
        }
    }

    /** Checks for `a, (b, c: Type), d: Type ->` (or a bare `->`) at the start of a lambda. */
    private fun hasLambdaParams(): Boolean {
        var i = pos
        if (tokens[i].type == ARROW) return true
        while (true) {
            if (tokens[i].type == LPAREN) {
                i++
                while (tokens[i].type != RPAREN) {
                    if (tokens[i].type != IDENTIFIER) return false
                    i++
                    if (tokens[i].type == COLON) i = typeEnd(i + 1) ?: return false
                    if (tokens[i].type == COMMA) i++ else if (tokens[i].type != RPAREN) return false
                }
            } else if (tokens[i].type != IDENTIFIER) {
                return false
            }
            i++
            if (tokens[i].type == COLON) i = typeEnd(i + 1) ?: return false
            when (tokens[i].type) {
                COMMA -> i++
                ARROW -> return true
                else -> return false
            }
        }
    }

    /**
     * Index after a type that starts at [start] in a lambda parameter list: the `,`, `->` or closing `)` that ends it,
     * outside its own brackets. Null when the tokens can't be a parameter type.
     */
    private fun typeEnd(start: Int): Int? {
        var depth = 0
        var i = start
        while (true) {
            when (tokens[i].type) {
                LT, LPAREN -> depth++
                GT -> depth--
                RPAREN -> if (depth == 0) return i else depth--
                COMMA, ARROW -> if (depth == 0) return i
                NEWLINE, RBRACE, LBRACE, EOF, SEMICOLON -> return null
                else -> Unit
            }
            i++
        }
    }

    /** Parses lambda parameters; a destructured parameter `(a, b)` is named `_` and its names go to [destructured]. */
    private fun parseLambdaParams(destructured: MutableMap<Int, List<String>>): List<String> {
        val params = mutableListOf<String>()
        while (!at(ARROW)) {
            if (at(LPAREN)) {
                destructured[params.size] = parseDestructuringNames()
                params += "_"
            } else {
                params += expect(IDENTIFIER, "a parameter name").value
            }
            if (match(COLON)) parseType()
            if (!match(COMMA)) break
        }
        expect(ARROW, "'->'")
        return params
    }

    private fun parseTry(): TryExpr {
        val start = advance()
        val body = parseBlock()
        val catches = mutableListOf<CatchClause>()
        while (nextNonNewline().let { it.type == IDENTIFIER && it.text == "catch" }) {
            skipNewlines()
            advance()
            val (name, type) = inParens {
                expect(LPAREN, "'('")
                val name = expect(IDENTIFIER, "an exception name").value
                expect(COLON, "':'")
                val type = parseType()
                expect(RPAREN, "')'")
                name to type
            }
            skipNewlines()
            catches += CatchClause(name, type, parseBlock())
        }
        val finallyBlock = if (nextNonNewline().let { it.type == IDENTIFIER && it.text == "finally" }) {
            skipNewlines()
            advance()
            skipNewlines()
            parseBlock()
        } else {
            null
        }
        if (catches.isEmpty() && finallyBlock == null) throw error("Expected 'catch' or 'finally'")
        return TryExpr(id(), spanFrom(start), body, catches, finallyBlock)
    }

    private fun parseIf(): IfExpr {
        val start = expect(IF, "'if'")
        val condition = parseCondition()
        val then = parseControlBody()
        val otherwise = if (nextNonNewline().type == ELSE) {
            skipNewlines()
            advance()
            parseControlBody()
        } else {
            null
        }
        return IfExpr(id(), spanFrom(start), condition, then, otherwise)
    }

    private fun parseWhen(): WhenExpr {
        val start = expect(WHEN, "'when'")
        var subjectName: String? = null
        val subject = if (at(LPAREN)) {
            inParens {
                advance()
                if (match(VAL)) {
                    subjectName = expect(IDENTIFIER, "a name").value
                    if (match(COLON)) parseType()
                    expect(ASSIGN, "'='")
                }
                val e = parseExpression()
                expect(RPAREN, "')'")
                e
            }
        } else {
            null
        }
        skipNewlines()
        expect(LBRACE, "'{'")
        val branches = mutableListOf<WhenBranch>()
        withNewlines(true) {
            while (true) {
                skipSeparators()
                if (at(RBRACE) || at(EOF)) break
                try {
                    val conditions = mutableListOf<WhenCondition>()
                    if (!match(ELSE)) {
                        do {
                            skipNewlines()
                            // A trailing comma may end the conditions: `A, B, -> …`.
                            if (conditions.isNotEmpty() && at(ARROW)) break
                            conditions += parseWhenCondition()
                        } while (match(COMMA))
                    }
                    val guard = if (match(IF)) parseExpression() else null
                    expect(ARROW, "'->'")
                    branches += WhenBranch(conditions, parseControlBody(), guard)
                } catch (_: ParseException) {
                    // Already reported as a diagnostic; parsing resumes at the next statement.
                    syncStatement()
                }
            }
            expect(RBRACE, "'}'")
        }
        return WhenExpr(id(), spanFrom(start), subject, branches, subjectName)
    }

    private fun parseWhenCondition(): WhenCondition {
        val t = cur()
        return when (t.type) {
            IS, NOT_IS -> {
                advance()
                WhenCondition.Is(parseType(), isNegated = t.type == NOT_IS)
            }
            IN, NOT_IN -> {
                advance()
                WhenCondition.In(parseExpression(), isNegated = t.type == NOT_IN)
            }
            else -> WhenCondition.Value(parseExpression())
        }
    }

    // ---------- Token helpers ----------

    private val newlinesIgnored: Boolean get() = newlineModes.lastOrNull() == false

    /** Current token; skips newlines first when they are ignored. */
    private fun cur(): Token {
        if (newlinesIgnored) skipNewlines()
        return tokens[pos]
    }

    /** Token right after the current one. */
    private fun peekAfter(): Token {
        cur()
        return tokens[minOf(pos + 1, tokens.lastIndex)]
    }

    private fun at(type: TokenType) = cur().type == type

    private fun isSoft(word: String): Boolean = cur().let { it.type == IDENTIFIER && it.text == word }

    private fun advance(): Token {
        val t = cur()
        if (t.type != EOF) pos++
        lastEnd = t.end
        return t
    }

    private fun match(type: TokenType): Boolean {
        if (!at(type)) return false
        advance()
        return true
    }

    private fun expect(type: TokenType, what: String): Token {
        if (at(type)) return advance()
        throw error("Expected $what, found ${describe(cur())}")
    }

    private fun skipNewlines() {
        while (tokens[pos].type == NEWLINE) pos++
    }

    private fun skipSeparators() {
        while (tokens[pos].type == NEWLINE || tokens[pos].type == SEMICOLON) pos++
    }

    private fun indexOfNextNonNewline(): Int {
        var i = pos
        while (tokens[i].type == NEWLINE) i++
        return i
    }

    private fun nextNonNewline(): Token = tokens[indexOfNextNonNewline()]

    /** Skips newlines when the next line starts with one of [types], so the expression continues. */
    private fun continueOnNextLine(vararg types: TokenType) {
        if (tokens[pos].type == NEWLINE && nextNonNewline().type in types) skipNewlines()
    }

    private inline fun <T> withNewlines(significant: Boolean, block: () -> T): T {
        newlineModes.addLast(significant)
        try {
            return block()
        } finally {
            newlineModes.removeLast()
        }
    }

    private inline fun <T> inParens(block: () -> T): T = withNewlines(false, block)

    private fun id() = nextId++

    private fun spanFrom(start: Token) = Span(start.start, maxOf(lastEnd, start.end), start.line, start.column)

    private fun Token.toSpan() = Span(start, maxOf(end, start + 1), line, column)

    /** Joins token texts in [from, to) into readable type text. */
    private fun textOf(from: Int, to: Int): String {
        val sb = StringBuilder()
        var prev: Token? = null
        var prevPrev: Token? = null
        for (i in from until to) {
            val t = tokens[i]
            if (t.type == NEWLINE) continue
            if (prev != null) {
                val space = t.type == ARROW || prev.type == ARROW || prev.type == COMMA ||
                    (prev.type.isWord() && t.type.isWord()) ||
                    (prev.type == IDENTIFIER && prevPrev?.type == AT && t.type != DOT && t.type != COLON) ||
                    (prev.text == "suspend" && t.type == LPAREN)
                if (space) sb.append(' ')
            }
            sb.append(t.text)
            prevPrev = prev
            prev = t
        }
        return sb.toString()
    }

    private fun TokenType.isWord() = this == IDENTIFIER || this == IN

    private fun describe(t: Token) = when (t.type) {
        EOF -> "end of file"
        NEWLINE -> "end of line"
        else -> "'${t.text}'"
    }

    // ---------- Errors ----------

    private fun error(message: String, at: Token = cur()): ParseException {
        diagnostics += Diagnostic(Diagnostic.Severity.ERROR, message, at.toSpan())
        return ParseException()
    }

    /** Skips to the end of the current statement: a newline or `;` outside brackets, or the `}` closing the current block. */
    private fun syncStatement() {
        var depth = 0
        while (tokens[pos].type != EOF) {
            when (tokens[pos].type) {
                NEWLINE, SEMICOLON -> if (depth == 0) return
                RBRACE -> if (depth == 0) return else depth--
                LBRACE, LPAREN, LBRACKET -> depth++
                RPAREN, RBRACKET -> if (depth > 0) depth--
                else -> Unit
            }
            pos++
        }
    }

    /** Skips to the next line that starts a top-level declaration. */
    private fun syncDeclaration() {
        newlineModes.clear()
        while (tokens[pos].type != EOF) {
            pos++
            if (tokens[pos - 1].type == NEWLINE && startsDeclaration(tokens[pos])) return
        }
    }

    private fun startsDeclaration(t: Token) = when (t.type) {
        FUN, VAL, VAR, CLASS, OBJECT, AT -> true
        IDENTIFIER -> t.text in modifierWords || t.text == "interface" || t.text == "import" || t.text == "typealias"
        else -> false
    }

    private companion object {
        val visibilityWords = setOf("private", "public", "internal", "protected")

        /** Modifiers that may come before a property accessor. */
        val accessorModifiers = setOf("private", "public", "internal", "protected", "inline", "external")
        val modifierWords = setOf(
            "private", "public", "internal", "protected", "override", "open", "abstract", "sealed", "final",
            "inline", "noinline", "crossinline", "suspend", "operator", "infix", "const", "lateinit", "tailrec",
            "external", "data", "enum", "inner", "value", "annotation", "companion", "expect", "actual",
            "vararg", "reified",
        )
        val assignOps = setOf(ASSIGN, PLUS_EQ, MINUS_EQ, STAR_EQ, SLASH_EQ, PERCENT_EQ)
        val statementEnds = setOf(NEWLINE, SEMICOLON, RBRACE, EOF)
        val returnEnds = setOf(NEWLINE, SEMICOLON, RBRACE, RPAREN, COMMA, ELSE, EOF)
    }
}

/** Parses Kotlin source text into a [ParseResult]. */
public object KotlinParser {
    /**
     * True if [source] has a `@Preview` annotation, written `@Preview` or qualified like
     * `@androidx.compose.ui.tooling.preview.Preview`, without parsing it. Comments, strings and imports don't count.
     * Cheap enough to call on every change, e.g. to decide whether a file is worth previewing.
     */
    public fun hasPreviews(source: String): Boolean {
        val tokens = Lexer(source).tokenize()
        for (i in tokens.indices) {
            if (tokens[i].type != TokenType.AT) continue
            var j = i + 1
            var name: String? = null
            while (j < tokens.size && tokens[j].type == TokenType.IDENTIFIER) {
                name = tokens[j].text
                if (tokens.getOrNull(j + 1)?.type != TokenType.DOT) break
                j += 2
            }
            if (name == "Preview") return true
        }
        return false
    }

    /** Parses [source]; [path] identifies the file in a project of several files and is kept in [Program.path]. */
    public fun parse(source: String, path: String? = null): ParseResult {
        val lexer = Lexer(source)
        val result = Parser(lexer.tokenize()).parse(path)
        return result.copy(diagnostics = lexer.diagnostics + result.diagnostics)
    }
}
