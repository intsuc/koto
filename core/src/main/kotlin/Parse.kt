package koto.core

sealed interface Concrete {
    val span: Span

    data class Ident(
        val text: String,
        override val span: Span,
    ) : Concrete

    data class Let(
        val name: Ident,
        val init: Concrete,
        val body: Concrete,
        val scope: Span,
        override val span: Span,
    ) : Concrete

    data class Fun(
        val name: Ident,
        val param: Concrete,
        val result: Concrete,
        val body: Concrete?,
        val scope: Span,
        override val span: Span,
    ) : Concrete

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
    val ident = parseIdent()
    return when (ident.text) {
        // let x = e1 ; e2
        "let" -> {
            skipWhitespace()
            val name = parseIdent()
            skipWhitespace()
            if (!peekable() || peek() != '=') {
                val _ = diagnose("Expected `=` after `let`", ident.span)
            } else {
                skip() // =
            }
            skipWhitespace()
            val init = parseAtLeast(0u)
            skipWhitespace()
            if (!peekable() || peek() != ';') {
                val _ = diagnose("Expected `;` after let initialization", ident.span)
            } else {
                skip() // ;
            }
            val scopeStart = cursor
            skipWhitespace()
            val body = parseAtLeast(0u)
            val scopeEnd = cursor
            Concrete.Let(
                name = name,
                init = init,
                body = body,
                scope = Span(scopeStart, scopeEnd),
                span = Span(ident.span.start, cursor),
            )
        }
        // fun( x : a ) → b
        // fun( x : a ) → b { e }
        "fun" -> {
            if (!peekable() || peek() != '(') {
                return diagnose("Expected `(` after `fun`", ident.span)
            } else {
                skip() // (
            }
            skipWhitespace()
            val name = parseIdent()
            skipWhitespace()
            if (!peekable() || peek() != ':') {
                val _ = diagnose("Expected `:` after parameter name", ident.span)
            } else {
                skip() // :
            }
            skipWhitespace()
            val param = parseAtLeast(0u)
            skipWhitespace()
            if (!peekable() || peek() != ')') {
                return diagnose("Expected `)` after parameter type", ident.span)
            } else {
                skip() // )
            }
            val scopeStart = cursor
            skipWhitespace()
            if (!peekable() || peek() != '→') {
                val _ = diagnose("Expected `→` after parameter", ident.span)
            } else {
                skip() // →
            }
            skipWhitespace()
            val result = parseAtLeast(0u)
            var end = cursor
            skipWhitespace()
            val scopeEnd: UInt
            val body = if (peekable() && peek() == '{') {
                skip() // {
                skipWhitespace()
                val body = parseAtLeast(0u)
                skipWhitespace()
                scopeEnd = cursor
                if (!peekable() || peek() != '}') {
                    val _ = diagnose("Expected `}` after function body", ident.span)
                } else {
                    skip() // }
                }
                end = cursor
                body
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
                span = Span(ident.span.start, end),
            )
        }

        else -> ident
    }
}

private tailrec fun ParseState.parseTail(minBp: UInt, head: Concrete): Concrete {
    // h( e )
    if (peekable() && peek() == '(') {
        skip() // (
        skipWhitespace()
        val argument = parseAtLeast(0u)
        skipWhitespace()
        if (!peekable() || peek() != ')') {
            val _ = diagnose("Expected `)` after function argument", head.span)
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
