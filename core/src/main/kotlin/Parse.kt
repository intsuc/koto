package koto.core

import koto.core.util.Diagnostic
import koto.core.util.Severity
import koto.core.util.Span

sealed interface Concrete {
    val span: Span

    // x
    data class Ident(
        val text: String,
        override val span: Span,
    ) : Concrete

    // let x = e e
    // let x : e = e e
    data class Let(
        val name: Ident,
        val anno: Concrete?,
        val init: Concrete,
        val body: Concrete,
        val scope: Span,
        override val span: Span,
    ) : Concrete

    // e → e
    // x : e → e
    data class Fun(
        val name: Ident?,
        val param: Concrete,
        val result: Concrete,
        val scope: Span,
        override val span: Span,
    ) : Concrete

    // e ( e )
    data class Call(
        val func: Concrete,
        val arg: Concrete,
        override val span: Span,
    ) : Concrete

    // e , e
    // x : e , e
    data class Pair(
        val name: Ident?,
        val first: Concrete,
        val second: Concrete,
        val scope: Span,
        override val span: Span,
    ) : Concrete

    // e @ e
    // x : e @ e
    data class Refine(
        val name: Ident?,
        val base: Concrete,
        val property: Concrete,
        val scope: Span,
        override val span: Span,
    ) : Concrete

    data class Err(
        val message: String,
        override val span: Span,
    ) : Concrete
}

data class ParseResult(
    val term: Concrete,
    val lineStarts: List<UInt>,
    val diagnostics: List<Diagnostic>,
)

class ParseState(
    val text: String,
    var cursor: UInt,
) {
    val length: UInt = text.length.toUInt()
    val lineStarts: MutableList<UInt> = mutableListOf(0u)
    val diagnostics: MutableList<Diagnostic> = mutableListOf()
}

private fun ParseState.peekable(): Boolean = cursor < length

private fun ParseState.peek(): Char = text[cursor.toInt()]

private fun ParseState.skip() {
    cursor++
}

private inline fun ParseState.skipWhile(predicate: ParseState.(Char) -> Boolean) {
    while (peekable() && predicate(peek())) {
        skip()
    }
}

private fun ParseState.skipWhitespace() {
    while (peekable()) {
        when (peek()) {
            ' ', '\t' -> skip()
            '\n' -> {
                skip()
                lineStarts.add(cursor)
            }

            '\r' -> {
                skip()
                if (peekable() && peek() == '\n') {
                    skip()
                }
                lineStarts.add(cursor)
            }

            '#' -> {
                skip()
                skipWhile { it != '\n' && it != '\r' }
            }

            else -> return
        }
    }
}

private fun ParseState.diagnose(message: String, span: Span): Concrete {
    diagnostics.add(Diagnostic(message, span, Severity.ERROR))
    return Concrete.Err(message, span)
}

private fun Char.isIdent(): Boolean = when (this) {
    in 'a'..'z', in '0'..'9', '.', '-' -> true
    else -> false
}

private fun ParseState.parseWord(): Pair<String, Span> {
    val start = cursor
    skipWhile { it.isIdent() }
    val identText = text.substring(start.toInt(), cursor.toInt())
    return identText to Span(start, cursor)
}

private fun ParseState.parseIdent(): Concrete.Ident {
    val start = cursor
    skipWhile { it.isIdent() }
    if (start == cursor) {
        val _ = diagnose("Expected identifier", Span(start, cursor))
    }
    val identText = text.substring(start.toInt(), cursor.toInt())
    return Concrete.Ident(identText, Span(start, cursor))
}

private fun ParseState.parseHead(minBp: UInt): Concrete {
    if (peekable() && peek() == '(') {
        skip() // (
        skipWhitespace()
        val inner = parseAtLeast(0u)
        val start = cursor
        skipWhitespace()
        if (!peekable() || peek() != ')') {
            val _ = diagnose("Expected `)`", Span(start, start + 1u))
        } else {
            skip() // )
        }
        return inner
    }

    val (text, span) = parseWord()
    return when (text) {
        // let x = e e
        "let" -> {
            skipWhitespace()
            val name = parseIdent()
            val start = cursor
            skipWhitespace()
            val anno = if (!peekable() || peek() != ':') {
                null
            } else {
                skip() // :
                skipWhitespace()
                parseAtLeast(0u)
            }
            skipWhitespace()
            if (!peekable() || peek() != '=') {
                val _ = diagnose("Expected `=` after `let`", Span(start, start + 1u))
            } else {
                skip() // =
            }
            skipWhitespace()
            val init = parseAtLeast(0u)
            val scopeStart = cursor
            skipWhitespace()
            val body = parseAtLeast(0u)
            val scopeEnd = cursor
            Concrete.Let(
                name = name,
                anno = anno,
                init = init,
                body = body,
                scope = Span(scopeStart, scopeEnd),
                span = Span(span.start, cursor),
            )
        }

        else if text.isNotEmpty() -> {
            Concrete.Ident(text, span)
        }

        else -> {
            diagnose("Expected expression", span)
        }
    }
}

