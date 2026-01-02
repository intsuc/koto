package koto.core

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import koto.core.util.*
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

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

    data class LetFun(
        val name: String,
        val binder: Pattern,
        val param: Term,
        val body: Term,
        val next: Term,
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

    data object Meta : Term

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

    data class Meta(
        var solution: Value?,
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
        is Term.LetFun -> {
            val func = Value.FunOf(term.binder) { arg -> add(lazyOf(arg)).eval(term.body) }
            add(lazyOf(func)).eval(term.next)
        }

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

        is Term.Meta -> Value.Meta(null)
        is Term.Err -> Value.Err
    }
}

private fun Level.quote(value: Value): Term {
    return when (val value = force(value)) {
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

        is Value.Meta -> Term.Meta

        is Value.Err -> Term.Err
    }
}

private tailrec fun force(value: Value): Value {
    return when (value) {
        is Value.Meta -> when (val solution = value.solution) {
            null -> value
            else -> force(solution)
        }

        else -> value
    }
}

/**
 * Ensures that the given value is solved.
 * Call [ensureSolved] whenever a [Value.Meta] is created to avoid global unification.
 */
private fun ElaborateState.ensureSolved(value: Value, span: Span) {
    if (force(value) is Value.Meta) {
        diagnostics.add(Diagnostic("Unsolved metavariable", span, Severity.ERROR))
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

private fun Level.conv(v1: Value, v2: Value): ConvResult {
    val v1 = force(v1)
    val v2 = force(v2)
    return when {
        v1 is Value.Type && v2 is Value.Type -> ConvResult.YES
        v1 is Value.Bool && v2 is Value.Bool -> ConvResult.YES
        v1 is Value.BoolOf && v2 is Value.BoolOf -> (v1.value == v2.value).toConvResult()
        v1 is Value.If && v2 is Value.If -> conv(v1.cond, v2.cond) then {
            conv(v1.thenBranch, v2.thenBranch) then {
                conv(v1.elseBranch, v2.elseBranch)
            }
        }

        v1 is Value.Int64 && v2 is Value.Int64 -> ConvResult.YES
        v1 is Value.Int64Of && v2 is Value.Int64Of -> (v1.value == v2.value).toConvResult()
        v1 is Value.Float64 && v2 is Value.Float64 -> ConvResult.YES
        v1 is Value.Float64Of && v2 is Value.Float64Of -> (v1.value == v2.value).toConvResult()
        v1 is Value.Fun && v2 is Value.Fun -> conv(v1.param, v2.param) then {
            Value.Var("$$this", this).let { x ->
                (this + 1u).conv(v1.result(x), v2.result(x))
            }
        }

        v1 is Value.FunOf && v2 is Value.FunOf -> Value.Var("$$this", this).let { x ->
            (this + 1u).conv(v1.result(x), v2.result(x))
        }

        v1 is Value.Call && v2 is Value.Call -> conv(v1.func, v2.func) then { conv(v1.arg, v2.arg) }
        v1 is Value.Pair && v2 is Value.Pair -> conv(v1.first, v2.first) then {
            Value.Var("$$this", this).let { x ->
                conv(v1.second(x), v2.second(x))
            }
        }

        v1 is Value.PairOf && v2 is Value.PairOf -> conv(v1.first, v2.first) then { conv(v1.second, v2.second) }
        v1 is Value.Refine && v2 is Value.Refine -> conv(v1.base, v2.base) then {
            Value.Var("$$this", this).let { x ->
                when (conv(v1.property(x), v2.property(x))) {
                    ConvResult.YES -> ConvResult.YES
                    else -> ConvResult.UNKNOWN
                }
            }
        }

        v2 is Value.Refine -> when (conv(v1, v2.base)) {
            ConvResult.YES -> ConvResult.UNKNOWN
            else -> ConvResult.NO
        }

        v1 is Value.Var && v2 is Value.Var -> (v1.level == v2.level).toConvResult()
        v1 is Value.Meta && v1 !== v2 -> {
            v1.solution = v2
            ConvResult.YES
        }

        v2 is Value.Meta && v1 !== v2 -> {
            v2.solution = v1
            ConvResult.YES
        }

        v1 == Value.Err || v2 == Value.Err -> ConvResult.YES

        else -> ConvResult.NO
    }
}

private data class Anno<out T>(
    val target: T,
    val type: Value,
)

@OptIn(ExperimentalContracts::class)
private inline fun <R> ElaborateState.extending(block: ElaborateState.() -> R): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
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
) {
    val size = size
    actualTypes.add(name.span to lazy { size.quote(type) })
    scopes.add(name.span to name.text)
    entries = entries.add(Entry(name.text, type))
    values = values.add(value)
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
        // x  ⇒  v
        is Concrete.Ident -> {
            val type = Value.Meta(null)
            extend(pattern, type)
            Anno(Pattern.Var(pattern.text), type)
        }

        // p : e  ⇒  v
        is Concrete.Anno -> {
            val source = extending {
                checkTerm(pattern.source, Value.Type)
            }
            val sourceV = values.eval(source.target)
            val target = checkPattern(pattern.target, sourceV)
            target
        }

        else -> diagnosePattern("Type annotation is required in pattern", pattern.span, Severity.ERROR)
    }
}

