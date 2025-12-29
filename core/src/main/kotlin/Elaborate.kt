package koto.core

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import koto.core.util.Diagnostic
import koto.core.util.IntervalTree
import koto.core.util.Severity
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

    data class If(
        val cond: Abstract,
        val thenBranch: Abstract,
        val elseBranch: Abstract,
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
        val result: Abstract,
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

    data class Refine(
        val name: String?,
        val base: Abstract,
        val property: Abstract,
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

    data class If(
        val cond: Value,
        val thenBranch: Value,
        val elseBranch: Value,
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
        val result: (arg: Value) -> Value,
    ) : Value

    data class Call(
        val func: Value,
        val arg: Value,
    ) : Value

    data class Pair(
        val name: String?,
        val first: Value,
        val second: (first: Value) -> Value,
    ) : Value

    data class PairOf(
        val first: Value,
        val second: Value,
    ) : Value

    data class Refine(
        val name: String?,
        val base: Value,
        val property: Value,
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
    var entries: PersistentList<Entry> = persistentListOf()
    var values: PersistentList<Lazy<Value>> = persistentListOf()
    val expectedTypes: MutableList<Pair<Span, Lazy<Abstract>>> = mutableListOf()
    val actualTypes: MutableList<Pair<Span, Lazy<Abstract>>> = mutableListOf()
    val scopes: MutableList<Pair<Span, String>> = mutableListOf()
    val diagnostics: MutableList<Diagnostic> = mutableListOf()
    val size: Level get() = entries.size.toUInt()
}

private fun ElaborateState.diagnose(
    message: String,
    span: Span,
    severity: Severity,
    expected: Value = Value.Err,
): Anno {
    diagnostics.add(Diagnostic(message, span, severity))
    return Anno(Abstract.Err, expected)
}

private fun PersistentList<Lazy<Value>>.eval(term: Abstract): Value {
    return when (term) {
        is Abstract.Type -> Value.Type
        is Abstract.Bool -> Value.Bool
        is Abstract.BoolOf -> Value.BoolOf(term.value)
        is Abstract.If -> {
            when (val cond = eval(term.cond)) {
                is Value.BoolOf -> if (cond.value) {
                    eval(term.thenBranch)
                } else {
                    eval(term.elseBranch)
                }

                else -> Value.If(
                    cond,
                    eval(term.thenBranch),
                    eval(term.elseBranch),
                )
            }
        }

        is Abstract.Int64 -> Value.Int64
        is Abstract.Int64Of -> Value.Int64Of(term.value)
        is Abstract.Float64 -> Value.Float64
        is Abstract.Float64Of -> Value.Float64Of(term.value)
        is Abstract.Let -> add(lazy { eval(term.init) }).eval(term.body)
        is Abstract.Var -> {
            val level = (lastIndex.toUInt() - term.index).toInt()
            this[level].value
        }

        is Abstract.Fun -> Value.Fun(
            term.name,
            eval(term.param),
        ) { arg -> add(lazyOf(arg)).eval(term.result) }

        is Abstract.FunOf -> Value.FunOf(
            term.name,
        ) { arg -> add(lazyOf(arg)).eval(term.result) }

        is Abstract.Call -> {
            val func = eval(term.func)
            val arg = eval(term.arg)
            when (func) {
                // is Value.FunOf -> func.result(arg)
                else -> Value.Call(func, arg)
            }
        }

        is Abstract.Pair -> Value.Pair(
            term.name,
            eval(term.first),
        ) { first -> add(lazyOf(first)).eval(term.second) }

        is Abstract.PairOf -> Value.PairOf(
            eval(term.first),
            eval(term.second),
        )

        is Abstract.Refine -> {
            val base = eval(term.base)
            when (val property = add(lazyOf(Value.Var(term.name ?: "$$this", size.toUInt()))).eval(term.property)) {
                is Value.BoolOf if property.value -> base
                else -> Value.Refine(term.name, base, property)
            }
        }

        is Abstract.Err -> Value.Err
    }
}

private fun Level.quote(value: Value): Abstract {
    return when (value) {
        is Value.Type -> Abstract.Type
        is Value.Bool -> Abstract.Bool
        is Value.BoolOf -> Abstract.BoolOf(value.value)
        is Value.If -> Abstract.If(
            quote(value.cond),
            quote(value.thenBranch),
            quote(value.elseBranch),
        )

        is Value.Int64 -> Abstract.Int64
        is Value.Int64Of -> Abstract.Int64Of(value.value)
        is Value.Float64 -> Abstract.Float64
        is Value.Float64Of -> Abstract.Float64Of(value.value)
        is Value.Fun -> Abstract.Fun(
            value.text,
            quote(value.param),
            (this + 1u).quote(value.result(Value.Var(value.text ?: "$$this", this))),
        )

        is Value.FunOf -> Abstract.FunOf(
            value.text,
            (this + 1u).quote(value.result(Value.Var(value.text, this))),
        )

        is Value.Call -> Abstract.Call(
            quote(value.func),
            quote(value.arg),
        )

        is Value.Pair -> Abstract.Pair(
            value.name,
            quote(value.first),
            (this + 1u).quote(value.second(Value.Var(value.name ?: "$$this", this)))
        )

        is Value.PairOf -> Abstract.PairOf(
            quote(value.first),
            quote(value.second),
        )

        is Value.Refine -> Abstract.Refine(
            value.name,
            quote(value.base),
            quote(value.property),
        )

        is Value.Var -> Abstract.Var(
            value.text,
            this - value.level - 1u,
        )

        is Value.Err -> Abstract.Err
    }
}

private enum class ConvResult {
    YES,
    NO,
    UNKNOWN,
}

private fun Boolean.toConvResult(): ConvResult {
    return if (this) ConvResult.YES else ConvResult.NO
}

private inline infix fun ConvResult.then(other: () -> ConvResult): ConvResult {
    return when (this) {
        ConvResult.YES -> other()
        ConvResult.NO -> ConvResult.NO
        ConvResult.UNKNOWN -> ConvResult.UNKNOWN
    }
}

private fun Level.conv(term1: Value, term2: Value): ConvResult {
    return when (term1) {
        is Value.Type if term2 is Value.Type -> ConvResult.YES
        is Value.Bool if term2 is Value.Bool -> ConvResult.YES
        is Value.BoolOf if term2 is Value.BoolOf -> (term1.value == term2.value).toConvResult()
        is Value.If if term2 is Value.If -> conv(term1.cond, term2.cond) then {
            conv(term1.thenBranch, term2.thenBranch) then {
                conv(term1.elseBranch, term2.elseBranch)
            }
        }

        is Value.Int64 if term2 is Value.Int64 -> ConvResult.YES
        is Value.Int64Of if term2 is Value.Int64Of -> (term1.value == term2.value).toConvResult()
        is Value.Float64 if term2 is Value.Float64 -> ConvResult.YES
        is Value.Float64Of if term2 is Value.Float64Of -> (term1.value == term2.value).toConvResult()
        is Value.Fun if term2 is Value.Fun -> conv(term1.param, term2.param) then {
            Value.Var("$$this", this).let { x ->
                (this + 1u).conv(term1.result(x), term2.result(x))
            }
        }

        is Value.FunOf if term2 is Value.FunOf -> Value.Var("$$this", this).let { x ->
            (this + 1u).conv(term1.result(x), term2.result(x))
        }

        is Value.Call if term2 is Value.Call -> conv(term1.func, term2.func) then { conv(term1.arg, term2.arg) }
        is Value.Pair if term2 is Value.Pair -> conv(term1.first, term2.first) then {
            Value.Var("$$this", this).let { x ->
                conv(term1.second(x), term2.second(x))
            }
        }

        is Value.PairOf if term2 is Value.PairOf -> conv(term1.first, term2.first) then { conv(term1.second, term2.second) }
        is Value.Refine if term2 is Value.Refine -> conv(term1.base, term2.base) then {
            when (conv(term1.property, term2.property)) {
                ConvResult.YES -> ConvResult.YES
                else -> ConvResult.UNKNOWN
            }
        }

        is Value.Var if term2 is Value.Var -> (term1.level == term2.level).toConvResult()
        is Value.Err -> ConvResult.YES
        else if term2 == Value.Err -> ConvResult.YES
        else if term2 is Value.Refine -> when (conv(term1, term2.base)) {
            ConvResult.YES -> ConvResult.UNKNOWN
            else -> ConvResult.NO
        }

        else -> ConvResult.NO
    }
}

private inline fun <R> ElaborateState.extend(
    name: Concrete.Ident?,
    type: Value,
    value: Lazy<Value> = lazyOf(Value.Var(name?.text ?: "$$size", size)),
    block: ElaborateState.() -> R,
): R {
    val size = size
    if (name != null) {
        actualTypes.add(name.span to lazy { size.quote(type) })
        scopes.add(name.span to name.text)
    }
    val oldEntries = entries
    val oldValues = values
    entries = entries.add(Entry(name?.text ?: "$$size", type))
    values = values.add(value)
    val result = block()
    entries = oldEntries
    values = oldValues
    return result
}

private data class Anno(
    val term: Abstract,
    val type: Value,
)

private fun ElaborateState.synth(term: Concrete): Anno {
    val entries = entries
    val values = values
    return when (term) {
        // x  ⇒  v
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
                } ?: diagnose("Unknown identifier `${term.text}`", term.span, Severity.ERROR)

                else -> {
                    val index = (entries.lastIndex - level)
                    require(index >= 0) { "de Bruijn index must be non-negative but got $index" }
                    val type = entries[level].type
                    Anno(Abstract.Var(term.text, index.toUInt()), type)
                }
            }
        }

        // let x = e e  ⇒  v
        // let x : e = e e  ⇒  v
        is Concrete.Let -> {
            val anno = term.anno?.let { anno -> check(anno, Value.Type) }
            val init = anno?.let { anno ->
                val annoV = values.eval(anno.term)
                check(term.init, annoV)
            } ?: synth(term.init)
            val body = extend(term.name, init.type, lazy { values.eval(init.term) }) {
                synth(term.body)
            }
            Anno(
                Abstract.Let(
                    term.name.text,
                    init.term,
                    body.term,
                ),
                body.type,
            )
        }

        // x : e → e  ⇒  v → v
        is Concrete.Fun if term.name != null -> {
            val param = check(term.param, Value.Type)
            val paramV = values.eval(param.term)
            val (result, resultType) = extend(term.name, paramV) {
                val result = synth(term.result)
                val resultType = size.quote(result.type)
                result to resultType
            }
            Anno(
                Abstract.FunOf(
                    term.name.text,
                    result.term,
                ),
                Value.Fun(
                    term.name.text,
                    paramV,
                ) { arg -> values.add(lazyOf(arg)).eval(resultType) },
            )
        }

        // e → e  ⇒  v
        is Concrete.Fun -> {
            // TODO: infer parameter type by unification?
            diagnose("Function parameter type annotation is required", term.span, Severity.ERROR)
        }

        // e ( e )  ⇒  v
        is Concrete.Call -> {
            val func = synth(term.func)
            when (val funcType = func.type) {
                is Value.Fun -> {
                    val arg = check(term.arg, funcType.param)
                    val argV = values.eval(arg.term)
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
                    diagnose("Expected function type, but found `$actualType`", term.func.span, Severity.ERROR)
                }
            }
        }

        // x : e , e  ⇒  type
        is Concrete.Pair if term.name != null -> {
            val first = check(term.first, Value.Type)
            val second = extend(term.name, first.type) {
                check(term.second, Value.Type)
            }
            Anno(
                Abstract.Pair(
                    term.name.text,
                    first.term,
                    second.term,
                ),
                Value.Type,
            )
        }

        // e , e  ⇒  v , v
        is Concrete.Pair -> {
            val first = synth(term.first)
            val second = synth(term.second)
            val secondType = size.quote(second.type)
            Anno(
                Abstract.PairOf(
                    first.term,
                    second.term,
                ),
                Value.Pair(
                    null,
                    first.type,
                ) { first -> values.add(lazyOf(first)).eval(secondType) },
            )
        }

        // e @ e  ⇒  type
        // x : e @ e  ⇒  type
        is Concrete.Refine -> {
            val base = check(term.base, Value.Type)
            val baseV = values.eval(base.term)
            val property = extend(term.name, baseV) {
                check(term.property, Value.Bool)
            }
            Anno(
                Abstract.Refine(
                    term.name?.text,
                    base.term,
                    property.term,
                ),
                Value.Type,
            )
        }

        // if e then e else e  ⇒  v
        is Concrete.If -> {
            val cond = check(term.cond, Value.Bool)
            val thenBranch = synth(term.thenBranch)
            val elseBranch = check(term.elseBranch, thenBranch.type)
            Anno(
                Abstract.If(
                    cond.term,
                    thenBranch.term,
                    elseBranch.term,
                ),
                thenBranch.type,
            )
        }

        is Concrete.Err -> Anno(Abstract.Err, Value.Err)
    }.also {
        actualTypes.add(term.span to lazy { size.quote(it.type) })
    }
}

