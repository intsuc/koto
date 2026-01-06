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

    // 0
    data class Int64Of(
        val value: Long,
        override val span: Span,
    ) : Concrete

    // 0.0
    data class Float64Of(
        val value: Double,
        override val span: Span,
    ) : Concrete

    // ""
    data class StrOf(
        val value: String,
        override val span: Span,
    ) : Concrete

    // let e = e e
    data class Let(
        val binder: Concrete,
        val init: Concrete,
        val next: Concrete,
        val scope: Span,
        override val span: Span,
    ) : Concrete

    // fun x(e , … , e) → e = e e
    data class LetFun(
        val name: Ident,
        val params: List<Concrete>,
        val result: Concrete,
        val body: Concrete,
        val next: Concrete,
        val bodyScope: Span,
        val nextScope: Span,
        override val span: Span,
    ) : Concrete

    // fun(e , … , e) → e
    data class Fun(
        val params: List<Concrete>,
        val result: Concrete,
        val scope: Span,
        override val span: Span,
    ) : Concrete

    // fun(e , … , e) = e
    data class FunOf(
        val params: List<Concrete>,
        val body: Concrete,
        val scope: Span,
        override val span: Span,
    ) : Concrete

    // e ( e , … , e )
    data class Call(
        val func: Concrete,
        val args: List<Concrete>,
        override val span: Span,
    ) : Concrete

    // { x = e , … , x = e }
    data class Record(
        val fields: List<Pair<Ident, Concrete>>,
        override val span: Span,
    ) : Concrete

    // e @ e
    data class Refine(
        val base: Concrete,
        val predicate: Concrete,
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

private fun ParseState.parseWord(): Pair<String, Span> {
    val start = cursor
    skipWhile {
        when (it) {
            in 'a'..'z', in '0'..'9', '-' -> true
            else -> false
        }
    }
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

    if (peekable() && peek() == '{') {
        val start = cursor
        skip() // {
        skipWhitespace()
        val fields = mutableListOf<Pair<Concrete.Ident, Concrete>>()
        while (peekable() && peek() != '}') {
            val (nameText, nameSpan) = parseWord()
            val name = Concrete.Ident(nameText, nameSpan)
            skipWhitespace()
            val colonStart = cursor
            if (!peekable() || peek() != '=') {
                val _ = diagnoseTerm("Expected `=` after record field name", Span(colonStart, colonStart + 1u))
            } else {
                skip() // =
            }
            skipWhitespace()
            val value = parseAtLeast(0u)
            fields.add(name to value)
            skipWhitespace()
            if (!peekable() || peek() != ',') {
                break
            }
            skip() // ,
            skipWhitespace()
        }
        val endStart = cursor
        if (!peekable() || peek() != '}') {
            val _ = diagnoseTerm("Expected `}`", Span(endStart, endStart + 1u))
        } else {
            skip() // }
        }
        return Concrete.Record(
            fields = fields,
            span = Span(start, cursor),
        )
    }

    if (peekable() && peek() == '"') {
        val start = cursor
        skip() // "
        val builder = StringBuilder()
        while (peekable()) {
            when (val c = peek()) {
                '"' -> {
                    skip() // "
                    break
                }

                '\n', '\r' -> {
                    diagnostics.add(Diagnostic("Unfinished string literal", Span(cursor, cursor + 1u), Severity.ERROR))
                    break
                }

                '\\' -> {
                    skip() // \
                    if (!peekable()) {
                        diagnostics.add(
                            Diagnostic(
                                "Unfinished escape sequence",
                                Span(cursor - 1u, cursor),
                                Severity.ERROR
                            )
                        )
                        break
                    }
                    when (val escaped = peek()) {
                        'n' -> {
                            builder.append('\n')
                            skip()
                        }

                        'r' -> {
                            builder.append('\r')
                            skip()
                        }

                        't' -> {
                            builder.append('\t')
                            skip()
                        }

                        '\\' -> {
                            builder.append('\\')
                            skip()
                        }

                        '"' -> {
                            builder.append('"')
                            skip()
                        }

                        else -> {
                            diagnostics.add(
                                Diagnostic(
                                    "Unknown escape sequence: \\$escaped",
                                    Span(cursor - 1u, cursor + 1u),
                                    Severity.ERROR
                                )
                            )
                            builder.append(escaped)
                            skip()
                        }
                    }
                }

                else -> {
                    builder.append(c)
                    skip()
                }
            }
        }
        return Concrete.StrOf(builder.toString(), Span(start, cursor))
    }

    if (peekable() && when (peek()) {
            in '0'..'9', '-' -> true
            else -> false
        }
    ) {
        val start = cursor
        skip()
        skipWhile {
            when (it) {
                in '0'..'9', '-', '.' -> true
                else -> false
            }
        }
        val substring = text.substring(start.toInt(), cursor.toInt())
        return substring.toLongOrNull()?.let {
            Concrete.Int64Of(it, Span(start, cursor))
        } ?: substring.toDoubleOrNull()?.let {
            Concrete.Float64Of(it, Span(start, cursor))
        } ?: Concrete.Ident(substring, Span(start, cursor))
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
                next = body,
                scope = Span(scopeStart, scopeEnd),
                span = Span(span.start, cursor),
            )
        }

        // fun( e , … , e ) → e
        // fun( e , … , e ) = e
        // fun x( e , … , e ) → e = e e
        "fun" -> {
            skipWhitespace()
            if (peekable() && peek() == '(') {
                val params = parseList(0u, '(', ')')
                skipWhitespace()
                if (peekable() && peek() == '→') {
                    skip() // →
                    val scopeStart = cursor
                    skipWhitespace()
                    val result = parseAtLeast(0u)
                    val scopeEnd = cursor
                    return Concrete.Fun(
                        params = params,
                        result = result,
                        scope = Span(scopeStart, scopeEnd),
                        span = Span(span.start, cursor),
                    )
                }
                if (!peekable() || peek() != '=') {
                    val eqStart = cursor
                    val _ = diagnoseTerm("Expected `=` after function result", Span(eqStart, eqStart + 1u))
                } else {
                    skip() // =
                }
                val scopeStart = cursor
                skipWhitespace()
                val body = parseAtLeast(0u)
                val scopeEnd = cursor
                Concrete.FunOf(
                    params = params,
                    body = body,
                    scope = Span(scopeStart, scopeEnd),
                    span = Span(span.start, cursor),
                )
            } else {
                val (nameText, nameSpan) = parseWord()
                val _ = Concrete.Ident(nameText, nameSpan)
                val params = parseList(0u, '(', ')')
                skipWhitespace()
                val arrowStart = cursor
                if (!peekable() || peek() != '→') {
                    val _ = diagnoseTerm("Expected `→` after function parameters", Span(arrowStart, arrowStart + 1u))
                } else {
                    skip() // →
                }
                skipWhitespace()
                val result = parseAtLeast(0u)
                val eqStart = cursor
                skipWhitespace()
                if (!peekable() || peek() != '=') {
                    val _ = diagnoseTerm("Expected `=` after function result", Span(eqStart, eqStart + 1u))
                } else {
                    skip() // =
                }
                val bodyScopeStart = cursor
                skipWhitespace()
                val body = parseAtLeast(0u)
                val nextScopeStart = cursor
                skipWhitespace()
                val next = parseAtLeast(0u)
                val scopeEnd = cursor
                Concrete.LetFun(
                    name = Concrete.Ident(nameText, nameSpan),
                    params = params,
                    result = result,
                    body = body,
                    next = next,
                    bodyScope = Span(bodyScopeStart, scopeEnd),
                    nextScope = Span(nextScopeStart, scopeEnd),
                    span = Span(span.start, cursor),
                )
            }
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

    // h ( e , … , e )
    if (peekable() && peek() == '(') {
        val args = parseList(0u, '(', ')')
        val next = Concrete.Call(
            func = head,
            args = args,
            span = Span(head.span.start, cursor),
        )
        return parseTail(minBp, next)
    }

    // h : e
    if (minBp <= 20u && peekable() && peek() == ':') {
        skip() // :
        skipWhitespace()
        val source = parseAtLeast(20u)
        val next = Concrete.Anno(
            target = head,
            source = source,
            span = Span(head.span.start, source.span.endExclusive),
        )
        return parseTail(minBp, next)
    }

    // h @ e
    if (minBp <= 200u && peekable() && peek() == '@') {
        skip() // @
        skipWhitespace()
        val predicate = parseAtLeast(201u)
        val next = Concrete.Refine(
            base = head,
            predicate = predicate,
            scope = Span.ZERO,
            span = Span(head.span.start, predicate.span.endExclusive),
        )
        return parseTail(minBp, next)
    }

    return head
}

private fun ParseState.parseList(minBp: UInt, prefix: Char, postfix: Char): List<Concrete> {
    val items = mutableListOf<Concrete>()
    if (!peekable() || peek() != prefix) {
        val _ = diagnoseTerm("Expected `$prefix`", Span(cursor, cursor + 1u))
    } else {
        skip() // prefix
    }
    skipWhitespace()
    while (peekable() && peek() != postfix) {
        val item = parseAtLeast(minBp)
        items.add(item)
        skipWhitespace()
        if (!peekable() || peek() != ',') {
            break
        }
        skip() // ,
        skipWhitespace()
    }
    if (!peekable() || peek() != postfix) {
        val _ = diagnoseTerm("Expected `$postfix`", Span(cursor, cursor + 1u))
    } else {
        skip() // postfix
    }
    return items
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
