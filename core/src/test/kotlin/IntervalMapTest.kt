package koto.core

import koto.core.util.IntervalMap
import koto.core.util.Span
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IntervalMapTest {
    @Test
    fun `empty returns null`() {
        val m = IntervalMap<String>()
        assertNull(m[0u])
        assertNull(m[1u])
    }

    @Test
    fun `single interval applies within and restores after endExclusive`() {
        val m = IntervalMap<String>()
        m[Span(1u, 4u)] = "a"

        assertNull(m[0u])
        assertEquals("a", m[1u])
        assertEquals("a", m[2u])
        assertEquals("a", m[3u])
        assertNull(m[4u])
        assertNull(m[100u])
    }

    @Test
    fun `overlapping set splits interval and preserves old values outside overlap`() {
        val m = IntervalMap<String>()
        m[Span(1u, 4u)] = "a"
        m[Span(2u, 3u)] = "b"

        assertNull(m[0u])
        assertEquals("a", m[1u])
        assertEquals("b", m[2u])
        assertEquals("a", m[3u])
        assertNull(m[4u])
    }

    @Test
    fun `adjacent intervals with same value merge seamlessly`() {
        val m = IntervalMap<String>()
        m[Span(1u, 3u)] = "a"
        m[Span(3u, 5u)] = "a"

        assertNull(m[0u])
        assertEquals("a", m[1u])
        assertEquals("a", m[2u])
        assertEquals("a", m[3u])
        assertEquals("a", m[4u])
        assertNull(m[5u])
    }
}
