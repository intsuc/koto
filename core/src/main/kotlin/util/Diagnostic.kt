package koto.core.util

data class Diagnostic(
    val message: String,
    val span: Span,
    val severity: Severity,
)

enum class Severity {
    ERROR,
    WARNING,
}
