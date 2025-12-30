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

sealed interface Pattern {
    data class Var(
        val text: String,
    ) : Pattern

    data object Err : Pattern
}

sealed interface Term {
    data object Type : Term

    data object Bool : Term

    data class BoolOf(
        val value: Boolean,
    ) : Term

    data class If(
        val cond: Term,
        val thenBranch: Term,
        val elseBranch: Term,
    ) : Term

    data object Int64 : Term

    data class Int64Of(
        val value: Long,
    ) : Term

    data object Float64 : Term

    data class Float64Of(
        val value: Double,
    ) : Term

    data class Let(
        val binder: Pattern,
        val init: Term,
        val body: Term,
    ) : Term

    data class Fun(
        val binder: Pattern,
        val param: Term,
        val result: Term,
    ) : Term

    data class FunOf(
        val binder: Pattern,
        val result: Term,
    ) : Term

    data class Call(
        val func: Term,
        val arg: Term,
    ) : Term

    data class Pair(
        val binder: Pattern,
        val first: Term,
        val second: Term,
    ) : Term

    data class PairOf(
        val first: Term,
        val second: Term,
    ) : Term

    data class Refine(
        val binder: Pattern,
        val base: Term,
        val property: Term,
    ) : Term

    data class Var(
        val text: String,
        val index: Index,
    ) : Term

    data object Err : Term
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
        val binder: Pattern,
        val param: Value,
        val result: (arg: Value) -> Value,
    ) : Value

    data class FunOf(
        val binder: Pattern,
        val result: (arg: Value) -> Value,
    ) : Value

    data class Call(
        val func: Value,
        val arg: Value,
    ) : Value

    data class Pair(
        val binder: Pattern,
        val first: Value,
        val second: (first: Value) -> Value,
    ) : Value

    data class PairOf(
        val first: Value,
        val second: Value,
    ) : Value

    data class Refine(
        val binder: Pattern,
        val base: Value,
        val property: (base: Value) -> Value,
    ) : Value

    data class Var(
        val text: String,
        val level: Level,
    ) : Value

    data object Err : Value
}

data class ElaborateResult(
    val term: Term,
    val expectedTypes: IntervalTree<Lazy<Term>>,
    val actualTypes: IntervalTree<Lazy<Term>>,
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
    val expectedTypes: MutableList<Pair<Span, Lazy<Term>>> = mutableListOf()
    val actualTypes: MutableList<Pair<Span, Lazy<Term>>> = mutableListOf()
    val scopes: MutableList<Pair<Span, String>> = mutableListOf()
    val diagnostics: MutableList<Diagnostic> = mutableListOf()
    val size: Level get() = entries.size.toUInt()
}

