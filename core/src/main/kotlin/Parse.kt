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

    // let e = e e
    data class Let(
        val binder: Concrete,
        val init: Concrete,
        val body: Concrete,
        val scope: Span,
        override val span: Span,
    ) : Concrete

    // e → e
    data class Fun(
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
    data class Pair(
        val first: Concrete,
        val second: Concrete,
        val scope: Span,
        override val span: Span,
    ) : Concrete

    // e @ e
    data class Refine(
        val base: Concrete,
        val property: Concrete,
        val scope: Span,
        override val span: Span,
    ) : Concrete

    // if e then e else e
    data class If(
        val cond: Concrete,
        val thenBranch: Concrete,
        val elseBranch: Concrete,
        override val span: Span,
    ) : Concrete

    // e : e
    data class Anno(
        val target: Concrete,
        val source: Concrete,
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

private fun ParseState.diagnoseTerm(message: String, span: Span): Concrete {
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

private fun ParseState.parseHead(minBp: UInt): Concrete {
    if (peekable() && peek() == '(') {
        skip() // (
        skipWhitespace()
        val inner = parseAtLeast(0u)
        val start = cursor
        skipWhitespace()
        if (!peekable() || peek() != ')') {
            val _ = diagnoseTerm("Expected `)`", Span(start, start + 1u))
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
            val binder = parseAtLeast(0u)
            val start = cursor
            skipWhitespace()
            if (!peekable() || peek() != '=') {
                val _ = diagnoseTerm("Expected `=` after `let`", Span(start, start + 1u))
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
                binder = binder,
                init = init,
                body = body,
                scope = Span(scopeStart, scopeEnd),
                span = Span(span.start, cursor),
            )
        }

        // if e then e else e
        "if" -> {
            skipWhitespace()
            val cond = parseAtLeast(0u)
            skipWhitespace()
            val thenStart = cursor
            val thenTextSpan = parseWord()
            if (thenTextSpan.first != "then") {
                val _ = diagnoseTerm("Expected `then` after `if` condition", Span(thenStart, thenStart + 1u))
            }
            skipWhitespace()
            val thenBranch = parseAtLeast(0u)
            skipWhitespace()
            val elseStart = cursor
            val elseTextSpan = parseWord()
            if (elseTextSpan.first != "else") {
                val _ = diagnoseTerm("Expected `else` after `then` branch", Span(elseStart, elseStart + 1u))
            }
            skipWhitespace()
            val elseBranch = parseAtLeast(0u)
            Concrete.If(
                cond = cond,
                thenBranch = thenBranch,
                elseBranch = elseBranch,
                span = Span(span.start, cursor),
            )
        }

        else if text.isNotEmpty() -> {
            Concrete.Ident(text, span)
        }

        else -> {
            diagnoseTerm("Expected expression", span)
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
            val _ = diagnoseTerm("Expected `)` after function argument", Span(start, start + 1u))
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
    if (minBp <= 50u && peekable() && peek() == ':') {
        skip() // :
        skipWhitespace()
        val source = parseAtLeast(50u)
        val next = Concrete.Anno(
            target = head,
            source = source,
            span = Span(head.span.start, source.span.endExclusive),
        )
        return parseTail(minBp, next)
    }

    // h , e
    if (minBp <= 100u && peekable() && peek() == ',') {
        skip() // ,
        skipWhitespace()
        val second = parseAtLeast(100u)
        val next = Concrete.Pair(
            first = head,
            second = second,
            scope = Span.ZERO,
            span = Span(head.span.start, second.span.endExclusive),
        )
        return parseTail(minBp, next)
    }

    // h → e
    if (minBp <= 50u && peekable() && peek() == '→') {
        skip() // →
        skipWhitespace()
        val result = parseAtLeast(50u)
        val next = Concrete.Fun(
            param = head,
            result = result,
            scope = Span.ZERO,
            span = Span(head.span.start, result.span.endExclusive),
        )
        return parseTail(minBp, next)
    }

    // h @ e
    if (minBp <= 200u && peekable() && peek() == '@') {
        skip() // @
        skipWhitespace()
        val property = parseAtLeast(201u)
        val next = Concrete.Refine(
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
            val _ = diagnoseTerm("Expected end of input", Span(cursor, cursor))
        }
        ParseResult(term, lineStarts, diagnostics)
    }
}
