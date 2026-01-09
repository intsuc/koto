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

// TODO: support complex patterns
typealias Pattern = String

data class Telescope(
    val values: PersistentList<Value>,
    val binders: List<Pattern>,
    val types: List<Term>,
)

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

    data object Str : Term

    data class StrOf(
        val value: String,
    ) : Term

    data class Let(
        val binder: Pattern,
        val init: Term,
        val body: Term,
    ) : Term

    data class LetFun(
        val name: String,
        val binders: List<Pattern>,
        val params: List<Term>,
        val body: Term,
        val next: Term,
    ) : Term

    data class Fun(
        val binders: List<Pattern>,
        val params: List<Term>,
        val result: Term,
    ) : Term

    data class FunOf(
        val binders: List<Pattern>,
        val body: Term,
    ) : Term

    data class Call(
        val func: Term,
        val args: List<Term>,
    ) : Term

    data class Record(
        val fields: Map<String, Term>,
    ) : Term

    data class RecordOf(
        val fields: Map<String, Term>,
    ) : Term

    data class Access(
        val record: Term,
        val field: String,
    ) : Term

    data class Refine(
        val binder: Pattern,
        val base: Term,
        val predicate: Term,
    ) : Term

    data class Var(
        val text: String,
        val index: Index,
    ) : Term

    data class Check(
        val target: Term,
        val type: Term,
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

    data object Str : Value

    data class StrOf(
        val value: String,
    ) : Value

    data class Fun(
        val telescope: Telescope,
        val result: (args: List<Value>) -> Value,
    ) : Value

    data class FunOf(
        val binders: List<Pattern>,
        val result: (args: List<Value>) -> Value,
    ) : Value

    data class Call(
        val func: Value,
        val args: List<Value>,
    ) : Value

    // TODO: dependent record
    data class Record(
        val fields: Map<String, Value>,
    ) : Value

    data class RecordOf(
        val fields: Map<String, Value>,
    ) : Value

    data class Access(
        val record: Value,
        val field: String,
    ) : Value

    data class Refine(
        val binder: Pattern,
        val base: Value,
        val predicate: (base: Value) -> Value,
    ) : Value

    data class Var(
        val text: String,
        val level: Level,
    ) : Value

    data class Check(
        val target: Value,
        val type: Value,
    ) : Value

    data class Meta(
        var solution: Value?,
    ) : Value

    data object Err : Value
}

data class CompletionEntry(
    val name: String,
    val type: Lazy<Term>,
)

data class ElaborateResult(
    val term: Term,
    val expectedTypes: IntervalTree<Lazy<Term>>,
    val actualTypes: IntervalTree<Lazy<Term>>,
    val completionEntries: IntervalTree<CompletionEntry>,
    val diagnostics: List<Diagnostic>,
)

private data class Entry(
    val name: String,
    val type: Value,
)

private class ElaborateState {
    var entries: PersistentList<Entry> = persistentListOf()
    var values: PersistentList<Value> = persistentListOf()
    val expectedTypes: MutableList<IntervalTree.Entry<Lazy<Term>>> = mutableListOf()
    val actualTypes: MutableList<IntervalTree.Entry<Lazy<Term>>> = mutableListOf()
    val completionEntries: MutableList<IntervalTree.Entry<CompletionEntry>> = mutableListOf()
    val diagnostics: MutableList<Diagnostic> = mutableListOf()
    val size: Level get() = entries.size.toUInt()
}