private fun PersistentList<Lazy<Value>>.eval(term: Term): Value {
    return when (term) {
        is Term.Type -> Value.Type
        is Term.Bool -> Value.Bool
        is Term.BoolOf -> Value.BoolOf(term.value)
        is Term.If -> {
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

        is Term.Int64 -> Value.Int64
        is Term.Int64Of -> Value.Int64Of(term.value)
        is Term.Float64 -> Value.Float64
        is Term.Float64Of -> Value.Float64Of(term.value)
        is Term.Let -> add(lazy { eval(term.init) }).eval(term.body)
        is Term.Var -> {
            val level = (lastIndex.toUInt() - term.index).toInt()
            this[level].value
        }

        is Term.Fun -> Value.Fun(
            term.binder,
            eval(term.param),
        ) { arg -> add(lazyOf(arg)).eval(term.result) }

        is Term.FunOf -> Value.FunOf(
            term.binder,
        ) { arg -> add(lazyOf(arg)).eval(term.result) }

        is Term.Call -> {
            val func = eval(term.func)
            val arg = eval(term.arg)
            when (func) {
                // is Value.FunOf -> func.result(arg)
                else -> Value.Call(func, arg)
            }
        }

        is Term.Pair -> Value.Pair(
            term.binder,
            eval(term.first),
        ) { first -> add(lazyOf(first)).eval(term.second) }

        is Term.PairOf -> Value.PairOf(
            eval(term.first),
            eval(term.second),
        )

        is Term.Refine -> {
            val base = eval(term.base)
            Value.Refine(term.binder, base) { base -> add(lazyOf(base)).eval(term.property) }
        }

        is Term.Err -> Value.Err
    }
}

private fun Level.quote(value: Value): Term {
    return when (value) {
        is Value.Type -> Term.Type
        is Value.Bool -> Term.Bool
        is Value.BoolOf -> Term.BoolOf(value.value)
        is Value.If -> Term.If(
            quote(value.cond),
            quote(value.thenBranch),
            quote(value.elseBranch),
        )

        is Value.Int64 -> Term.Int64
        is Value.Int64Of -> Term.Int64Of(value.value)
        is Value.Float64 -> Term.Float64
        is Value.Float64Of -> Term.Float64Of(value.value)
        is Value.Fun -> Term.Fun(
            value.binder,
            quote(value.param),
            (this + 1u).quote(value.result(Value.Var("$$this", this))),
        )

        is Value.FunOf -> Term.FunOf(
            value.binder,
            (this + 1u).quote(value.result(Value.Var("$$this", this))),
        )

        is Value.Call -> Term.Call(
            quote(value.func),
            quote(value.arg),
        )

        is Value.Pair -> Term.Pair(
            value.binder,
            quote(value.first),
            (this + 1u).quote(value.second(Value.Var("$$this", this)))
        )

        is Value.PairOf -> Term.PairOf(
            quote(value.first),
            quote(value.second),
        )

        is Value.Refine -> Term.Refine(
            value.binder,
            quote(value.base),
            (this + 1u).quote(value.property(Value.Var("$$this", this))),
        )

        is Value.Var -> Term.Var(
            value.text,
            this - value.level - 1u,
        )

        is Value.Err -> Term.Err
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
            Value.Var("$$this", this).let { x ->
                when (conv(term1.property(x), term2.property(x))) {
                    ConvResult.YES -> ConvResult.YES
                    else -> ConvResult.UNKNOWN
                }
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

private data class Anno<out T>(
    val target: T,
    val type: Value,
)

private inline fun <R> ElaborateState.extending(block: ElaborateState.() -> R): R {
    val oldEntries = entries
    val oldValues = values
    val result = block()
    entries = oldEntries
    values = oldValues
    return result
}

private fun ElaborateState.extend(
    name: Concrete.Ident,
    type: Value,
    value: Lazy<Value> = lazyOf(Value.Var(name.text, size)),
): ElaborateState {
    return apply {
        val size = size
        actualTypes.add(name.span to lazy { size.quote(type) })
        scopes.add(name.span to name.text)
        entries = entries.add(Entry(name.text, type))
        values = values.add(value)
    }
}

private fun ElaborateState.diagnosePattern(
    message: String,
    span: Span,
    severity: Severity,
    expected: Value = Value.Err,
): Anno<Pattern> {
    diagnostics.add(Diagnostic(message, span, severity))
    return Anno(Pattern.Err, expected)
}

private fun ElaborateState.synthPattern(pattern: Concrete): Anno<Pattern> {
    return when (pattern) {
        is Concrete.Anno -> {
            val source = checkTerm(pattern.source, Value.Type)
            val sourceV = values.eval(source.target)
            val target = checkPattern(pattern.target, sourceV)
            target
        }

        else -> diagnosePattern("Type annotation is required in pattern", pattern.span, Severity.ERROR)
    }
}

private fun ElaborateState.checkPattern(pattern: Concrete, expected: Value): Anno<Pattern> {
    return when (pattern) {
        is Concrete.Ident -> Anno(Pattern.Var(pattern.text), expected)
        else -> diagnosePattern("Unsupported pattern", pattern.span, Severity.ERROR, expected)
    }
}

private fun ElaborateState.diagnoseTerm(
    message: String,
    span: Span,
    severity: Severity,
    expected: Value = Value.Err,
): Anno<Term> {
    diagnostics.add(Diagnostic(message, span, severity))
    return Anno(Term.Err, expected)
}

private fun ElaborateState.synthTerm(term: Concrete): Anno<Term> {
    val entries = entries
    val values = values
    return when (term) {
        // x  ⇒  v
        is Concrete.Ident -> when (term.text) {
            "type" -> Anno(Term.Type, Value.Type)
            "bool" -> Anno(Term.Bool, Value.Type)
            "false" -> Anno(Term.BoolOf(false), Value.Bool)
            "true" -> Anno(Term.BoolOf(true), Value.Bool)
            "int64" -> Anno(Term.Int64, Value.Type)
            "float64" -> Anno(Term.Float64, Value.Type)
            else -> when (val level = entries.indexOfLast { it.name == term.text }) {
                -1 -> term.text.toLongOrNull()?.let { value ->
                    Anno(Term.Int64Of(value), Value.Int64)
                } ?: term.text.toDoubleOrNull()?.let { value ->
                    Anno(Term.Float64Of(value), Value.Float64)
                } ?: diagnoseTerm("Unknown identifier `${term.text}`", term.span, Severity.ERROR)

                else -> {
                    val index = (entries.lastIndex - level)
                    require(index >= 0) { "de Bruijn index must be non-negative but got $index" }
                    val type = entries[level].type
                    Anno(Term.Var(term.text, index.toUInt()), type)
                }
            }
        }

        // let x = e e  ⇒  v
        // let x : e = e e  ⇒  v
        is Concrete.Let -> {
            val anno = term.anno?.let { anno -> checkTerm(anno, Value.Type) }
            val init = anno?.let { anno ->
                val annoV = values.eval(anno.term)
                checkTerm(term.init, annoV)
            } ?: synthTerm(term.init)
            val body = extend(term.name, init.type, lazy { values.eval(init.term) }) {
                synthTerm(term.body)
            }
            Anno(
                Term.Let(
                    term.name.text,
                    init.term,
                    body.target,
                ),
                body.type,
            )
        }

        // x : e → e  ⇒  v → v
        is Concrete.Fun if term.name != null -> {
            val param = checkTerm(term.param, Value.Type)
            val paramV = values.eval(param.target)
            val (result, resultType) = extend(term.name, paramV) {
                val result = synthTerm(term.result)
                val resultType = size.quote(result.type)
                result to resultType
            }
            Anno(
                Term.FunOf(
                    term.name.text,
                    result.target,
                ),
                Value.Fun(
                    null,
                    paramV,
                ) { arg -> values.add(lazyOf(arg)).eval(resultType) },
            )
        }

        // e → e  ⇒  v
        is Concrete.Fun -> {
            // TODO: infer parameter type by unification?
            diagnoseTerm("Function parameter type annotation is required", term.span, Severity.ERROR)
        }

        // e ( e )  ⇒  v
        is Concrete.Call -> {
            val func = synthTerm(term.func)
            when (val funcType = func.type) {
                is Value.Fun -> {
                    val arg = checkTerm(term.arg, funcType.param)
                    val argV = values.eval(arg.target)
                    Anno(
                        Term.Call(
                            func.target,
                            arg.target,
                        ),
                        funcType.result(argV),
                    )
                }

                else -> {
                    val _ = synthTerm(term.arg)
                    val actualType = stringify(size.quote(funcType), 0u)
                    diagnoseTerm("Expected function type, but found `$actualType`", term.func.span, Severity.ERROR)
                }
            }
        }

        // x : e , e  ⇒  type
        is Concrete.Pair if term.name != null -> {
            val first = checkTerm(term.first, Value.Type)
            val second = extend(term.name, first.type) {
                checkTerm(term.second, Value.Type)
            }
            Anno(
                Term.Pair(
                    term.name.text,
                    first.target,
                    second.target,
                ),
                Value.Type,
            )
        }

        // e , e  ⇒  v , v
        is Concrete.Pair -> {
            val first = synthTerm(term.first)
            val second = synthTerm(term.second)
            val secondType = size.quote(second.type)
            Anno(
                Term.PairOf(
                    first.target,
                    second.target,
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
            val base = checkTerm(term.base, Value.Type)
            val baseV = values.eval(base.target)
            val property = extend(term.name, baseV) {
                checkTerm(term.property, Value.Bool)
            }
            Anno(
                Term.Refine(
                    term.name?.text,
                    base.target,
                    property.target,
                ),
                Value.Type,
            )
        }

        // if e then e else e  ⇒  v
        is Concrete.If -> {
            val cond = checkTerm(term.cond, Value.Bool)
            val thenBranch = synthTerm(term.thenBranch)
            val elseBranch = checkTerm(term.elseBranch, thenBranch.type)
            Anno(
                Term.If(
                    cond.target,
                    thenBranch.target,
                    elseBranch.target,
                ),
                thenBranch.type,
            )
        }

        is Concrete.Err -> Anno(Term.Err, Value.Err)
    }.also {
        actualTypes.add(term.span to lazy { size.quote(it.type) })
    }
}

private fun ElaborateState.checkTerm(term: Concrete, expected: Value): Anno<Term> {
    val _ = entries
    val values = values
    return when (term) {
        // let x = e e  ⇐  v
        // let x : e = e e  ⇐  v
        is Concrete.Let -> {
            val anno = term.anno?.let { anno -> checkTerm(anno, Value.Type) }
            val init = anno?.let { anno ->
                val annoV = values.eval(anno.term)
                checkTerm(term.init, annoV)
            } ?: synthTerm(term.init)
            val body = extend(term.name, init.type, lazy { values.eval(init.term) }) {
                checkTerm(term.body, expected)
            }
            Anno(
                Term.Let(
                    term.name.text,
                    init.term,
                    body.target,
                ),
                expected,
            )
        }

        // e → e  ⇐  type
        // x : e → e  ⇐  type
        is Concrete.Fun if expected is Value.Type -> {
            val param = checkTerm(term.param, Value.Type)
            val paramV = values.eval(param.target)
            val result = extend(term.name, paramV) {
                checkTerm(term.result, Value.Type)
            }
            Anno(
                Term.Fun(
                    term.name?.text,
                    param.target,
                    result.target,
                ),
                expected,
            )
        }

        // x → e  ⇐  v → v
        is Concrete.Fun if term.name == null && term.param is Concrete.Ident && expected is Value.Fun -> {
            val paramV = expected.param
            val resultType = expected.result(Value.Var(term.param.text, size))
            val result = extend(term.param, paramV) {
                checkTerm(term.result, resultType)
            }
            Anno(
                Term.FunOf(
                    term.param.text,
                    result.target,
                ),
                expected,
            )
        }

        // e , e  ⇐  type
        // x : e , e  ⇐  type
        is Concrete.Pair if expected is Value.Type -> {
            val first = checkTerm(term.first, Value.Type)
            val second = extend(term.name, first.type) {
                checkTerm(term.second, Value.Type)
            }
            Anno(
                Term.Pair(
                    term.name?.text,
                    first.target,
                    second.target,
                ),
                expected,
            )
        }

        // e , e  ⇐  v , v
        is Concrete.Pair if term.name == null && expected is Value.Pair -> {
            val first = checkTerm(term.first, expected.first)
            val firstV = values.eval(first.target)
            val second = checkTerm(term.second, expected.second(firstV))
            Anno(
                Term.PairOf(
                    first.target,
                    second.target,
                ),
                expected,
            )
        }

        // if e then e else e  ⇐  v
        is Concrete.If -> {
            val cond = checkTerm(term.cond, Value.Bool)
            val thenBranch = checkTerm(term.thenBranch, expected)
            val elseBranch = checkTerm(term.elseBranch, expected)
            Anno(
                Term.If(
                    cond.target,
                    thenBranch.target,
                    elseBranch.target,
                ),
                expected,
            )
        }

        // e  ⇐  v
        else -> {
            val synthesized = synthTerm(term)
            when (size.conv(synthesized.type, expected)) {
                ConvResult.YES -> {
                    Anno(synthesized.target, expected)
                }

                ConvResult.NO -> {
                    val expectedType = stringify(size.quote(expected), 0u)
                    val actualType = stringify(size.quote(synthesized.type), 0u)
                    diagnoseTerm("Expected `${expectedType}` but found `${actualType}`", term.span, Severity.ERROR, expected)
                }

                ConvResult.UNKNOWN -> {
                    val expectedType = stringify(size.quote(expected), 0u)
                    val actualType = stringify(size.quote(synthesized.type), 0u)
                    diagnoseTerm("Expected `${expectedType}` but found `${actualType}`", term.span, Severity.WARNING, expected)
                }
            }
        }
    }.also {
        expectedTypes.add(term.span to lazy { size.quote(it.type) })
    }
}

fun elaborate(input: ParseResult): ElaborateResult {
    return ElaborateState().run {
        val term = synthTerm(input.term).target
        val expectedTypes = IntervalTree.of(expectedTypes)
        val actualTypes = IntervalTree.of(actualTypes)
        val scopes = IntervalTree.of(scopes)
        ElaborateResult(term, expectedTypes, actualTypes, scopes, diagnostics)
    }
}
