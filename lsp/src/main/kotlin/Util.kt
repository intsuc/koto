package koto.lsp

import koto.core.Abstract
import koto.core.Span
import koto.core.contains
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range

fun Span.toRange(lineStarts: List<UInt>): Range {
    fun offsetToPosition(offset: UInt): Position {
        val index = lineStarts.binarySearch(offset)
        val line = if (index >= 0) index else -index - 2
        val lineStart = lineStarts[line]
        val character = offset - lineStart
        return Position(line, character.toInt())
    }

    val startPosition = offsetToPosition(start)
    val endPosition = offsetToPosition(end)
    return Range(startPosition, endPosition)
}

fun Position.toOffset(lineStarts: List<UInt>): UInt {
    val lineStart = lineStarts[line]
    return lineStart + character.toUInt()
}

fun findNode(term: Abstract, offset: UInt): Abstract? {
    return if (offset in term.span) {
        term
    } else {
        null
    }
}
