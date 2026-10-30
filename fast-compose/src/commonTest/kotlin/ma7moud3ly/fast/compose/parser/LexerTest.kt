package ma7moud3ly.fast.compose.parser

import ma7moud3ly.fast.compose.parser.TokenType.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LexerTest {

    private fun lex(source: String) = Lexer(source).tokenize()

    private fun types(source: String) = lex(source).dropLast(1).map { it.type }

    private fun values(source: String, type: TokenType) = lex(source).filter { it.type == type }.map { it.value }

    @Test
    fun endsWithEof() {
        assertEquals(listOf(EOF), lex("").map { it.type })
        assertEquals(EOF, lex("a b").last().type)
    }

    @Test
    fun composableCall() {
        assertEquals(
            listOf(
                IDENTIFIER, LPAREN, STRING_START, STRING_TEXT, STRING_END, COMMA,
                IDENTIFIER, DOT, IDENTIFIER, LPAREN, INT, DOT, IDENTIFIER, RPAREN, RPAREN,
            ),
            types("Text(\"Hello\", Modifier.width(100.dp))"),
        )
    }

    @Test
    fun keywordsAndSoftKeywords() {
        assertEquals(
            listOf(
                FUN, VAL, VAR, CLASS, OBJECT, IF, ELSE, WHEN, FOR, WHILE, DO, RETURN,
                BREAK, CONTINUE, IN, IS, AS, THIS, NULL, TRUE, FALSE, IDENTIFIER, IDENTIFIER,
            ),
            types("fun val var class object if else when for while do return break continue in is as this null true false data by"),
        )
    }

    @Test
    fun negatedAndSafeTypeOperators() {
        assertEquals(listOf(IDENTIFIER, AS_SAFE, IDENTIFIER), types("x as? Int"))
        assertEquals(listOf(IDENTIFIER, NOT_IS, IDENTIFIER), types("a !is B"))
        assertEquals(listOf(IDENTIFIER, NOT_IN, IDENTIFIER), types("a !in b"))
        assertEquals(listOf(NOT, IDENTIFIER), types("!isEnabled"))
    }

    @Test
    fun numbers() {
        assertEquals(
            listOf(INT, FLOAT, INT, LONG, DOUBLE, DOUBLE, INT, INT, FLOAT),
            types("16 1.5f 0xFF0000FF 10L 1e3 2.5 1_000 0b101 3f"),
        )
        assertEquals(listOf(INT, RANGE, INT), types("1..10"))
        assertEquals(listOf(INT, RANGE_UNTIL, IDENTIFIER), types("0..<n"))
        assertEquals(listOf(INT, DOT, IDENTIFIER), types("16.dp"))
    }

    @Test
    fun stringTemplate() {
        val source = "\"Hi \$name \${a + b}!\""
        assertEquals(
            listOf(
                STRING_START, STRING_TEXT, TEMPLATE_IDENT, STRING_TEXT, TEMPLATE_START,
                IDENTIFIER, PLUS, IDENTIFIER, TEMPLATE_END, STRING_TEXT, STRING_END,
            ),
            types(source),
        )
        assertEquals(listOf("Hi ", " ", "!"), values(source, STRING_TEXT))
        assertEquals(listOf("name"), values(source, TEMPLATE_IDENT))
    }

    @Test
    fun templateWithBracesAndNestedString() {
        assertEquals(
            listOf(
                STRING_START, TEMPLATE_START, IDENTIFIER, DOT, IDENTIFIER,
                LBRACE, IDENTIFIER, RBRACE, TEMPLATE_END, STRING_END,
            ),
            types("\"\${ items.map { it } }\""),
        )
        assertEquals(
            listOf(
                STRING_START, STRING_TEXT, TEMPLATE_START, STRING_START, STRING_TEXT, STRING_END,
                TEMPLATE_END, STRING_TEXT, STRING_END,
            ),
            types("\"a\${\"b\"}c\""),
        )
    }

    @Test
    fun escapes() {
        assertEquals(listOf("a\nb\$cA"), values("\"a\\nb\\\$c\\u0041\"", STRING_TEXT))
    }

    @Test
    fun rawStrings() {
        val source = "\"\"\"say \"hi\" \$x\"\"\""
        assertEquals(listOf(STRING_START, STRING_TEXT, TEMPLATE_IDENT, STRING_END), types(source))
        assertEquals(listOf("say \"hi\" "), values(source, STRING_TEXT))
        assertEquals(listOf("a\""), values("\"\"\"a\"\"\"\"", STRING_TEXT))
    }

    @Test
    fun chars() {
        assertEquals(listOf("a", "\n", "A"), values("'a' '\\n' '\\u0041'", CHAR))
    }

    @Test
    fun multiCharLiteralIsError() {
        val lexer = Lexer("'Hello'")
        assertEquals(listOf(ERROR, EOF), lexer.tokenize().map { it.type })
        assertEquals(1, lexer.diagnostics.size)
        assertTrue("use \"...\" for strings" in lexer.diagnostics.single().message)
    }

    @Test
    fun commentsAndNewlines() {
        assertEquals(
            listOf(IDENTIFIER, NEWLINE, IDENTIFIER),
            types("a // c\n/* x /* nested */ y */ b"),
        )
        assertEquals(listOf(IDENTIFIER, NEWLINE, IDENTIFIER), types("a\n\n\r\n  b"))
    }

    @Test
    fun operatorsUseLongestMatch() {
        assertEquals(
            listOf(
                IDENTIFIER, SAFE_DOT, IDENTIFIER, ELVIS, IDENTIFIER, NOT_NULL, EQ_EQ_EQ, IDENTIFIER,
                NOT_EQ_EQ, IDENTIFIER, ARROW, IDENTIFIER, DOUBLE_COLON, IDENTIFIER, PLUS_EQ, INT,
                AND_AND, IDENTIFIER, OR_OR, IDENTIFIER, PLUS_PLUS, LT_EQ, GT_EQ,
            ),
            types("a?.b ?: c!! === d !== e -> f :: g += 1 && h || i++ <= >="),
        )
    }

    @Test
    fun annotationsAndBackticks() {
        assertEquals(listOf(AT, IDENTIFIER, FUN, IDENTIFIER), types("@Composable fun `my preview`"))
        assertEquals(listOf("my preview"), values("`my preview`", IDENTIFIER))
    }

    @Test
    fun positions() {
        val tokens = lex("fun a()\n  b")
        val b = tokens.first { it.text == "b" }
        assertEquals(2, b.line)
        assertEquals(3, b.column)
        assertEquals(10, b.start)
        assertEquals(1, tokens.first().column)
    }

    @Test
    fun unterminatedString() {
        val lexer = Lexer("\"abc")
        assertEquals(listOf(STRING_START, STRING_TEXT, EOF), lexer.tokenize().map { it.type })
        assertTrue(lexer.diagnostics.single().message.startsWith("Unterminated string"))
    }

    @Test
    fun unexpectedCharacter() {
        val lexer = Lexer("a # b")
        assertEquals(listOf(IDENTIFIER, ERROR, IDENTIFIER, EOF), lexer.tokenize().map { it.type })
        assertEquals(1, lexer.diagnostics.size)
    }
}