// TODO: support nested patterns
private fun ElaborateState.checkPattern(pattern: Concrete, expected: Value): Anno<Pattern> {
    return when (pattern) {
        // x  ⇐  v
        is Concrete.Ident -> {
            extend(pattern, expected)
            Anno(Pattern.Var(pattern.text), expected)
        }

        // p : e  ⇐  v
        else -> {
            val synthesized = synthPattern(pattern)
            when (size.conv(synthesized.type, expected)) {
                ConvResult.YES -> {
                    Anno(synthesized.target, expected)
                }

                ConvResult.NO -> {
                    val expectedType = stringify(size.quote(expected), 0u)
                    val actualType = stringify(size.quote(synthesized.type), 0u)
                    diagnosePattern("Expected `${expectedType}` but found `${actualType}`", pattern.span, Severity.ERROR, expected)
                }

                ConvResult.UNKNOWN -> {
                    val expectedType = stringify(size.quote(expected), 0u)
                    val actualType = stringify(size.quote(synthesized.type), 0u)
                    diagnosePattern("Expected `${expectedType}` but found `${actualType}`", pattern.span, Severity.WARNING, expected)
                }
            }
        }
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
        is Concrete.Let -> {
            val binder: Anno<Pattern>
            val body: Anno<Term>
            extending {
                binder = synthPattern(term.binder)
                body = synthTerm(term.body)
            }
            val init = checkTerm(term.init, binder.type)
            ensureSolved(binder.type, term.binder.span)
            Anno(
                Term.Let(
                    binder.target,
                    init.target,
                    body.target,
                ),
                body.type,
            )
        }

        // fun x(e) → e e  ⇒  v
        is Concrete.LetFun -> {
            val param: Anno<Pattern>
            val body: Anno<Term>
            val result: Anno<Term>
            val funType: Value
            extending {
                param = synthPattern(term.param)
                val values = values
                result = checkTerm(term.result, Value.Type)
                val resultV = values.eval(result.target)
                funType = Value.Fun(
                    param.target,
                    param.type,
                ) { arg -> values.add(lazyOf(arg)).eval(result.target) }
                extend(term.name, funType)
                body = checkTerm(term.body, resultV)
                ensureSolved(param.type, term.param.span)
            }
            val paramType = size.quote(param.type)
            extending {
                extend(term.name, funType)
                val next = synthTerm(term.next)
                Anno(
                    Term.LetFun(
                        term.name.text,
                        param.target,
                        paramType,
                        body.target,
                        next.target,
                    ),
                    next.type,
                )
            }
        }

        // e → e  ⇒  v → v
        is Concrete.Fun -> {
            extending {
                val param = synthPattern(term.param)
                val result = synthTerm(term.result)
                ensureSolved(param.type, term.param.span)
                val resultType = size.quote(result.type)
                Anno(
                    Term.FunOf(
                        param.target,
                        result.target,
                    ),
                    Value.Fun(
                        param.target,
                        param.type,
                    ) { arg -> values.add(lazyOf(arg)).eval(resultType) },
                )
            }
        }

        // e ( e )  ⇒  v
        is Concrete.Call -> {
            val func = synthTerm(term.func)
            when (val funcType = force(func.type)) {
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
                    Pattern.Var("$$size"),
                    first.type,
                ) { first -> values.add(lazyOf(first)).eval(secondType) },
            )
        }

        // e @ e  ⇒  type
        is Concrete.Refine -> {
            val base: Anno<Pattern>
            val property: Anno<Term>
            extending {
                base = synthPattern(term.base)
                property = checkTerm(term.property, Value.Bool)
                ensureSolved(base.type, term.base.span)
            }
            val baseType = size.quote(base.type)
            Anno(
                Term.Refine(
                    base.target,
                    baseType,
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

        is Concrete.Anno -> {
            val source = checkTerm(term.source, Value.Type)
            val sourceV = values.eval(source.target)
            val target = checkTerm(term.target, sourceV)
            target
        }

        is Concrete.Err -> Anno(Term.Err, Value.Err)
    }.also {
        actualTypes.add(term.span to lazy { size.quote(it.type) })
    }
}

private fun ElaborateState.checkTerm(term: Concrete, expected: Value): Anno<Term> {
    val expected = force(expected)
    return when (term) {
        // let x = e e  ⇐  v
        is Concrete.Let -> {
            val binder: Anno<Pattern>
            val body: Anno<Term>
            extending {
                binder = synthPattern(term.binder)
                body = checkTerm(term.body, expected)
            }
            val init = checkTerm(term.init, binder.type)
            ensureSolved(binder.type, term.binder.span)
            Anno(
                Term.Let(
                    binder.target,
                    init.target,
                    body.target,
                ),
                body.type,
            )
        }

        // fun x(e) → e e  ⇐  v
        is Concrete.LetFun -> {
            val param: Anno<Pattern>
            val body: Anno<Term>
            val result: Anno<Term>
            val funType: Value
            extending {
                param = synthPattern(term.param)
                val values = values
                result = checkTerm(term.result, Value.Type)
                val resultV = values.eval(result.target)
                funType = Value.Fun(
                    param.target,
                    param.type,
                ) { arg -> values.add(lazyOf(arg)).eval(result.target) }
                extend(term.name, funType)
                body = checkTerm(term.body, resultV)
                ensureSolved(param.type, term.param.span)
            }
            val paramType = size.quote(param.type)
            extending {
                extend(term.name, funType)
                val next = checkTerm(term.next, expected)
                Anno(
                    Term.LetFun(
                        term.name.text,
                        param.target,
                        paramType,
                        body.target,
                        next.target,
                    ),
                    expected,
                )
            }
        }

        // e → e  ⇐  type
        is Concrete.Fun if expected is Value.Type -> {
            val param: Anno<Pattern>
            val result: Anno<Term>
            extending {
                param = synthPattern(term.param)
                result = checkTerm(term.result, Value.Type)
                ensureSolved(param.type, term.param.span)
            }
            val paramType = size.quote(param.type)
            Anno(
                Term.Fun(
                    param.target,
                    paramType,
                    result.target,
                ),
                expected,
            )
        }

        // e → e  ⇐  v → v
        is Concrete.Fun if expected is Value.Fun -> {
            val resultType = expected.result(Value.Var("$$size", size))
            extending {
                val param = checkPattern(term.param, expected.param)
                val result = checkTerm(term.result, resultType)
                ensureSolved(param.type, term.param.span)
                Anno(
                    Term.FunOf(
                        param.target,
                        result.target,
                    ),
                    expected,
                )
            }
        }

        // e , e  ⇐  type
        is Concrete.Pair if expected is Value.Type -> {
            val first: Anno<Pattern>
            val second: Anno<Term>
            extending {
                first = synthPattern(term.first)
                second = checkTerm(term.second, Value.Type)
                ensureSolved(first.type, term.first.span)
            }
            val firstType = size.quote(first.type)
            Anno(
                Term.Pair(
                    first.target,
                    firstType,
                    second.target,
                ),
                expected,
            )
        }

        // e , e  ⇐  v , v
        is Concrete.Pair if expected is Value.Pair -> {
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
