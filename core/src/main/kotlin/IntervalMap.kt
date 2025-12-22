package koto.core

import java.util.*

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
