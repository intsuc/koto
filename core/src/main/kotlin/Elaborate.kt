package koto.core

import koto.core.util.Diagnostic
import koto.core.util.IntervalTree
import koto.core.util.Span

/** de Bruijn index */
typealias Index = UInt

/** de Bruijn level */
typealias Level = UInt

sealed interface Abstract {
    val span: Span

    data class Type(
        override val span: Span,
    ) : Abstract

    data class Int64(
        override val span: Span,
    ) : Abstract

    data class Int64Of(
        val value: Long,
        override val span: Span,
    ) : Abstract

    data class Let(
        val name: String,
        val init: Abstract,
        val body: Abstract,
        val scope: Span,
        override val span: Span,
    ) : Abstract

    data class Fun(
        val name: String,
        val param: Abstract,
        val result: Abstract,
        val scope: Span,
        override val span: Span,
    ) : Abstract

    data class FunOf(
        val name: String,
        val param: Abstract,
        val body: Abstract,
        val scope: Span,
        override val span: Span,
    ) : Abstract

    data class Call(
        val func: Abstract,
        val arg: Abstract,
        override val span: Span,
    ) : Abstract

    data class Var(
        val text: String,
        val index: Index,
        override val span: Span,
    ) : Abstract

    data class Err(
        val message: String,
        override val span: Span,
    ) : Abstract
}

sealed interface Value {
    data object Type : Value

    data object Int64 : Value

    data class Int64Of(
        val value: Long,
    ) : Value

    data class Fun(
        val text: String,
        val param: Value,
        val result: (arg: Value) -> Value,
    ) : Value

    data class FunOf(
        val text: String,
        val param: Value,
        val body: (arg: Value) -> Value,
    ) : Value

    data class Call(
        val func: Value,
        val arg: Value,
    ) : Value

    data class Var(
        val text: String,
        val level: Level,
    ) : Value

    data object Err : Value
}

data class ElaborateResult(
    val term: Abstract,
    val types: IntervalTree<Lazy<Abstract>>,
    val scopes: IntervalTree<String>,
    val diagnostics: List<Diagnostic>,
)

private data class Entry(
    val name: String,
    val type: Value,
)

private class ElaborateState {
    val entries: MutableList<Entry> = mutableListOf()
    val types: MutableList<Pair<Span, Lazy<Abstract>>> = mutableListOf()
    val scopes: MutableList<Pair<Span, String>> = mutableListOf()
    val diagnostics: MutableList<Diagnostic> = mutableListOf()
    val size: Level get() = entries.size.toUInt()
}

private fun ElaborateState.diagnose(message: String, span: Span): Abstract {
    diagnostics.add(Diagnostic(message, span))
    return Abstract.Err(message, span)
}

private fun ElaborateState.eval(term: Abstract): Value {
    return when (term) {
        is Abstract.Type -> Value.Type
        is Abstract.Int64 -> Value.Int64
        is Abstract.Int64Of -> Value.Int64Of(term.value)
        is Abstract.Var -> {
            val level = entries.lastIndex.toUInt() - term.index
            Value.Var(term.text, level)
        }

        is Abstract.Fun -> Value.Fun(
            term.name,
            eval(term.param),
        ) { arg -> eval(term.result) }

        is Abstract.FunOf -> Value.FunOf(
            term.name,
            eval(term.param),
        ) { arg -> eval(term.body) }

        is Abstract.Call -> {
            val func = eval(term.func)
            val arg = eval(term.arg)
            when (func) {
                is Value.FunOf -> func.body(arg)
                else -> Value.Call(func, arg)
            }
        }

        else -> Value.Err
    }
}

// TODO
private fun Level.quote(value: Value): Abstract {
    return when (value) {
        is Value.Type -> Abstract.Type(Span.ZERO)
        is Value.Int64 -> Abstract.Int64(Span.ZERO)
        is Value.Int64Of -> Abstract.Int64Of(value.value, Span.ZERO)
        is Value.Fun -> Abstract.Fun(
            value.text,
            quote(value.param),
            quote(value.result(Value.Var(value.text, this))),
            Span.ZERO,
            Span.ZERO,
        )

        is Value.FunOf -> Abstract.FunOf(
            value.text,
            quote(value.param),
            quote(value.body(Value.Var(value.text, this))),
            Span.ZERO,
            Span.ZERO,
        )

        is Value.Call -> Abstract.Call(
            quote(value.func),
            quote(value.arg),
            Span.ZERO,
        )

        is Value.Var -> Abstract.Var(
            value.text,
            this - value.level - 1u,
            Span.ZERO,
        )

        is Value.Err -> Abstract.Err("_", Span.ZERO)
    }
}

private fun Level.conv(term1: Value, term2: Value): Boolean {
    return when (term1) {
        is Value.Type if term2 is Value.Type -> true
        is Value.Int64 if term2 is Value.Int64 -> true
        is Value.Int64Of if term2 is Value.Int64Of -> term1.value == term2.value
        is Value.Fun if term2 is Value.Fun -> {
            conv(term1.param, term2.param) &&
                    Value.Var("$$this", this).let { x -> (this + 1u).conv(term1.result(x), term2.result(x)) }
        }

        is Value.FunOf if term2 is Value.FunOf -> {
            conv(term1.param, term2.param) &&
                    Value.Var("$$this", this).let { x -> (this + 1u).conv(term1.body(x), term2.body(x)) }
        }

        is Value.Call if term2 is Value.Call -> {
            conv(term1.func, term2.func) &&
                    conv(term1.arg, term2.arg)
        }

        is Value.Var if term2 is Value.Var -> term1.level == term2.level
        is Value.Err -> true
        else -> false
    }
}