private fun PersistentList<Value>.eval(term: Term): Value {
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
        is Term.Str -> Value.Str
        is Term.StrOf -> Value.StrOf(term.value)
        is Term.Let -> add(eval(term.init)).eval(term.body)
        is Term.LetFun -> {
            val func = Value.FunOf(term.binders) { args -> addAll(args).eval(term.body) }
            add(func).eval(term.next)
        }

        is Term.Var -> {
            val level = (lastIndex.toUInt() - term.index).toInt()
            requireNotNull(getOrNull(level)) {
                "unbound variable: ${term.text} at index ${term.index}"
            }
        }

        is Term.Fun -> Value.Fun(
            Telescope(this, term.binders, term.params),
        ) { args -> addAll(args).eval(term.result) }

        is Term.FunOf -> Value.FunOf(
            term.binders,
        ) { args -> addAll(args).eval(term.body) }

        is Term.Call -> {
            val func = eval(term.func)
            val args = term.args.map { arg -> eval(arg) }
            when (func) {
                // is Value.FunOf -> func.result(arg)
                else -> Value.Call(func, args)
            }
        }

        is Term.Record -> {
            val fields = term.fields.mapValues { (_, value) -> eval(value) }
            Value.Record(fields)
        }

        is Term.RecordOf -> {
            val fields = term.fields.mapValues { (_, value) -> eval(value) }
            Value.RecordOf(fields)
        }

        is Term.Access -> {
            when (val record = eval(term.record)) {
                is Value.RecordOf -> record.fields[term.field] ?: Value.Err
                else -> Value.Access(record, term.field)
            }
        }

        is Term.Refine -> {
            val base = eval(term.base)
            Value.Refine(term.binder, base) { base -> add(base).eval(term.predicate) }
        }

        is Term.Check -> Value.Check(
            eval(term.target),
            eval(term.type),
        )

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
        is Value.Str -> Term.Str
        is Value.StrOf -> Term.StrOf(value.value)
        is Value.Fun -> {
            var args = persistentListOf<Value>()
            val params = value.telescope.binders.zip(value.telescope.types).map { (binder, param) ->
                val paramV = value.telescope.values.addAll(args).eval(param)
                val level = this + args.size.toUInt()
                val param = level.quote(paramV)
                args = args.add(Value.Var(binder, level))
                param
            }
            val result = (this + value.telescope.binders.size.toUInt()).quote(value.result(args))
            Term.Fun(
                value.telescope.binders,
                params,
                result,
            )
        }

        is Value.FunOf -> {
            val result =
                (this + value.binders.size.toUInt()).quote(value.result((0u until value.binders.size.toUInt()).map { i ->
                    Value.Var(value.binders[i.toInt()], this + i)
                }))
            Term.FunOf(
                value.binders,
                result,
            )
        }

        is Value.Call -> Term.Call(
            quote(value.func),
            value.args.map { arg -> quote(arg) },
        )

        is Value.Record -> Term.Record(
            value.fields.mapValues { (_, v) -> quote(v) },
        )

        is Value.RecordOf -> Term.RecordOf(
            value.fields.mapValues { (_, v) -> quote(v) },
        )

        is Value.Access -> Term.Access(
            quote(value.record),
            value.field,
        )

        is Value.Refine -> Term.Refine(
            value.binder,
            quote(value.base),
            (this + 1u).quote(value.predicate(Value.Var(value.binder, this))),
        )

        is Value.Var -> Term.Var(
            value.text,
            this - value.level - 1u,
        )

        is Value.Check -> Term.Check(
            quote(value.target),
            quote(value.type),
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

private fun Level.convZip(vs1: List<Value>, vs2: List<Value>): ConvResult {
    if (vs1.size != vs2.size) return ConvResult.NO
    var result = ConvResult.YES
    for (i in vs1.indices) {
        result = result then {
            conv(vs1[i], vs2[i])
        }
        if (result != ConvResult.YES) break
    }
    return result
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
        v1 is Value.Str && v2 is Value.Str -> ConvResult.YES
        v1 is Value.StrOf && v2 is Value.StrOf -> (v1.value == v2.value).toConvResult()
        v1 is Value.Fun && v2 is Value.Fun -> {
            if (v1.telescope.binders.size != v2.telescope.binders.size) return ConvResult.NO
            var args = persistentListOf<Value>()
            var result = ConvResult.YES
            for (i in v1.telescope.types.indices) {
                result = result then {
                    val param1 = v1.telescope.values.addAll(args).eval(v1.telescope.types[i])
                    val param2 = v2.telescope.values.addAll(args).eval(v2.telescope.types[i])
                    conv(param1, param2)
                }
                if (result != ConvResult.YES) return result
                args = args.add(Value.Var("$$this", this + i.toUInt()))
            }
            result then {
                (this + v1.telescope.binders.size.toUInt()).conv(v1.result(args), v2.result(args))
            }
        }

        v1 is Value.FunOf && v2 is Value.FunOf -> {
            val args = (0u until v1.binders.size.toUInt()).map { i ->
                Value.Var("$$this", this + i)
            }
            (this + v1.binders.size.toUInt()).conv(v1.result(args), v2.result(args))
        }

        v1 is Value.Call && v2 is Value.Call -> conv(v1.func, v2.func) then { convZip(v1.args, v2.args) }
        v1 is Value.Record && v2 is Value.Record -> {
            if (v1.fields.keys != v2.fields.keys) return ConvResult.NO
            var result = ConvResult.YES
            for (key in v1.fields.keys) {
                result = result then {
                    conv(v1.fields[key]!!, v2.fields[key]!!)
                }
                if (result != ConvResult.YES) break
            }
            result
        }

        v1 is Value.RecordOf && v2 is Value.RecordOf -> {
            if (v1.fields.keys != v2.fields.keys) return ConvResult.NO
            var result = ConvResult.YES
            for (key in v1.fields.keys) {
                result = result then {
                    conv(v1.fields[key]!!, v2.fields[key]!!)
                }
                if (result != ConvResult.YES) break
            }
            result
        }

        v1 is Value.Access && v2 is Value.Access -> (v1.field == v2.field).toConvResult() then {
            conv(v1.record, v2.record)
        }

        v1 is Value.Refine && v2 is Value.Refine -> conv(v1.base, v2.base) then {
            Value.Var("$$this", this).let { x ->
                when (conv(v1.predicate(x), v2.predicate(x))) {
                    ConvResult.YES -> ConvResult.YES
                    else -> ConvResult.UNKNOWN
                }
            }
        }

        v2 is Value.Refine -> when (conv(v1, v2.base)) {
            ConvResult.YES -> when (val predicate = v2.predicate(Value.Var("$$this", this))) {
                is Value.BoolOf if predicate.value -> ConvResult.YES
                else -> ConvResult.UNKNOWN
            }

            else -> ConvResult.NO
        }

        v1 is Value.Var && v2 is Value.Var -> (v1.level == v2.level).toConvResult()
        v1 is Value.Check && v2 is Value.Check -> conv(v1.target, v2.target) then {
            conv(v1.type, v2.type)
        }

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
    name: String,
    nameSpan: Span,
    scope: Span,
    type: Value,
    value: Value = Value.Var(name, size),
) {
    val size = size
    val quotedType = lazy { size.quote(type) }
    actualTypes.add(IntervalTree.Entry(nameSpan, quotedType))
    completionEntries.add(IntervalTree.Entry(scope, CompletionEntry(name, quotedType)))
    entries = entries.add(Entry(name, type))
    values = values.add(value)
}

private fun ElaborateState.diagnosePattern(
    message: String,
    span: Span,
    severity: Severity,
    expected: Value = Value.Err,
    pattern: Pattern = "$$size",
): Anno<Pattern> {
    diagnostics.add(Diagnostic(message, span, severity))
    return Anno(pattern, expected)
}

private fun ElaborateState.synthPattern(pattern: Concrete): Anno<Pattern> {
    return when (pattern) {
        // x  ⇒  v
        is Concrete.Ident -> {
            val type = Value.Meta(null)
            Anno(pattern.text, type)
        }

        // p : e  ⇒  v
        is Concrete.Anno -> {
            val source = extending {
                checkTerm(pattern.source, Value.Type)
            }
            val sourceV = values.eval(source.target)
            val target = synthPattern(pattern.target)
            Anno(target.target, sourceV)
        }

        else -> diagnosePattern("Unsupported pattern", pattern.span, Severity.ERROR)
    }
}

private fun ElaborateState.checkPattern(pattern: Concrete, expected: Value): Anno<Pattern> {
    return when (pattern) {
        // x  ⇐  v
        is Concrete.Ident -> {
            Anno(pattern.text, expected)
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
                    diagnosePattern(
                        "Expected `${expectedType}` but found `${actualType}`",
                        pattern.span,
                        Severity.ERROR,
                        expected
                    )
                }

                ConvResult.UNKNOWN -> {
                    val expectedType = stringify(size.quote(expected), 0u)
                    val actualType = stringify(size.quote(synthesized.type), 0u)
                    diagnosePattern(
                        "Expected `${expectedType}` but found `${actualType}`",
                        pattern.span,
                        Severity.WARNING,
                        expected,
                        synthesized.target
                    )
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
    term: Term = Term.Err,
): Anno<Term> {
    diagnostics.add(Diagnostic(message, span, severity))
    return Anno(term, expected)
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
            "str" -> Anno(Term.Str, Value.Type)
            else -> when (val level = entries.indexOfLast { it.name == term.text }) {
                -1 -> diagnoseTerm("Unknown identifier `${term.text}`", term.span, Severity.ERROR)
                else -> {
                    val index = (entries.lastIndex - level)
                    require(index >= 0) { "de Bruijn index must be non-negative but got $index" }
                    val type = entries[level].type
                    Anno(Term.Var(term.text, index.toUInt()), type)
                }
            }
        }

        // 0  ⇒  int64
        is Concrete.Int64Of -> Anno(Term.Int64Of(term.value), Value.Int64)

        // 0.0  ⇒  float64
        is Concrete.Float64Of -> Anno(Term.Float64Of(term.value), Value.Float64)

        // ""  ⇒  str
        is Concrete.StrOf -> Anno(Term.StrOf(term.value), Value.Str)

        // let x = e e  ⇒  v
        is Concrete.Let -> {
            val binder = synthPattern(term.binder)
            val init = checkTerm(term.init, binder.type)
            val initV = values.eval(init.target)
            extending {
                extend(binder.target, term.binder.span, term.scope, binder.type, initV)
                val body = synthTerm(term.next)
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
        }

        // fun x( e , … , e ) → e = e e  ⇒  v
        is Concrete.LetFun -> {
            val binders = mutableListOf<Pattern>()
            val params = mutableListOf<Term>()
            val paramsV = mutableListOf<Value>()
            val body: Anno<Term>
            val funType: Value
            extending {
                val values1 = values
                for (param in term.params) {
                    val param1 = synthPattern(param)
                    binders.add(param1.target)
                    params.add(size.quote(param1.type))
                    paramsV.add(param1.type)
                    extend(param1.target, param.span, term.bodyScope, param1.type)
                }
                val result = checkTerm(term.result, Value.Type)
                val resultV = values.eval(result.target)
                funType = Value.Fun(
                    Telescope(values1, binders, params),
                ) { args -> values1.addAll(args).eval(result.target) }
                extend(term.name.text, term.name.span, term.bodyScope, funType)
                body = checkTerm(term.body, resultV)
                for ((paramV, param) in paramsV zip term.params) {
                    ensureSolved(paramV, param.span)
                }
            }
            extending {
                extend(term.name.text, term.name.span, term.nextScope, funType)
                val next = synthTerm(term.next)
                Anno(
                    Term.LetFun(
                        term.name.text,
                        binders,
                        params,
                        body.target,
                        next.target,
                    ),
                    next.type,
                )
            }
        }

        // fun( e, … , e ) → e  ⇒  type
        is Concrete.Fun -> {
            extending {
                val binders = mutableListOf<Pattern>()
                val params = mutableListOf<Term>()
                val paramsV = mutableListOf<Value>()
                for (param in term.params) {
                    val param1 = synthPattern(param)
                    binders.add(param1.target)
                    params.add(size.quote(param1.type))
                    paramsV.add(param1.type)
                    extend(param1.target, param.span, term.scope, param1.type)
                }
                val result = checkTerm(term.result, Value.Type)
                for ((paramV, param) in paramsV zip term.params) {
                    ensureSolved(paramV, param.span)
                }
                Anno(
                    Term.Fun(
                        binders,
                        params,
                        result.target,
                    ),
                    Value.Type,
                )
            }
        }

        // fun( e , … , e ) = e  ⇒  v → v
        is Concrete.FunOf -> {
            extending {
                val values = values
                val binders = mutableListOf<Pattern>()
                val params = mutableListOf<Term>()
                val paramsV = mutableListOf<Value>()
                for (param in term.params) {
                    val param1 = synthPattern(param)
                    binders.add(param1.target)
                    params.add(size.quote(param1.type))
                    paramsV.add(param1.type)
                    extend(param1.target, param.span, term.scope, param1.type)
                }
                val body = synthTerm(term.body)
                for ((param1, param) in paramsV zip term.params) {
                    ensureSolved(param1, param.span)
                }
                val resultType = size.quote(body.type)
                Anno(
                    Term.FunOf(
                        binders,
                        body.target,
                    ),
                    Value.Fun(
                        Telescope(values, binders, params),
                    ) { args -> values.addAll(args).eval(resultType) },
                )
            }
        }

        // e ( e , … , e )  ⇒  v
        is Concrete.Call -> {
            val func = synthTerm(term.func)
            when (val funcType = force(func.type)) {
                is Value.Fun -> {
                    val args = mutableListOf<Term>()
                    val argsV = mutableListOf<Value>()
                    var paramValues = funcType.telescope.values
                    for ((arg, param) in term.args zip funcType.telescope.types) {
                        val paramV = paramValues.eval(param)
                        val arg1 = checkTerm(arg, paramV)
                        args.add(arg1.target)
                        val argV = values.eval(arg1.target)
                        argsV.add(argV)
                        paramValues = paramValues.add(argV)
                    }
                    if (term.args.size < funcType.telescope.types.size) {
                        val _ = diagnoseTerm("Too few arguments", term.span, Severity.ERROR)
                        repeat(funcType.telescope.types.size - term.args.size) {
                            args.add(Term.Err)
                            argsV.add(Value.Err)
                        }
                    }
                    val resultType = funcType.result(argsV)
                    Anno(
                        Term.Call(
                            func.target,
                            args,
                        ),
                        resultType,
                    )
                }

                else -> {
                    for (arg in term.args) {
                        val _ = synthTerm(arg)
                    }
                    val actualType = stringify(size.quote(funcType), 0u)
                    diagnoseTerm("Expected function type, but found `$actualType`", term.func.span, Severity.ERROR)
                }
            }
        }

        // { x = e , … , x = e }  ⇒  { x = v , … , x = v }
        is Concrete.Record -> {
            val fields = mutableMapOf<String, Term>()
            val fieldsV = mutableMapOf<String, Value>()
            for ((name, value) in term.fields) {
                val value1 = synthTerm(value)
                fields[name.text] = value1.target
                fieldsV[name.text] = value1.type
            }
            Anno(
                Term.RecordOf(fields),
                Value.Record(fieldsV),
            )
        }

        // e . x  ⇒  v
        is Concrete.Access -> {
            val record = synthTerm(term.record)
            when (val recordType = force(record.type)) {
                is Value.Record -> {
                    when (val fieldType = recordType.fields[term.field.text]) {
                        null -> diagnoseTerm("Unknown field `${term.field.text}`", term.field.span, Severity.ERROR)
                        else -> Anno(
                            Term.Access(
                                record.target,
                                term.field.text,
                            ),
                            fieldType,
                        )
                    }
                }

                else -> {
                    val actualType = stringify(size.quote(recordType), 0u)
                    diagnoseTerm("Expected record type, but found `$actualType`", term.record.span, Severity.ERROR)
                }
            }
        }

        // e @ e  ⇒  type
        is Concrete.Refine -> {
            val base: Anno<Pattern>
            val predicate: Anno<Term>
            extending {
                base = synthPattern(term.base)
                extend(base.target, term.base.span, term.scope, base.type)
                predicate = checkTerm(term.predicate, Value.Bool)
                ensureSolved(base.type, term.base.span)
            }
            val baseType = size.quote(base.type)
            Anno(
                Term.Refine(
                    base.target,
                    baseType,
                    predicate.target,
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
        actualTypes.add(IntervalTree.Entry(term.span, lazy { size.quote(it.type) }))
    }
}

private fun ElaborateState.checkTerm(term: Concrete, expected: Value): Anno<Term> {
    val expected = force(expected)
    return when (term) {
        // let x = e e  ⇐  v
        is Concrete.Let -> {
            val binder = synthPattern(term.binder)
            val init = checkTerm(term.init, binder.type)
            val initV = values.eval(init.target)
            extending {
                extend(binder.target, term.binder.span, term.scope, binder.type, initV)
                val body = checkTerm(term.next, expected)
                ensureSolved(binder.type, term.binder.span)
                Anno(
                    Term.Let(
                        binder.target,
                        init.target,
                        body.target,
                    ),
                    expected,
                )
            }
        }

        // fun x( e, … , e ) → e = e e  ⇐  v
        is Concrete.LetFun -> {
            val binders = mutableListOf<Pattern>()
            val params = mutableListOf<Term>()
            val paramsV = mutableListOf<Value>()
            val body: Anno<Term>
            val funType: Value
            extending {
                val values1 = values
                for (param in term.params) {
                    val param1 = synthPattern(param)
                    binders.add(param1.target)
                    params.add(size.quote(param1.type))
                    paramsV.add(param1.type)
                    extend(param1.target, param.span, term.bodyScope, param1.type)
                }
                val result = checkTerm(term.result, Value.Type)
                val resultV = values.eval(result.target)
                funType = Value.Fun(
                    Telescope(values1, binders, params),
                ) { args -> values1.addAll(args).eval(result.target) }
                extend(term.name.text, term.name.span, term.bodyScope, funType)
                body = checkTerm(term.body, resultV)
                for ((paramV, param) in paramsV zip term.params) {
                    ensureSolved(paramV, param.span)
                }
            }
            extending {
                extend(term.name.text, term.name.span, term.nextScope, funType)
                val next = checkTerm(term.next, expected)
                Anno(
                    Term.LetFun(
                        term.name.text,
                        binders,
                        params,
                        body.target,
                        next.target,
                    ),
                    next.type,
                )
            }
        }

        // fun( e , … , e ) = e  ⇐  v → v
        is Concrete.FunOf if expected is Value.Fun && term.params.size == expected.telescope.binders.size -> {
            extending {
                val binders = mutableListOf<Pattern>()
                val params = mutableListOf<Value>()
                var args = persistentListOf<Value>()
                for ((param, paramType) in term.params zip expected.telescope.types) {
                    val paramTypeV = expected.telescope.values.addAll(args).eval(paramType)
                    val param1 = checkPattern(param, paramTypeV)
                    binders.add(param1.target)
                    params.add(param1.type)
                    val level = expected.telescope.values.size.toUInt() + args.size.toUInt()
                    args = args.add(Value.Var(param1.target, level))
                    extend(param1.target, param.span, term.scope, paramTypeV)
                }
                val resultType = expected.result(args)
                val result = checkTerm(term.body, resultType)
                for ((param1, param) in params zip term.params) {
                    ensureSolved(param1, param.span)
                }
                Anno(
                    Term.FunOf(
                        binders,
                        result.target,
                    ),
                    expected,
                )
            }
        }

        // { x = e , … , x = e }  ⇐  type
        is Concrete.Record if expected is Value.Type -> {
            val fields = mutableMapOf<String, Term>()
            for ((name, value) in term.fields) {
                val value1 = checkTerm(value, Value.Type)
                fields[name.text] = value1.target
            }
            Anno(
                Term.Record(fields),
                expected,
            )
        }

        // { x = e , … , x = e }  ⇐  { x = v , … , x = v }
        is Concrete.Record if expected is Value.Record && term.fields.size >= expected.fields.keys.size -> {
            val fields = mutableMapOf<String, Term>()
            for ((name, value) in term.fields) {
                val fieldType = expected.fields[name.text] ?: run {
                    val _ = diagnoseTerm("Unexpected field `${name.text}`", name.span, Severity.ERROR)
                    Value.Err
                }
                val value1 = checkTerm(value, fieldType)
                fields[name.text] = value1.target
            }
            Anno(
                Term.RecordOf(fields),
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
                    diagnoseTerm(
                        "Expected `${expectedType}` but found `${actualType}`",
                        term.span,
                        Severity.ERROR,
                        expected
                    )
                }

                ConvResult.UNKNOWN -> {
                    val expectedType = stringify(size.quote(expected), 0u)
                    val actualType = stringify(size.quote(synthesized.type), 0u)
                    val _ = diagnoseTerm(
                        "Expected `${expectedType}` but found `${actualType}`",
                        term.span,
                        Severity.WARNING,
                        expected,
                        synthesized.target
                    )
                    Anno(
                        Term.Check(
                            synthesized.target,
                            size.quote(expected),
                        ),
                        expected,
                    )
                }
            }
        }
    }.also {
        expectedTypes.add(IntervalTree.Entry(term.span, lazy { size.quote(it.type) }))
    }
}

fun elaborate(input: ParseResult): ElaborateResult {
    return ElaborateState().run {
        val term = synthTerm(input.term).target
        val expectedTypes = IntervalTree.of(expectedTypes)
        val actualTypes = IntervalTree.of(actualTypes)
        val scopes = IntervalTree.of(completionEntries)
        ElaborateResult(term, expectedTypes, actualTypes, scopes, diagnostics)
    }
}
