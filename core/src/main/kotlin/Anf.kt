package koto.core

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import koto.core.util.stringify

sealed interface AnfTerm {
    data class If(
        val cond: AnfAtom,
        val thenBranch: AnfTerm,
        val elseBranch: AnfTerm,
        val next: AnfTerm,
    ) : AnfTerm

    data class Let(
        val binder: String,
        val init: AnfAtom,
        val next: AnfTerm,
    ) : AnfTerm

    data class LetFun(
        val name: String,
        val binders: List<String>,
        val body: AnfTerm,
        val next: AnfTerm,
    ) : AnfTerm

    data class Def(
        val name: String,
        val next: AnfTerm,
    ) : AnfTerm

    data class Set(
        val name: String,
        val source: AnfAtom,
    ) : AnfTerm

    data class Ret(
        val result: AnfAtom,
    ) : AnfTerm

    data class Atom(
        val atom: AnfAtom,
    ) : AnfTerm
}

sealed interface AnfAtom {
    data object Type : AnfAtom

    data object Bool : AnfAtom

    data class BoolOf(
        val value: Boolean,
    ) : AnfAtom

    data object Int64 : AnfAtom

    data class Int64Of(
        val value: Long,
    ) : AnfAtom

    data object Float64 : AnfAtom

    data class Float64Of(
        val value: Double,
    ) : AnfAtom

    data object Str : AnfAtom

    data class StrOf(
        val value: String,
    ) : AnfAtom

    data class Fun(
        val binders: List<String>,
        val params: List<AnfAtom>,
        val result: AnfAtom,
    ) : AnfAtom

    data class FunOf(
        val binders: List<String>,
        val body: AnfTerm,
    ) : AnfAtom

    data class Call(
        val func: AnfAtom,
        val args: List<AnfAtom>,
    ) : AnfAtom

    data class Record(
        val fields: Map<String, AnfAtom>,
    ) : AnfAtom

    data class RecordOf(
        val fields: Map<String, AnfAtom>,
    ) : AnfAtom

    data class Access(
        val record: AnfAtom,
        val field: String,
    ) : AnfAtom

    data class Refine(
        val binder: Pattern,
        val base: AnfAtom,
        val predicate: AnfTerm,
    ) : AnfAtom

    data class Check(
        val target: AnfAtom,
        val type: AnfAtom,
    ) : AnfAtom

    data class Var(
        val text: String,
    ) : AnfAtom
}

private class Fresh {
    private val used: MutableSet<String> = mutableSetOf()
    private val next: MutableMap<String, Int> = mutableMapOf()

    operator fun invoke(base: String): String {
        var i = next.getOrDefault(base, 0)
        while (true) {
            val candidate = if (i == 0) base else "${base}_$i"
            i++
            if (used.add(candidate)) {
                next[base] = i
                return candidate
            }
        }
    }
}

private data class AnfState(
    val env: PersistentList<String>,
    val fresh: Fresh,
) {
    fun push(name: String): AnfState = copy(env = env.add(name))

    fun lookup(index: Index): String {
        val level = env.lastIndex - index.toInt()
        return env[level]
    }
}

