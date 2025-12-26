package koto.core

import koto.core.util.Diagnostic
import koto.core.util.Span

sealed interface Concrete {
    val span: Span

    data class Ident(
        val text: String,
        override val span: Span,
    ) : Concrete

    data class Let(
        val name: Ident,
        val anno: Concrete?,
        val init: Concrete,
        val body: Concrete,
        val scope: Span,
        override val span: Span,
    ) : Concrete

    data class Fun(
        val name: Ident,
        val param: Concrete?,
        val result: Concrete?,
        val body: Concrete?,
        val scope: Span,
        override val span: Span,
    ) : Concrete {
        init {
            require(!(param == null && body == null))
            require(!(result == null && body == null))
        }
    }

    data class Call(
        val func: Concrete,
        val arg: Concrete,
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
    diagnostics.add(Diagnostic(message, span))
    return Concrete.Err(message, span)
}

private fun Char.isIdent(): Boolean = when (this) {
    in 'a'..'z', in '0'..'9', '-' -> true
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
    val (text, span) = parseWord()
    return when (text) {
        // let x = e1 e2
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
        // fun( x : a ) → b
        // fun( x ) { e }
        // fun( x ) → b { e }
        // fun( x : a ) { e }
        // fun( x : a ) → b { e }
        "fun" -> {
            var start = cursor
            if (!peekable() || peek() != '(') {
                return diagnose("Expected `(` after `fun`", Span(start, start + 1u))
            } else {
                skip() // (
            }
            skipWhitespace()
            val name = parseIdent()
            skipWhitespace()
            val param = if (!peekable() || peek() != ':') {
                null
            } else {
                skip() // :
                skipWhitespace()
                parseAtLeast(0u)
            }
            start = cursor
            skipWhitespace()
            if (!peekable() || peek() != ')') {
                return diagnose("Expected `)` after parameter type", Span(start, start + 1u))
            } else {
                skip() // )
            }
            val scopeStart = cursor
            start = cursor
            skipWhitespace()
            val result = if (peekable() && peek() == '→') {
                skip() // →
                skipWhitespace()
                parseAtLeast(0u)
            } else {
                null
            }
            var end = cursor
            skipWhitespace()
            val scopeEnd: UInt
            val body = if (peekable() && peek() == '{') {
                skip() // {
                skipWhitespace()
                val body = parseAtLeast(0u)
                start = cursor
                skipWhitespace()
                scopeEnd = cursor
                if (!peekable() || peek() != '}') {
                    val _ = diagnose("Expected `}` after function body", Span(start, start + 1u))
                } else {
                    skip() // }
                }
                end = cursor
                body
            } else if (result == null) {
                scopeEnd = cursor
                diagnose("Expected result type", Span(start, start + 1u))
            } else {
                scopeEnd = cursor
                null
            }
            Concrete.Fun(
                name = name,
                param = param,
                result = result,
                body = body,
                scope = Span(scopeStart, scopeEnd),
                span = Span(span.start, end),
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
    // h( e )
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
        val call = Concrete.Call(
            func = head,
            arg = argument,
            span = Span(head.span.start, end),
        )
        return parseTail(minBp, call)
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
