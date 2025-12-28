package koto.core

import koto.core.util.Diagnostic
import koto.core.util.IntervalTree
import koto.core.util.Span

/** de Bruijn index */
typealias Index = UInt

/** de Bruijn level */
typealias Level = UInt

sealed interface Abstract {
    data object Type : Abstract

    data object Bool : Abstract

    data class BoolOf(
        val value: Boolean,
    ) : Abstract

    data object Int64 : Abstract

    data class Int64Of(
        val value: Long,
    ) : Abstract

    data object Float64 : Abstract

    data class Float64Of(
        val value: Double,
    ) : Abstract

    data class Let(
        val name: String,
        val init: Abstract,
        val body: Abstract,
    ) : Abstract

    data class Fun(
        val name: String?,
        val param: Abstract,
        val result: Abstract,
    ) : Abstract

    data class FunOf(
        val name: String,
        val body: Abstract,
    ) : Abstract

    data class Call(
        val func: Abstract,
        val arg: Abstract,
    ) : Abstract

    data class Pair(
        val name: String?,
        val first: Abstract,
        val second: Abstract,
    ) : Abstract

    data class PairOf(
        val first: Abstract,
        val second: Abstract,
    ) : Abstract

    data class Var(
        val text: String,
        val index: Index,
    ) : Abstract

    data object Err : Abstract
}

sealed interface Value {
    data object Type : Value

    data object Bool : Value

    data class BoolOf(
        val value: Boolean,
    ) : Value

    data object Int64 : Value

    data class Int64Of(
        val value: Long,
    ) : Value

    data object Float64 : Value

    data class Float64Of(
        val value: Double,
    ) : Value

    data class Fun(
        val text: String?,
        val param: Value,
        val result: (arg: Value) -> Value,
    ) : Value

    data class FunOf(
        val text: String,
        val body: (arg: Value) -> Value,
    ) : Value

    data class Call(
        val func: Value,
        val arg: Value,
    ) : Value

    data class Pair(
        val name: String?,
        val first: Value,
        val second: Value,
    ) : Value

    data class PairOf(
        val first: Value,
        val second: Value,
    ) : Value

    data class Var(
        val text: String,
        val level: Level,
    ) : Value

    data object Err : Value
}

data class ElaborateResult(
    val term: Abstract,
    val expectedTypes: IntervalTree<Lazy<Abstract>>,
    val actualTypes: IntervalTree<Lazy<Abstract>>,
    val scopes: IntervalTree<String>,
    val diagnostics: List<Diagnostic>,
)

private data class Entry(
    val name: String,
    val type: Value,
)

private class ElaborateState {
    val entries: MutableList<Entry> = mutableListOf()
    val expectedTypes: MutableList<Pair<Span, Lazy<Abstract>>> = mutableListOf()
    val actualTypes: MutableList<Pair<Span, Lazy<Abstract>>> = mutableListOf()
    val scopes: MutableList<Pair<Span, String>> = mutableListOf()
    val diagnostics: MutableList<Diagnostic> = mutableListOf()
    val size: Level get() = entries.size.toUInt()
}

private fun ElaborateState.diagnose(message: String, span: Span, expected: Value = Value.Err): Anno {
    diagnostics.add(Diagnostic(message, span))
    return Anno(Abstract.Err, expected)
}

private fun ElaborateState.eval(term: Abstract): Value {
    return when (term) {
        is Abstract.Type -> Value.Type
        is Abstract.Bool -> Value.Bool
        is Abstract.BoolOf -> Value.BoolOf(term.value)
        is Abstract.Int64 -> Value.Int64
        is Abstract.Int64Of -> Value.Int64Of(term.value)
        is Abstract.Float64 -> Value.Float64
        is Abstract.Float64Of -> Value.Float64Of(term.value)
        is Abstract.Let -> {
            val init = eval(term.init)
            entries.add(Entry(term.name, init))
            val body = eval(term.body)
            entries.removeAt(entries.lastIndex)
            body
        }

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
        ) { arg -> eval(term.body) }

        is Abstract.Call -> {
            val func = eval(term.func)
            val arg = eval(term.arg)
            when (func) {
                is Value.FunOf -> func.body(arg)
                else -> Value.Call(func, arg)
            }
        }

        is Abstract.Pair -> Value.Pair(
            term.name,
            eval(term.first),
            eval(term.second),
        )

        is Abstract.PairOf -> Value.PairOf(
            eval(term.first),
            eval(term.second),
        )

        is Abstract.Err -> Value.Err
    }
}

