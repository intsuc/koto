package koto.core

data class Diagnostic(
    val message: String,
    val span: Span,
)
