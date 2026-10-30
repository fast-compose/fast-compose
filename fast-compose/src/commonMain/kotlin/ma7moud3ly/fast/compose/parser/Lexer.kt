package ma7moud3ly.fast.compose.parser

import ma7moud3ly.fast.compose.parser.TokenType.*

/**
 * Converts source text into tokens, ending with [TokenType.EOF].
 * Never throws: invalid input produces [TokenType.ERROR] tokens and entries in [diagnostics].
 * Consecutive line breaks become a single [TokenType.NEWLINE].
 */
public class Lexer(private val source: String) {

    private val tokens = mutableListOf<Token>()
    private val _diagnostics = mutableListOf<Diagnostic>()
    public val diagnostics: List<Diagnostic> get() = _diagnostics

    private var pos = 0
    private var line = 1
    private var column = 1

    private var startPos = 0
    private var startLine = 1
    private var startColumn = 1

    /** Open string literals and `${…}` templates, innermost last. */
    private val modes = ArrayDeque<Mode>()

    private sealed interface Mode {
        class Str(val raw: Boolean) : Mode
        class Template(var depth: Int = 0) : Mode
    }

    public fun tokenize(): List<Token> {
        tokens.clear()
        _diagnostics.clear()
        modes.clear()
        pos = 0
        line = 1
        column = 1

        while (true) {
            val mode = modes.lastOrNull()
            if (mode is Mode.Str) {
                scanStringContent(mode)
                continue
            }
            if (isAtEnd) break
            scanToken()
        }
        if (modes.isNotEmpty()) {
            markStart()
            report("Unterminated string template")
        }
        markStart()
        add(EOF)
        return tokens.toList()
    }

    // ---------- Code ----------

    private fun scanToken() {
        markStart()
        val c = advance()
        when {
            c == '\n' -> if (tokens.lastOrNull()?.type != NEWLINE) add(NEWLINE)
            c == ' ' || c == '\t' || c == '\r' || c == '\u000C' -> Unit
            c == '/' && peek() == '/' -> lineComment()
            c == '/' && peek() == '*' -> blockComment()
            c.isAsciiDigit() -> number(c)
            c == '"' -> stringStart()
            c == '\'' -> charLiteral()
            c == '`' -> backtickIdentifier()
            isIdentStart(c) -> identifier()
            else -> operator(c)
        }
    }

    private fun lineComment() {
        while (!isAtEnd && peek() != '\n') advance()
    }

    private fun blockComment() {
        advance()
        var depth = 1
        while (depth > 0) {
            when {
                isAtEnd -> return report("Unterminated comment")
                peek() == '/' && peek(1) == '*' -> { advance(); advance(); depth++ }
                peek() == '*' && peek(1) == '/' -> { advance(); advance(); depth-- }
                else -> advance()
            }
        }
    }

    private fun identifier() {
        while (isIdentPart(peek())) advance()
        val type = keywords[current()] ?: IDENTIFIER
        if (type == AS && peek() == '?') {
            advance()
            add(AS_SAFE)
        } else {
            add(type)
        }
    }

    private fun backtickIdentifier() {
        while (!isAtEnd && peek() != '`' && peek() != '\n') advance()
        if (peek() != '`') return error("Unterminated backtick identifier")
        advance()
        add(IDENTIFIER, current().substring(1, current().length - 1))
    }

    private fun number(first: Char) {
        if (first == '0' && (peek() == 'x' || peek() == 'X')) return radixNumber { it.isHexDigit() }
        if (first == '0' && (peek() == 'b' || peek() == 'B')) return radixNumber { it == '0' || it == '1' }

        digits()
        var isDouble = false
        if (peek() == '.' && peek(1).isAsciiDigit()) {
            advance()
            digits()
            isDouble = true
        }
        if ((peek() == 'e' || peek() == 'E') &&
            (peek(1).isAsciiDigit() || ((peek(1) == '+' || peek(1) == '-') && peek(2).isAsciiDigit()))
        ) {
            advance()
            if (peek() == '+' || peek() == '-') advance()
            digits()
            isDouble = true
        }
        when {
            peek() == 'f' || peek() == 'F' -> { advance(); add(FLOAT) }
            peek() == 'L' && !isDouble -> { advance(); add(LONG) }
            isDouble -> add(DOUBLE)
            else -> add(INT)
        }
    }