private fun Level.quote(value: Value): Abstract {
    return when (value) {
        is Value.Type -> Abstract.Type
        is Value.Bool -> Abstract.Bool
        is Value.BoolOf -> Abstract.BoolOf(value.value)
        is Value.Int64 -> Abstract.Int64
        is Value.Int64Of -> Abstract.Int64Of(value.value)
        is Value.Float64 -> Abstract.Float64
        is Value.Float64Of -> Abstract.Float64Of(value.value)
        is Value.Fun -> Abstract.Fun(
            value.text,
            quote(value.param),
            quote(value.result(Value.Var(value.text ?: "$$this", this))),
        )

        is Value.FunOf -> Abstract.FunOf(
            value.text,
            quote(value.body(Value.Var(value.text, this))),
        )

        is Value.Call -> Abstract.Call(
            quote(value.func),
            quote(value.arg),
        )

        is Value.Pair -> Abstract.Pair(
            value.name,
            quote(value.first),
            quote(value.second),
        )

        is Value.PairOf -> Abstract.PairOf(
            quote(value.first),
            quote(value.second),
        )

        is Value.Var -> Abstract.Var(
            value.text,
            this - value.level - 1u,
        )

        is Value.Err -> Abstract.Err
    }
}

private fun Level.conv(term1: Value, term2: Value): Boolean {
    return when (term1) {
        is Value.Type if term2 is Value.Type -> true
        is Value.Bool if term2 is Value.Bool -> true
        is Value.BoolOf if term2 is Value.BoolOf -> term1.value == term2.value
        is Value.Int64 if term2 is Value.Int64 -> true
        is Value.Int64Of if term2 is Value.Int64Of -> term1.value == term2.value
        is Value.Float64 if term2 is Value.Float64 -> true
        is Value.Float64Of if term2 is Value.Float64Of -> term1.value == term2.value
        is Value.Fun if term2 is Value.Fun -> {
            conv(term1.param, term2.param) &&
                    Value.Var("$$this", this).let { x -> (this + 1u).conv(term1.result(x), term2.result(x)) }
        }

        is Value.FunOf if term2 is Value.FunOf -> {
            Value.Var("$$this", this).let { x -> (this + 1u).conv(term1.body(x), term2.body(x)) }
        }

        is Value.Call if term2 is Value.Call -> conv(term1.func, term2.func) && conv(term1.arg, term2.arg)
        is Value.Pair if term2 is Value.Pair -> conv(term1.first, term2.first) && conv(term1.second, term2.second)
        is Value.PairOf if term2 is Value.PairOf -> conv(term1.first, term2.first) && conv(term1.second, term2.second)
        is Value.Var if term2 is Value.Var -> term1.level == term2.level
        is Value.Err -> true
        else if term2 == Value.Err -> true
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
            "type" -> Anno(Abstract.Type, Value.Type)
            "bool" -> Anno(Abstract.Bool, Value.Type)
            "false" -> Anno(Abstract.BoolOf(false), Value.Bool)
            "true" -> Anno(Abstract.BoolOf(true), Value.Bool)
            "int64" -> Anno(Abstract.Int64, Value.Type)
            "float64" -> Anno(Abstract.Float64, Value.Type)
            else -> when (val level = entries.indexOfLast { it.name == term.text }) {
                -1 -> term.text.toLongOrNull()?.let { value ->
                    Anno(Abstract.Int64Of(value), Value.Int64)
                } ?: term.text.toDoubleOrNull()?.let { value ->
                    Anno(Abstract.Float64Of(value), Value.Float64)
                } ?: diagnose("Unknown identifier `${term.text}`", term.span)

                else -> {
                    val index = (entries.lastIndex - level).toUInt()
                    val type = entries[level].type
                    Anno(Abstract.Var(term.text, index), type)
                }
            }
        }

        is Concrete.Let -> {
            val anno = term.anno?.let { anno -> check(anno, Value.Type) }
            val init = anno?.let { anno ->
                val annoV = eval(anno.term)
                check(term.init, annoV)
            } ?: synth(term.init)
            actualTypes.add(term.name.span to lazy { size.quote(init.type) })
            scopes.add(term.scope to term.name.text)
            entries.add(Entry(term.name.text, init.type))
            val body = synth(term.body)
            entries.removeAt(entries.lastIndex)
            Anno(
                Abstract.Let(
                    term.name.text,
                    init.term,
                    body.term,
                ),
                body.type,
            )
        }

        // fun(x : e) → e
        is Concrete.Fun if term.param != null && term.result != null && term.body == null -> {
            val param = check(term.param, Value.Type)
            if (term.name != null) {
                scopes.add(term.scope to term.name.text)
                entries.add(Entry(term.name.text, eval(param.term)))
            }
            val result = check(term.result, Value.Type)
            if (term.name != null) {
                entries.removeAt(entries.lastIndex)
            }
            Anno(
                Abstract.Fun(
                    term.name?.text,
                    param.term,
                    result.term,
                ),
                Value.Type,
            )
        }

        // fun(x : e) { e }
        is Concrete.Fun if term.param != null && term.result == null && term.body != null -> {
            val param = check(term.param, Value.Type)
            val paramV = eval(param.term)
            requireNotNull(term.name)
            actualTypes.add(term.name.span to lazy { size.quote(paramV) })
            scopes.add(term.scope to term.name.text)
            entries.add(Entry(term.name.text, paramV))
            val body = synth(term.body)
            val result = size.quote(body.type)
            entries.removeAt(entries.lastIndex)
            Anno(
                Abstract.FunOf(
                    term.name.text,
                    body.term,
                ),
                Value.Fun(
                    term.name.text,
                    eval(param.term),
                ) { arg -> eval(result) },
            )
        }

        // fun(x : e) → e { e }
        is Concrete.Fun if term.param != null -> {
            val param = check(term.param, Value.Type)
            val paramV = eval(param.term)
            requireNotNull(term.name)
            actualTypes.add(term.name.span to lazy { size.quote(paramV) })
            scopes.add(term.scope to term.name.text)
            entries.add(Entry(term.name.text, paramV))
            val result = check(term.result!!, Value.Type)
            val body = check(term.body!!, eval(result.term))
            entries.removeAt(entries.lastIndex)
            Anno(
                Abstract.FunOf(
                    term.name.text,
                    body.term,
                ),
                Value.Fun(
                    term.name.text,
                    eval(param.term),
                ) { arg -> eval(result.term) },
            )
        }

        is Concrete.Fun -> {
            diagnose("Function parameter type annotation is required", term.span)
        }

        is Concrete.Call -> {
            val func = synth(term.func)
            when (val funcType = func.type) {
                is Value.Fun -> {
                    val arg = check(term.arg, funcType.param)
                    val argV = eval(arg.term)
                    Anno(
                        Abstract.Call(
                            func.term,
                            arg.term,
                        ),
                        funcType.result(argV),
                    )
                }

                else -> {
                    val _ = synth(term.arg)
                    val actualType = stringify(size.quote(funcType), 0u)
                    diagnose("Expected function type, but found `$actualType`", term.func.span)
                }
            }
        }

        is Concrete.Pair if term.name != null -> {
            val first = check(term.first, Value.Type)
            actualTypes.add(term.name.span to lazy { size.quote(first.type) })
            scopes.add(term.scope to term.name.text)
            entries.add(Entry(term.name.text, first.type))
            val second = check(term.second, Value.Type)
            entries.removeAt(entries.lastIndex)
            Anno(
                Abstract.Pair(
                    term.name.text,
                    first.term,
                    second.term,
                ),
                Value.Type,
            )
        }

        is Concrete.Pair -> {
            val first = synth(term.first)
            val second = synth(term.second)
            Anno(
                Abstract.PairOf(
                    first.term,
                    second.term,
                ),
                Value.Pair(
                    null,
                    first.type,
                    second.type,
                ),
            )
        }

        is Concrete.Err -> Anno(Abstract.Err, Value.Err)
    }.also {
        actualTypes.add(term.span to lazy { size.quote(it.type) })
    }
}

