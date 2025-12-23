package koto.core.util

import java.util.*

class IntervalMap<V> {
    private val boundaries = TreeMap<UInt, V?>()

    operator fun get(key: UInt): V? = boundaries.floorEntry(key)?.value

    operator fun set(span: Span, value: V) {
        val start = span.start
        val end = span.end
        val beforeBeginValue = boundaries.lowerEntry(start)?.value
        val oldEndValue = this[end]

        boundaries.subMap(start, true, end, false).clear()

        if (beforeBeginValue != value) {
            boundaries[start] = value
        }

        if (oldEndValue == value) {
            boundaries.remove(end)
        } else {
            boundaries[end] = oldEndValue
        }
    }
}

@Suppress("NOTHING_TO_INLINE")
inline fun <V : Any> intervalMapOf(): IntervalMap<V> = IntervalMap()