private tailrec fun ParseState.parseTail(minBp: UInt, head: Concrete): Concrete {
    skipWhitespace()

    // h ( e )
    if (peekable() && peek() == '(') {
        skip() // (
        skipWhitespace()
        val argument = parseAtLeast(0u)
        val start = cursor
        skipWhitespace()
        if (!peekable() || peek() != ')') {
            val _ = diagnose("Expected `)` after function argument", Span(start, start + 1u))
        } else {
            skip() // )
        }
        val end = cursor
        val next = Concrete.Call(
            func = head,
            arg = argument,
            span = Span(head.span.start, end),
        )
        return parseTail(minBp, next)
    }

    // h : e
    if (head is Concrete.Ident && minBp <= 20u && peekable() && peek() == ':') {
        skipWhitespace()
        skip() // :
        skipWhitespace()
        val first = parseAtLeast(21u)
        val start = cursor
        skipWhitespace()
        val scopeStart: UInt

        // h : e , e
        if (peekable() && peek() == ',') {
            skip() // ,
            scopeStart = cursor
            skipWhitespace()
            val second = parseAtLeast(10u)
            val scopeEnd = cursor
            val next = Concrete.Pair(
                name = head,
                first = first,
                second = second,
                scope = Span(scopeStart, scopeEnd),
                span = Span(head.span.start, second.span.endExclusive),
            )
            return parseTail(minBp, next)
        }

        // h : e → e
        if (peekable() && peek() == '→') {
            skip() // →
            scopeStart = cursor
            skipWhitespace()
            val result = parseAtLeast(5u)
            val scopeEnd = cursor
            val next = Concrete.Fun(
                name = head,
                param = first,
                result = result,
                scope = Span(scopeStart, scopeEnd),
                span = Span(head.span.start, result.span.endExclusive),
            )
            return parseTail(minBp, next)
        }

        // h : e @ e
        if (peekable() && peek() == '@') {
            skip() // @
            scopeStart = cursor
            skipWhitespace()
            val property = parseAtLeast(21u)
            val scopeEnd = cursor
            val next = Concrete.Refine(
                name = head,
                base = first,
                property = property,
                scope = Span(scopeStart, scopeEnd),
                span = Span(head.span.start, property.span.endExclusive),
            )
            return parseTail(minBp, next)
        }

        return diagnose("Expected `,` or `→`", Span(start - 1u, start))
    }

    // h , e
    if (minBp <= 10u && peekable() && peek() == ',') {
        skip() // ,
        skipWhitespace()
        val second = parseAtLeast(10u)
        val next = Concrete.Pair(
            name = null,
            first = head,
            second = second,
            scope = Span.ZERO,
            span = Span(head.span.start, second.span.endExclusive),
        )
        return parseTail(minBp, next)
    }

    // h → e
    if (minBp <= 5u && peekable() && peek() == '→') {
        skip() // →
        skipWhitespace()
        val result = parseAtLeast(5u)
        val next = Concrete.Fun(
            name = null,
            param = head,
            result = result,
            scope = Span.ZERO,
            span = Span(head.span.start, result.span.endExclusive),
        )
        return parseTail(minBp, next)
    }

    // h @ e
    if (minBp <= 20u && peekable() && peek() == '@') {
        skip() // @
        skipWhitespace()
        val property = parseAtLeast(21u)
        val next = Concrete.Refine(
            name = null,
            base = head,
            property = property,
            scope = Span.ZERO,
            span = Span(head.span.start, property.span.endExclusive),
        )
        return parseTail(minBp, next)
    }

    return head
}

private fun ParseState.parseAtLeast(minBp: UInt): Concrete {
    val head = parseHead(minBp)
    return parseTail(minBp, head)
}

fun parse(input: String): ParseResult {
    return ParseState(input, 0u).run {
        skipWhitespace()
        val term = parseAtLeast(0u)
        skipWhitespace()
        if (peekable()) {
            val _ = diagnose("Expected end of input", Span(cursor, cursor))
        }
        ParseResult(term, lineStarts, diagnostics)
    }
}
