package koto.core

import java.util.*

@JvmInline
value class Span(private val value: ULong) {
    constructor(start: UInt, end: UInt) : this((start.toULong() shl 32) or end.toULong())

    val start: UInt get() = (value shr 32).toUInt()
    val end: UInt get() = (value and 0xFFFFFFFFu).toUInt()

    companion object {
        val ZERO = Span(0u, 0u)
    }
}

operator fun Span.contains(offset: UInt): Boolean = offset in start..<end

class IntervalMap<K : Comparable<K>, V : Any> {
    private val boundaries = TreeMap<K, V?>()

    operator fun get(key: K): V? = boundaries.floorEntry(key)?.value

    fun set(begin: K, endExclusive: K, value: V) {
        if (begin >= endExclusive) {
            return
        }

        val beforeBeginValue = boundaries.lowerEntry(begin)?.value
        val oldEndValue = this[endExclusive]

        boundaries.subMap(begin, true, endExclusive, false).clear()

        if (beforeBeginValue != value) {
            boundaries[begin] = value
        }

        if (oldEndValue == value) {
            boundaries.remove(endExclusive)
        } else {
            boundaries[endExclusive] = oldEndValue
        }
    }
}

@Suppress("NOTHING_TO_INLINE")
inline fun <K : Comparable<K>, V : Any> intervalMapOf(): IntervalMap<K, V> = IntervalMap()

operator fun <V : Any> IntervalMap<UInt, V>.set(span: Span, value: V) {
    set(span.start, span.end, value)
}

data class Diagnostic(
    val message: String,
    val span: Span,
)
