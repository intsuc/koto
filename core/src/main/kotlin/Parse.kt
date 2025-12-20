package koto.core

@JvmInline
value class Span(private val value: ULong) {
    constructor(start: UInt, end: UInt) : this((start.toULong() shl 32) or end.toULong())

    val start: UInt get() = (value shr 32).toUInt()
    val end: UInt get() = (value and 0xFFFFFFFFu).toUInt()
}

sealed interface Concrete {
    val span: Span

    data class Ident(val text: String, override val span: Span) : Concrete

    data class Err(val message: String, override val span: Span) : Concrete
}

data class ParseResult(
    val term: Concrete,
    val lineStarts: List<UInt>,
    val errors: List<Concrete.Err>,
)

class ParseState(
    val text: String,
    var cursor: UInt,
) {
    val length: UInt = text.length.toUInt()
    val lineStarts: MutableList<UInt> = mutableListOf(0u)
    val errors: MutableList<Concrete.Err> = mutableListOf()
}

fun ParseState.peekable(): Boolean = cursor < length

fun ParseState.peek(): Char = text[cursor.toInt()]

inline fun ParseState.skipWhile(predicate: ParseState.(Char) -> Boolean) {
    while (peekable() && predicate(peek())) {
        cursor++
    }
}

private fun ParseState.skipWhitespace() {
    while (peekable()) {
        when (peek()) {
            ' ', '\t' -> cursor++
            '\n' -> {
                cursor++
                lineStarts.add(cursor)
            }

            '\r' -> {
                cursor++
                if (peekable() && peek() == '\n') {
                    cursor++
                }
                lineStarts.add(cursor)
            }

            else -> return
        }
    }
}

// TODO: use cursor stack to avoid nested function allocations
private inline fun <R> ParseState.span(block: ParseState.(start: UInt, until: ParseState.() -> Span) -> R): R {
    val start = cursor
    return block(cursor) { Span(start, cursor) }
}

private fun ParseState.error(message: String, span: Span): Concrete.Err {
    val err = Concrete.Err(message, span)
    errors.add(err)
    return err
}

private fun Char.isIdent(): Boolean = when (this) {
    in 'a'..'z', in '0'..'9', '-' -> true
    else -> false
}

private fun ParseState.parseIdent(): Concrete {
    return span { start, until ->
        skipWhile { it.isIdent() }
        if (start == cursor) {
            error("Expected identifier", until())
        } else {
            val identText = text.substring(start.toInt(), cursor.toInt())
            Concrete.Ident(identText, until())
        }
    }
}

private fun ParseState.parseTerm(): Concrete {
    // TODO
    return parseIdent()
}

fun parse(input: String): ParseResult {
    return ParseState(input, 0u).run {
        skipWhitespace()
        val term = parseTerm()
        skipWhitespace()
        if (peekable()) {
            val _ = error("Expected end of input", Span(cursor, cursor))
        }
        ParseResult(term, lineStarts, errors)
    }
}