private fun ElaborateState.check(term: Concrete, expected: Value): Anno {
    return when (term) {
        is Concrete.Let -> {
            val anno = term.anno?.let { anno -> check(anno, Value.Type) }
            val init = anno?.let { anno ->
                val annoV = eval(anno.term)
                check(term.init, annoV)
            } ?: synth(term.init)
            actualTypes.add(term.name.span to lazy { size.quote(init.type) })
            scopes.add(term.scope to term.name.text)
            entries.add(Entry(term.name.text, init.type))
            val body = check(term.body, expected)
            entries.removeAt(entries.lastIndex)
            Anno(
                Abstract.Let(
                    term.name.text,
                    init.term,
                    body.term,
                ),
                expected,
            )
        }

        is Concrete.Fun if term.param == null && term.result == null && term.body != null && expected is Value.Fun -> {
            requireNotNull(term.name)
            actualTypes.add(term.name.span to lazy { size.quote(expected.param) })
            scopes.add(term.scope to term.name.text)
            entries.add(Entry(term.name.text, expected.param))
            val body = check(term.body, expected.result(Value.Var(term.name.text, size)))
            entries.removeAt(entries.lastIndex)
            Anno(
                Abstract.FunOf(
                    term.name.text,
                    body.term,
                ),
                expected,
            )
        }

        is Concrete.Pair if expected is Value.Type -> {
            val first = check(term.first, Value.Type)
            if (term.name != null) {
                actualTypes.add(term.name.span to lazy { size.quote(first.type) })
                scopes.add(term.scope to term.name.text)
                entries.add(Entry(term.name.text, first.type))
            }
            val second = check(term.second, Value.Type)
            if (term.name != null) {
                entries.removeAt(entries.lastIndex)
            }
            Anno(
                Abstract.Pair(
                    term.name?.text,
                    first.term,
                    second.term,
                ),
                expected,
            )
        }

        is Concrete.Pair if term.name == null && expected is Value.Pair -> {
            val first = check(term.first, expected.first)
            val second = check(term.second, expected.second)
            Anno(
                Abstract.PairOf(
                    first.term,
                    second.term,
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
                diagnose("Expected `${expectedType}` but found `${actualType}`", term.span, expected)
            }
        }
    }.also {
        expectedTypes.add(term.span to lazy { size.quote(it.type) })
    }
}

fun elaborate(input: ParseResult): ElaborateResult {
    return ElaborateState().run {
        val term = synth(input.term).term
        val expectedTypes = IntervalTree.of(expectedTypes)
        val actualTypes = IntervalTree.of(actualTypes)
        val scopes = IntervalTree.of(scopes)
        ElaborateResult(term, expectedTypes, actualTypes, scopes, diagnostics)
    }
}
