package koto.core

import koto.core.util.IntervalTree
import koto.core.util.IntervalTree.Entry
import koto.core.util.Span
import kotlin.test.*

class IntervalTreeTest {
    @Test
    fun `empty tree returns nothing`() {
        val tree = IntervalTree.of<String>(emptyList())

        assertEquals(emptyList(), tree.getAll(0u))
        assertEquals(emptyList(), tree.getAll(123u))
        assertNull(tree.getLeaf(0u))
        assertNull(tree.getLeaf(123u))
    }

    @Test
    fun `single interval respects start inclusive and end exclusive`() {
        val tree = IntervalTree.of(listOf(Entry(Span(10u, 20u), "A")))

        assertEquals(emptyList(), tree.getAll(9u))
        assertEquals(listOf("A"), tree.getAll(10u))
        assertEquals(listOf("A"), tree.getAll(19u))
        assertEquals(emptyList(), tree.getAll(20u))

        assertNull(tree.getLeaf(9u))
        assertEquals("A", tree.getLeaf(10u)?.value)
        assertEquals("A", tree.getLeaf(19u)?.value)
        assertNull(tree.getLeaf(20u))
    }

    @Test
    fun `nested intervals return all containing values in traversal order`() {
        val tree = IntervalTree.of(
            listOf(
                Entry(Span(0u, 100u), "outer"),
                Entry(Span(10u, 20u), "inner"),
                Entry(Span(12u, 18u), "deep"),
            )
        )

        assertEquals(listOf("outer"), tree.getAll(5u))
        assertEquals("outer", tree.getLeaf(5u)?.value)

        assertEquals(listOf("outer", "inner", "deep"), tree.getAll(15u))
        assertEquals("deep", tree.getLeaf(15u)?.value)

        assertEquals(emptyList(), tree.getAll(101u))
        assertNull(tree.getLeaf(101u))
    }

    @Test
    fun `same start sorts longer interval before shorter one`() {
        val tree = IntervalTree.of(
            listOf(
                // Intentionally in reverse order to ensure the sorter decides nesting.
                Entry(Span(0u, 5u), "inner"),
                Entry(Span(0u, 10u), "outer"),
            )
        )

        assertEquals(listOf("outer", "inner"), tree.getAll(3u))
        assertEquals("inner", tree.getLeaf(3u)?.value)
        assertEquals(listOf("outer"), tree.getAll(7u))
        assertEquals("outer", tree.getLeaf(7u)?.value)
    }

    @Test
    fun `duplicate identical spans store multiple values in same node`() {
        val span = Span(10u, 20u)
        val tree = IntervalTree.of(
            listOf(
                Entry(span, 1),
                Entry(span, 2),
            )
        )

        val all = tree.getAll(15u)
        assertEquals(2, all.size)
        assertEquals(setOf(1, 2), all.toSet())
        val leaf = tree.getLeaf(15u)?.value
        assertTrue(leaf == 1 || leaf == 2)
    }

    @Test
    fun `sibling intervals and gaps behave correctly`() {
        val tree = IntervalTree.of(
            listOf(
                Entry(Span(0u, 10u), "A"),
                Entry(Span(10u, 20u), "B"),
                Entry(Span(30u, 40u), "C"),
            )
        )

        assertEquals(listOf("A"), tree.getAll(9u))
        assertEquals("A", tree.getLeaf(9u)?.value)

        assertEquals(listOf("B"), tree.getAll(10u))
        assertEquals("B", tree.getLeaf(10u)?.value)

        // Gap: no interval contains this point.
        assertEquals(emptyList(), tree.getAll(25u))
        assertNull(tree.getLeaf(25u))

        assertEquals(listOf("C"), tree.getAll(35u))
        assertEquals("C", tree.getLeaf(35u)?.value)
    }

    @Test
    fun `crossing intervals are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            IntervalTree.of(
                listOf(
                    Entry(Span(0u, 10u), "A"),
                    // Crosses A: overlaps but is not contained.
                    Entry(Span(5u, 15u), "B"),
                )
            )
        }
    }
}
