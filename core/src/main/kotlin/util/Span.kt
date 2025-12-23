package koto.core.util

@JvmInline
value class Span(private val value: ULong) {
    constructor(start: UInt, end: UInt) : this((start.toULong() shl 32) or end.toULong())

    val start: UInt get() = (value shr 32).toUInt()
    val end: UInt get() = (value and 0xFFFFFFFFu).toUInt()

    operator fun contains(offset: UInt): Boolean = offset in start..<end

    companion object {
        val ZERO = Span(0u, 0u)
    }
}
