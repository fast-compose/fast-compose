package ma7moud3ly.fast.compose.parser

/** Location of a node or error in the source. */
public data class Span(val start: Int, val end: Int, val line: Int, val column: Int)

public data class Diagnostic(
    val severity: Severity,
    val message: String,
    val span: Span,
) {
    public enum class Severity { ERROR, WARNING }
}