private data class Anno(
    val term: Abstract,
    val type: Value,
)

private fun ElaborateState.synth(term: Concrete): Anno {
    return when (term) {
        is Concrete.Ident -> when (term.text) {
            "type" -> Anno(Abstract.Type(term.span), Value.Type)
            "int64" -> Anno(Abstract.Int64(term.span), Value.Type)
            else -> when (val level = entries.indexOfLast { it.name == term.text }) {
                -1 -> when (val value = term.text.toLongOrNull()) {
                    null -> Anno(diagnose("Unknown identifier: ${term.text}", term.span), Value.Err)
                    else -> Anno(Abstract.Int64Of(value, term.span), Value.Int64)
                }

                else -> {
                    val index = (entries.lastIndex - level).toUInt()
                    val type = entries[level].type
                    Anno(Abstract.Var(term.text, index, term.span), type)
                }
            }
        }

        is Concrete.Let -> {
            val init = synth(term.init)
            types.add(term.name.span to lazy { size.quote(init.type) })
            scopes.add(term.scope to term.name.text)
            entries.add(Entry(term.name.text, init.type))
            val body = synth(term.body)
            entries.removeAt(entries.lastIndex)
            Anno(
                Abstract.Let(
                    term.name.text,
                    init.term,
                    body.term,
                    term.scope,
                    term.span,
                ),
                body.type,
            )
        }

        is Concrete.Fun if term.result != null && term.body == null -> {
            val param = check(term.param, Value.Type)
            scopes.add(term.scope to term.name.text)
            entries.add(Entry(term.name.text, eval(param.term)))
            val result = check(term.result, Value.Type)
            entries.removeAt(entries.lastIndex)
            Anno(
                Abstract.Fun(
                    term.name.text,
                    param.term,
                    result.term,
                    term.scope,
                    term.span,
                ),
                Value.Type,
            )
        }

        is Concrete.Fun if term.result == null && term.body != null -> {
            val param = check(term.param, Value.Type)
            val paramV = eval(param.term)
            types.add(term.name.span to lazy { size.quote(paramV) })
            scopes.add(term.scope to term.name.text)
            entries.add(Entry(term.name.text, paramV))
            val body = synth(term.body)
            val result = size.quote(body.type)
            entries.removeAt(entries.lastIndex)
            Anno(
                Abstract.FunOf(
                    term.name.text,
                    param.term,
                    body.term,
                    term.scope,
                    term.span,
                ),
                Value.Fun(
                    term.name.text,
                    eval(param.term),
                ) { arg -> eval(result) },
            )
        }

        is Concrete.Fun -> {
            val param = check(term.param, Value.Type)
            val paramV = eval(param.term)
            types.add(term.name.span to lazy { size.quote(paramV) })
            scopes.add(term.scope to term.name.text)
            entries.add(Entry(term.name.text, paramV))
            val result = check(term.result!!, Value.Type)
            val body = check(term.body!!, eval(result.term))
            entries.removeAt(entries.lastIndex)
            Anno(
                Abstract.FunOf(
                    term.name.text,
                    param.term,
                    body.term,
                    term.scope,
                    term.span,
                ),
                Value.Fun(
                    term.name.text,
                    eval(param.term),
                ) { arg -> eval(result.term) },
            )
        }

        is Concrete.Call -> {
            val func = synth(term.func)
            when (val funcType = func.type) {
                is Value.Fun -> {
                    val arg = check(term.arg, funcType.param)
                    Anno(
                        Abstract.Call(
                            func.term,
                            arg.term,
                            term.span,
                        ),
                        funcType.result(arg.type),
                    )
                }

                else -> {
                    val _ = synth(term.arg)
                    val actualType = stringify(size.quote(funcType), 0u)
                    Anno(
                        diagnose("Expected type = function type\nActual type = $actualType", term.func.span),
                        Value.Err,
                    )
                }
            }
        }

        is Concrete.Err -> Anno(Abstract.Err(term.message, term.span), Value.Err)
    }.also {
        types.add(it.term.span to lazy { size.quote(it.type) })
    }
}

private fun ElaborateState.check(term: Concrete, expected: Value): Anno {
    return when (term) {
        is Concrete.Let -> {
            val init = synth(term.init)
            types.add(term.name.span to lazy { size.quote(init.type) })
            scopes.add(term.scope to term.name.text)
            entries.add(Entry(term.name.text, init.type))
            val body = check(term.body, expected)
            entries.removeAt(entries.lastIndex)
            Anno(
                Abstract.Let(
                    term.name.text,
                    init.term,
                    body.term,
                    term.scope,
                    term.span,
                ),
                expected,
            )
        }

        else -> {
            val synthesized = synth(term)
            if (size.conv(synthesized.type, expected)) {
                Anno(synthesized.term, expected)
            } else {
                val expectedType = stringify(size.quote(expected), 0u)
                val actualType = stringify(size.quote(synthesized.type), 0u)
                Anno(diagnose("Expected type = ${expectedType}\nActual type = $actualType", term.span), expected)
            }
        }
    }.also {
        types.add(it.term.span to lazy { size.quote(it.type) })
    }
}

fun elaborate(input: ParseResult): ElaborateResult {
    return ElaborateState().run {
        val term = synth(input.term).term
        val types = IntervalTree.of(types)
        val scopes = IntervalTree.of(scopes)
        ElaborateResult(term, types, scopes, diagnostics)
    }
}
