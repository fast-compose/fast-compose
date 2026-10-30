package ma7moud3ly.fast.compose.parser

/**
 * A lexical token with its position in the source.
 * [text] is the raw source slice; [value] is the decoded content: escapes resolved for
 * [TokenType.STRING_TEXT] and [TokenType.CHAR], the name for [TokenType.TEMPLATE_IDENT]
 * and backtick identifiers, otherwise the same as [text].
 */
public data class Token(
    val type: TokenType,
    val text: String,
    val start: Int,
    val line: Int,
    val column: Int,
    val value: String = text,
) {
    public val end: Int get() = start + text.length
}

public enum class TokenType {
    // Literals and names. Soft keywords (data, by, get, set, …) are IDENTIFIER.
    // INT may hold a value that only fits in Long (e.g. 0xFF000000); LONG is used for the `L` suffix.
    IDENTIFIER, INT, LONG, FLOAT, DOUBLE, CHAR,

    // String templates: "a $b ${c}" → STRING_START, STRING_TEXT, TEMPLATE_IDENT, STRING_TEXT, TEMPLATE_START, …, TEMPLATE_END, STRING_END
    STRING_START, STRING_TEXT, TEMPLATE_IDENT, TEMPLATE_START, TEMPLATE_END, STRING_END,

    // Keywords
    FUN, VAL, VAR, CLASS, OBJECT, IF, ELSE, WHEN, FOR, WHILE, DO, RETURN,
    BREAK, CONTINUE, IN, IS, AS, AS_SAFE, THIS, NULL, TRUE, FALSE,

    // Punctuation
    LPAREN, RPAREN, LBRACE, RBRACE, LBRACKET, RBRACKET,
    COMMA, DOT, SAFE_DOT, COLON, DOUBLE_COLON, SEMICOLON, ARROW, AT, QUESTION,

    // Operators
    PLUS, MINUS, STAR, SLASH, PERCENT,
    PLUS_EQ, MINUS_EQ, STAR_EQ, SLASH_EQ, PERCENT_EQ, ASSIGN,
    PLUS_PLUS, MINUS_MINUS,
    EQ_EQ, NOT_EQ, EQ_EQ_EQ, NOT_EQ_EQ, LT, GT, LT_EQ, GT_EQ,
    AND_AND, OR_OR, NOT, NOT_NULL, NOT_IS, NOT_IN, ELVIS, RANGE, RANGE_UNTIL,

    // Layout and special
    NEWLINE, EOF, ERROR,
}
