package koto.core

@JvmInline
value class Span(private val value: ULong) {
    constructor(start: UInt, end: UInt) : this((start.toULong() shl 32) or end.toULong())

    val start: UInt get() = (value shr 32).toUInt()
    val end: UInt get() = (value and 0xFFFFFFFFu).toUInt()
}

operator fun Span.contains(offset: UInt): Boolean = offset in start..<end

data class Diagnostic(
    val message: String,
    val span: Span,
)
