package koto.core.util

class IntervalTree<V> private constructor(
    private val root: Node<V>,
) {
    data class Entry<V>(
        val span: Span,
        val value: V,
    )

    private class Node<V>(
        val span: Span,
    ) {
        val values: MutableList<V> = mutableListOf()
        val children: MutableList<Node<V>> = mutableListOf()
    }

    fun getAll(offset: UInt): List<V> {
        val out = ArrayList<V>()
        var node = root

        while (true) {
            out.addAll(node.values)
            val child = findChildContaining(node.children, offset) ?: break
            node = child
        }
        return out
    }

    fun getLeaf(offset: UInt): Entry<V>? {
        var node = root
        var best: Node<V>? = null

        while (true) {
            val child = findChildContaining(node.children, offset) ?: break
            node = child
            best = node
        }

        return best?.values?.firstOrNull()?.let { value ->
            Entry(best.span, value)
        }
    }

    private fun findChildContaining(children: List<Node<V>>, offset: UInt): Node<V>? {
        if (children.isEmpty()) return null

        // Find the rightmost child with start <= offset.
        var l = 0
        var r = children.size
        while (l < r) {
            val m = (l + r) ushr 1
            if (children[m].span.start <= offset) l = m + 1 else r = m
        }
        val i = l - 1
        if (i < 0) return null
        val c = children[i]
        return if (offset < c.span.endExclusive) c else null
    }

    companion object {
        fun <V> of(entries: List<Entry<V>>): IntervalTree<V> {
            // Sort by start asc, end desc so outer intervals come before inner ones at same start.
            val sorted = entries.sortedWith(
                compareBy<Entry<V>> { (span, _) -> span.start }
                    .thenByDescending { (span, _) -> span.endExclusive }
            )

            val root = Node<V>(Span.ALL)
            val stack = ArrayDeque<Node<V>>()
            stack.addLast(root)

            for ((span, value) in sorted) {
                val s = span.start
                val t = span.endExclusive

                // Move up until we find a parent that can contain or be before this interval.
                while (stack.isNotEmpty() && s >= stack.last().span.endExclusive) {
                    stack.removeLast()
                }
                val parent = requireNotNull(stack.lastOrNull()) { "No parent found (this should not happen)." }

                require(parent.span.endExclusive !in (s + 1u)..<t) {
                    "Crossing intervals detected: child [$s, $t) crosses parent [${parent.span.start}, ${parent.span.endExclusive})"
                }
                require(t <= parent.span.endExclusive) {
                    "Interval [$s, $t) is not contained in parent [${parent.span.start}, ${parent.span.endExclusive})"
                }

                // Deduplicate identical boundaries by storing multiple values in the same node.
                val lastChild = parent.children.lastOrNull()
                if (lastChild != null && lastChild.span.start == s && lastChild.span.endExclusive == t) {
                    lastChild.values.add(value)
                    // Do NOT push duplicate interval node again.
                    continue
                }

                val node = Node<V>(Span(s, t))
                node.values.add(value)
                parent.children.add(node)
                stack.addLast(node)
            }

            return IntervalTree(root)
        }
    }
}