private fun AnfState.anfTerm(term: Term, k: (AnfAtom) -> AnfTerm): AnfTerm {
    return when (term) {
        is Term.Type -> k(AnfAtom.Type)
        is Term.Bool -> k(AnfAtom.Bool)
        is Term.BoolOf -> k(AnfAtom.BoolOf(term.value))
        is Term.If -> anfTerm(term.cond) { cond ->
            val result = fresh("result")
            val thenBranch = anfTerm(term.thenBranch) { AnfTerm.Set(result, it) }
            val elseBranch = anfTerm(term.elseBranch) { AnfTerm.Set(result, it) }
            val next = k(AnfAtom.Var(result))
            AnfTerm.Def(
                result,
                AnfTerm.If(
                    cond,
                    thenBranch,
                    elseBranch,
                    next,
                ),
            )
        }

        is Term.Int64 -> k(AnfAtom.Int64)
        is Term.Int64Of -> k(AnfAtom.Int64Of(term.value))
        is Term.Float64 -> k(AnfAtom.Float64)
        is Term.Float64Of -> k(AnfAtom.Float64Of(term.value))
        is Term.Str -> k(AnfAtom.Str)
        is Term.StrOf -> k(AnfAtom.StrOf(term.value))
        is Term.Let -> anfTerm(term.init) { init ->
            val binder = fresh(term.binder)
            val nextState = push(binder)
            val next = nextState.anfTerm(term.body, k)
            AnfTerm.Let(
                binder,
                init,
                next,
            )
        }

        is Term.LetFun -> {
            val name = fresh(term.name)
            var bodyState = push(name)
            val binders = term.binders.map { binder ->
                val binder = bodyState.fresh(binder)
                bodyState = bodyState.push(binder)
                binder
            }
            val body = bodyState.anfTerm(term.body) { AnfTerm.Ret(it) }
            val nextState = push(name)
            val next = nextState.anfTerm(term.next, k)
            AnfTerm.LetFun(
                name,
                binders,
                body,
                next,
            )
        }

        is Term.Fun -> anfTermList(term.params) { params ->
            anfTerm(term.result) { result ->
                k(AnfAtom.Fun(term.binders, params, result))
            }
        }

        is Term.FunOf -> {
            var bodyState = this
            val binders = term.binders.map { binder ->
                val binder = bodyState.fresh(binder)
                bodyState = bodyState.push(binder)
                binder
            }
            val body = bodyState.anfTerm(term.body) { AnfTerm.Ret(it) }
            k(AnfAtom.FunOf(binders, body))
        }

        is Term.Call -> anfTerm(term.func) { func ->
            anfTermList(term.args) { args ->
                k(AnfAtom.Call(func, args))
            }
        }

        is Term.Record -> anfTermMap(term.fields) { fields ->
            k(AnfAtom.Record(fields))
        }

        is Term.RecordOf -> anfTermMap(term.fields) { fields ->
            k(AnfAtom.RecordOf(fields))
        }

        is Term.Access -> anfTerm(term.record) { record ->
            k(AnfAtom.Access(record, term.field))
        }

        is Term.Refine -> anfTerm(term.base) { base ->
            val binder = fresh(term.binder)
            val predicateState = push(binder)
            val predicate = predicateState.anfTerm(term.predicate) { AnfTerm.Ret(it) }
            k(AnfAtom.Refine(binder, base, predicate))
        }

        is Term.Check -> anfTerm(term.target) { target ->
            anfTerm(term.type) { type ->
                k(AnfAtom.Check(target, type))
            }
        }

        is Term.Var -> {
            val text = lookup(term.index)
            k(AnfAtom.Var(text))
        }

        is Term.Meta -> error("unexpected term: ${stringify(term)}")
        is Term.Err -> error("unexpected term: ${stringify(term)}")
    }
}

private fun AnfState.anfTermList(terms: List<Term>, k: (List<AnfAtom>) -> AnfTerm): AnfTerm {
    val iterator = terms.iterator()
    fun loop(acc: MutableList<AnfAtom>): AnfTerm {
        if (!iterator.hasNext()) return k(acc)
        val term = iterator.next()
        return anfTerm(term) {
            acc.add(it)
            loop(acc)
        }
    }
    return loop(mutableListOf())
}

private fun AnfState.anfTermMap(terms: Map<String, Term>, k: (Map<String, AnfAtom>) -> AnfTerm): AnfTerm {
    val iterator = terms.entries.iterator()
    fun loop(acc: MutableMap<String, AnfAtom>): AnfTerm {
        if (!iterator.hasNext()) return k(acc)
        val (key, term) = iterator.next()
        return anfTerm(term) {
            acc[key] = it
            loop(acc)
        }
    }
    return loop(mutableMapOf())
}

fun anf(input: Term): AnfTerm {
    val state = AnfState(persistentListOf(), Fresh())
    return state.anfTerm(input) { AnfTerm.Atom(it) }
}
