package koto.core

import koto.core.util.IntervalTree
import koto.core.util.Span
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IntervalTreeTest {
    @Test
    fun `empty returns empty list`() {
        val t = IntervalTree<String>()
        assertTrue(t[0u].isEmpty())
        assertTrue(t[1u].isEmpty())
        assertTrue(t[100u].isEmpty())
    }

    @Test
    fun `single interval applies within and excludes end`() {
        val t = IntervalTree<String>()
        t[Span(1u, 4u)] = "a"

        assertEquals(emptyList(), t[0u])
        assertEquals(listOf("a"), t[1u])
        assertEquals(listOf("a"), t[2u])
        assertEquals(listOf("a"), t[3u])
        assertEquals(emptyList(), t[4u])
    }

    @Test
    fun `overlapping intervals return all matches`() {
        val t = IntervalTree<String>()
        t[Span(1u, 5u)] = "a"
        t[Span(2u, 4u)] = "b"
        t[Span(3u, 6u)] = "c"

        assertEquals(setOf("a"), t[1u].toSet())
        assertEquals(setOf("a", "b"), t[2u].toSet())
        assertEquals(setOf("a", "b", "c"), t[3u].toSet())
        assertEquals(setOf("a", "c"), t[4u].toSet())
        assertEquals(setOf("c"), t[5u].toSet())
        assertEquals(emptySet(), t[6u].toSet())
    }

    @Test
    fun `disjoint intervals across branches query correctly`() {
        val t = IntervalTree<String>()
        t[Span(0u, 2u)] = "left"
        t[Span(10u, 12u)] = "right"
        t[Span(4u, 9u)] = "middle"

        assertEquals(setOf("left"), t[0u].toSet())
        assertEquals(setOf("left"), t[1u].toSet())
        assertEquals(emptySet(), t[2u].toSet())

        assertEquals(emptySet(), t[3u].toSet())
        assertEquals(setOf("middle"), t[4u].toSet())
        assertEquals(setOf("middle"), t[8u].toSet())
        assertEquals(emptySet(), t[9u].toSet())

        assertEquals(setOf("right"), t[10u].toSet())
        assertEquals(setOf("right"), t[11u].toSet())
        assertEquals(emptySet(), t[12u].toSet())
    }

    @Test
    fun `zero-length span never matches`() {
        val t = IntervalTree<String>()
        t[Span(3u, 3u)] = "z"
        assertTrue(t[2u].isEmpty())
        assertTrue(t[3u].isEmpty())
        assertTrue(t[4u].isEmpty())
    }
}
