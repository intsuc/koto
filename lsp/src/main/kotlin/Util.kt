package koto.lsp

import koto.core.util.Severity
import koto.core.util.Span
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import java.net.URI
import kotlin.io.path.toPath

fun Span.toRange(lineStarts: List<UInt>): Range {
    fun offsetToPosition(offset: UInt): Position {
        val index = lineStarts.binarySearch(offset)
        val line = if (index >= 0) index else -index - 2
        val lineStart = lineStarts[line]
        val character = offset - lineStart
        return Position(line, character.toInt())
    }

    val startPosition = offsetToPosition(start)
    val endPosition = offsetToPosition(endExclusive)
    return Range(startPosition, endPosition)
}

fun Position.toOffset(lineStarts: List<UInt>): UInt {
    val lineStart = lineStarts[line]
    return lineStart + character.toUInt()
}

fun Severity.toLsp(): DiagnosticSeverity {
    return when (this) {
        Severity.ERROR -> DiagnosticSeverity.Error
        Severity.WARNING -> DiagnosticSeverity.Warning
    }
}

fun normalizeUri(uri: String): String {
    return URI.create(uri).toPath().toUri().toString()
}
