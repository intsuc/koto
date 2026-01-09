package koto.core.util

@JvmInline
value class Span(private val value: ULong) {
    constructor(start: UInt, end: UInt) : this((start.toULong() shl 32) or end.toULong())

    init {
        require(start <= endExclusive) { "Invalid Span: start ($start) > endExclusive ($endExclusive)" }
    }

    val start: UInt get() = (value shr 32).toUInt()
    val endExclusive: UInt get() = (value and 0xFFFFFFFFu).toUInt()

    operator fun contains(offset: UInt): Boolean = offset in start..<endExclusive

    fun includes(other: Span): Boolean = start < other.start || other.endExclusive < endExclusive

    companion object {
        val ZERO: Span = Span(0u, 0u)
        val ALL: Span = Span(UInt.MIN_VALUE, UInt.MAX_VALUE)
    }
}