private fun ElaborateState.check(term: Concrete, expected: Value): Anno {
    val _ = entries
    val values = values
    return when (term) {
        // let x = e e  ⇐  v
        // let x : e = e e  ⇐  v
        is Concrete.Let -> {
            val anno = term.anno?.let { anno -> check(anno, Value.Type) }
            val init = anno?.let { anno ->
                val annoV = values.eval(anno.term)
                check(term.init, annoV)
            } ?: synth(term.init)
            val body = extend(term.name, init.type, lazy { values.eval(init.term) }) {
                check(term.body, expected)
            }
            Anno(
                Abstract.Let(
                    term.name.text,
                    init.term,
                    body.term,
                ),
                expected,
            )
        }

        // e → e  ⇐  type
        // x : e → e  ⇐  type
        is Concrete.Fun if expected is Value.Type -> {
            val param = check(term.param, Value.Type)
            val paramV = values.eval(param.term)
            val result = extend(term.name, paramV) {
                check(term.result, Value.Type)
            }
            Anno(
                Abstract.Fun(
                    term.name?.text,
                    param.term,
                    result.term,
                ),
                expected,
            )
        }

        // x → e  ⇐  v → v
        is Concrete.Fun if term.name == null && term.param is Concrete.Ident && expected is Value.Fun -> {
            val paramV = expected.param
            val resultType = expected.result(Value.Var(term.param.text, size))
            val result = extend(term.param, paramV) {
                check(term.result, resultType)
            }
            Anno(
                Abstract.FunOf(
                    term.param.text,
                    result.term,
                ),
                expected,
            )
        }

        // e , e  ⇐  type
        // x : e , e  ⇐  type
        is Concrete.Pair if expected is Value.Type -> {
            val first = check(term.first, Value.Type)
            val second = extend(term.name, first.type) {
                check(term.second, Value.Type)
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

        // e , e  ⇐  v , v
        is Concrete.Pair if term.name == null && expected is Value.Pair -> {
            val first = check(term.first, expected.first)
            val firstV = values.eval(first.term)
            val second = check(term.second, expected.second(firstV))
            Anno(
                Abstract.PairOf(
                    first.term,
                    second.term,
                ),
                expected,
            )
        }

        // if e then e else e  ⇐  v
        is Concrete.If -> {
            val cond = check(term.cond, Value.Bool)
            val thenBranch = check(term.thenBranch, expected)
            val elseBranch = check(term.elseBranch, expected)
            Anno(
                Abstract.If(
                    cond.term,
                    thenBranch.term,
                    elseBranch.term,
                ),
                expected,
            )
        }

        // e  ⇐  v
        else -> {
            val synthesized = synth(term)
            when (size.conv(synthesized.type, expected)) {
                ConvResult.YES -> {
                    Anno(synthesized.term, expected)
                }

                ConvResult.NO -> {
                    val expectedType = stringify(size.quote(expected), 0u)
                    val actualType = stringify(size.quote(synthesized.type), 0u)
                    diagnose("Expected `${expectedType}` but found `${actualType}`", term.span, Severity.ERROR, expected)
                }

                ConvResult.UNKNOWN -> {
                    val expectedType = stringify(size.quote(expected), 0u)
                    val actualType = stringify(size.quote(synthesized.type), 0u)
                    diagnose("Expected `${expectedType}` but found `${actualType}`", term.span, Severity.WARNING, expected)
                }
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