    private inline fun radixNumber(isDigit: (Char) -> Boolean) {
        advance()
        val digitsStart = pos
        while (isDigit(peek()) || peek() == '_') advance()
        if (pos == digitsStart) return error("Expected digits after '${current()}'")
        if (peek() == 'L') {
            advance()
            add(LONG)
        } else {
            add(INT)
        }
    }

    private fun digits() {
        while (peek().isAsciiDigit() || peek() == '_') advance()
    }

    private fun operator(c: Char) {
        val type = when (c) {
            '(' -> LPAREN
            ')' -> RPAREN
            '[' -> LBRACKET
            ']' -> RBRACKET
            ',' -> COMMA
            ';' -> SEMICOLON
            '@' -> AT
            '{' -> {
                (modes.lastOrNull() as? Mode.Template)?.let { it.depth++ }
                LBRACE
            }
            '}' -> {
                val template = modes.lastOrNull() as? Mode.Template
                when {
                    template == null -> RBRACE
                    template.depth == 0 -> { modes.removeLast(); TEMPLATE_END }
                    else -> { template.depth--; RBRACE }
                }
            }
            '.' -> if (match('.')) { if (match('<')) RANGE_UNTIL else RANGE } else DOT
            ':' -> if (match(':')) DOUBLE_COLON else COLON
            '?' -> when {
                match('.') -> SAFE_DOT
                match(':') -> ELVIS
                else -> QUESTION
            }
            '+' -> when {
                match('+') -> PLUS_PLUS
                match('=') -> PLUS_EQ
                else -> PLUS
            }
            '-' -> when {
                match('-') -> MINUS_MINUS
                match('=') -> MINUS_EQ
                match('>') -> ARROW
                else -> MINUS
            }
            '*' -> if (match('=')) STAR_EQ else STAR
            '/' -> if (match('=')) SLASH_EQ else SLASH
            '%' -> if (match('=')) PERCENT_EQ else PERCENT
            '=' -> if (match('=')) { if (match('=')) EQ_EQ_EQ else EQ_EQ } else ASSIGN
            '!' -> when {
                match('=') -> if (match('=')) NOT_EQ_EQ else NOT_EQ
                match('!') -> NOT_NULL
                matchWord("is") -> NOT_IS
                matchWord("in") -> NOT_IN
                else -> NOT
            }
            '<' -> if (match('=')) LT_EQ else LT
            '>' -> if (match('=')) GT_EQ else GT
            '&' -> if (match('&')) AND_AND else null
            '|' -> if (match('|')) OR_OR else null
            else -> null
        }
        if (type == null) error("Unexpected character '${current()}'") else add(type)
    }

    // ---------- Strings and chars ----------

    private fun stringStart() {
        val raw = peek() == '"' && peek(1) == '"'
        if (raw) {
            advance()
            advance()
        }
        add(STRING_START)
        modes.addLast(Mode.Str(raw))
    }

    /** Scans string content up to the closing quote, a `${` template, or an error. */
    private fun scanStringContent(mode: Mode.Str) {
        markStart()
        val text = StringBuilder()
        while (true) {
            if (isAtEnd) {
                flushText(text)
                report("Unterminated string literal")
                modes.clear()
                return
            }
            val c = peek()
            when {
                c == '"' && !mode.raw -> {
                    flushText(text)
                    advance()
                    add(STRING_END)
                    modes.removeLast()
                    return
                }
                c == '"' && peek(1) == '"' && peek(2) == '"' -> {
                    // In raw strings, quotes before the closing three belong to the content.
                    var quotes = 3
                    while (peek(quotes) == '"') quotes++
                    repeat(quotes - 3) { text.append(advance()) }
                    flushText(text)
                    repeat(3) { advance() }
                    add(STRING_END)
                    modes.removeLast()
                    return
                }
                c == '\n' && !mode.raw -> {
                    flushText(text)
                    report("Unterminated string literal")
                    modes.removeLast()
                    return
                }
                c == '\\' && !mode.raw -> {
                    advance()
                    escape(text)
                }
                c == '$' && isIdentStart(peek(1)) -> {
                    flushText(text)
                    advance()
                    while (isIdentPart(peek())) advance()
                    add(TEMPLATE_IDENT, current().substring(1))
                    markStart()
                }
                c == '$' && peek(1) == '{' -> {
                    flushText(text)
                    advance()
                    advance()
                    add(TEMPLATE_START)
                    modes.addLast(Mode.Template())
                    return
                }
                else -> text.append(advance())
            }
        }
    }

