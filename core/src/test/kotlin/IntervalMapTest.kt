package koto.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IntervalMapTest {
    @Test
    fun `empty returns null`() {
        val m = IntervalMap<Int, String>()
        assertNull(m[-1])
        assertNull(m[0])
        assertNull(m[1])
    }

    @Test
    fun `single interval applies within and restores after endExclusive`() {
        val m = IntervalMap<Int, String>()
        m.set(1, 4, "a")

        assertNull(m[0])
        assertEquals("a", m[1])
        assertEquals("a", m[2])
        assertEquals("a", m[3])
        assertNull(m[4])
        assertNull(m[100])
    }

    @Test
    fun `overlapping set splits interval and preserves old values outside overlap`() {
        val m = IntervalMap<Int, String>()
        m.set(1, 4, "a")
        m.set(2, 3, "b")

        assertNull(m[0])
        assertEquals("a", m[1])
        assertEquals("b", m[2])
        assertEquals("a", m[3])
        assertNull(m[4])
    }

    @Test
    fun `adjacent intervals with same value merge seamlessly`() {
        val m = IntervalMap<Int, String>()
        m.set(1, 3, "a")
        m.set(3, 5, "a")

        assertNull(m[0])
        assertEquals("a", m[1])
        assertEquals("a", m[2])
        assertEquals("a", m[3])
        assertEquals("a", m[4])
        assertNull(m[5])
    }

    @Test
    fun `invalid range is a no-op`() {
        val m = IntervalMap<Int, String>()
        m.set(1, 2, "a")

        m.set(5, 5, "x")
        m.set(6, 4, "y")

        assertNull(m[0])
        assertEquals("a", m[1])
        assertNull(m[2])
    }
}
