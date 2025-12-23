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

class IntervalTree<V> {
    private class Entry<V>(val span: Span, val value: V)

    private class Node<V>(val center: UInt) {
        val overlapping: MutableList<Entry<V>> = mutableListOf()
        var left: Node<V>? = null
        var right: Node<V>? = null
    }

    private var root: Node<V>? = null

    operator fun set(span: Span, value: V) {
        root = insert(root, Entry(span, value))
    }

    operator fun get(offset: UInt): List<V> {
        val out = mutableListOf<V>()
        query(root, offset, out)
        return out
    }

    private fun insert(node: Node<V>?, entry: Entry<V>): Node<V> {
        val start = entry.span.start
        val end = entry.span.end

        if (node == null) {
            fun mid(a: UInt, b: UInt): UInt = a + (b - a) / 2u
            val center = mid(start, end)
            return Node<V>(center).also { it.overlapping.add(entry) }
        }

        when {
            end < node.center -> node.left = insert(node.left, entry)
            start > node.center -> node.right = insert(node.right, entry)
            else -> node.overlapping.add(entry)
        }
        
        return node
    }

    private tailrec fun query(node: Node<V>?, offset: UInt, out: MutableList<V>) {
        if (node == null) return

        for (e in node.overlapping) {
            if (offset in e.span) out.add(e.value)
        }

        when {
            offset < node.center -> query(node.left, offset, out)
            offset > node.center -> query(node.right, offset, out)
        }
    }
}

@Suppress("NOTHING_TO_INLINE")
inline fun <V> intervalTreeOf(): IntervalTree<V> = IntervalTree()

data class Diagnostic(
    val message: String,
    val span: Span,
)