    /** Adds pending string text as a [TokenType.STRING_TEXT] token and starts a new token. */
    private fun flushText(text: StringBuilder) {
        if (pos > startPos) add(STRING_TEXT, text.toString())
        text.clear()
        markStart()
    }

    private fun charLiteral() {
        val value = StringBuilder()
        while (!isAtEnd && peek() != '\'' && peek() != '\n') {
            if (peek() == '\\') {
                advance()
                escape(value)
            } else {
                value.append(advance())
            }
        }
        if (peek() != '\'') return error("Unterminated character literal")
        advance()
        when (value.length) {
            1 -> add(CHAR, value.toString())
            0 -> error("Empty character literal")
            else -> error("Too many characters in a character literal; use \"...\" for strings")
        }
    }

    /** Decodes an escape sequence after a consumed backslash into [out]. */
    private fun escape(out: StringBuilder) {
        val escStart = pos - 1
        val escLine = line
        val escColumn = column - 1
        if (isAtEnd) return report("Unterminated escape sequence", escStart, escLine, escColumn)
        when (val e = advance()) {
            't' -> out.append('\t')
            'b' -> out.append('\b')
            'n' -> out.append('\n')
            'r' -> out.append('\r')
            '\'', '"', '\\', '$' -> out.append(e)
            'u' -> {
                val hex = source.substring(pos, minOf(pos + 4, source.length))
                if (hex.length == 4 && hex.all { it.isHexDigit() }) {
                    repeat(4) { advance() }
                    out.append(hex.toInt(16).toChar())
                } else {
                    report("Invalid unicode escape", escStart, escLine, escColumn)
                }
            }
            else -> {
                report("Illegal escape: '\\$e'", escStart, escLine, escColumn)
                out.append(e)
            }
        }
    }

    // ---------- Helpers ----------

    private val isAtEnd: Boolean get() = pos >= source.length

    private fun peek(offset: Int = 0): Char = source.getOrElse(pos + offset) { '\u0000' }

    private fun advance(): Char {
        val c = source[pos++]
        if (c == '\n') {
            line++
            column = 1
        } else {
            column++
        }
        return c
    }

    private fun match(expected: Char): Boolean {
        if (peek() != expected) return false
        advance()
        return true
    }

    /** Consumes [word] only if it is not followed by an identifier character. */
    private fun matchWord(word: String): Boolean {
        if (!source.startsWith(word, pos) || isIdentPart(peek(word.length))) return false
        repeat(word.length) { advance() }
        return true
    }

    private fun markStart() {
        startPos = pos
        startLine = line
        startColumn = column
    }

    private fun current(): String = source.substring(startPos, pos)

    private fun add(type: TokenType, value: String? = null) {
        val text = current()
        tokens += Token(type, text, startPos, startLine, startColumn, value ?: text)
    }

    private fun error(message: String) {
        report(message)
        add(ERROR)
    }

    private fun report(
        message: String,
        start: Int = startPos,
        line: Int = startLine,
        column: Int = startColumn,
    ) {
        _diagnostics += Diagnostic(Diagnostic.Severity.ERROR, message, Span(start, maxOf(pos, start + 1), line, column))
    }

    private fun isIdentStart(c: Char) = c.isLetter() || c == '_'

    private fun isIdentPart(c: Char) = c.isLetterOrDigit() || c == '_'

    private fun Char.isAsciiDigit() = this in '0'..'9'

    private fun Char.isHexDigit() = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    private companion object {
        val keywords = mapOf(
            "fun" to FUN, "val" to VAL, "var" to VAR, "class" to CLASS, "object" to OBJECT,
            "if" to IF, "else" to ELSE, "when" to WHEN, "for" to FOR, "while" to WHILE, "do" to DO,
            "return" to RETURN, "break" to BREAK, "continue" to CONTINUE,
            "in" to IN, "is" to IS, "as" to AS, "this" to THIS,
            "null" to NULL, "true" to TRUE, "false" to FALSE,
        )
    }
}
