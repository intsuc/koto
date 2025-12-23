package koto.core.util

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
