package koto.core.util

import koto.core.Pattern
import koto.core.Term

fun quote(string: String): String {
    val builder = StringBuilder()
    for (c in string) {
        when (c) {
            '\n' -> builder.append("\\n")
            '\r' -> builder.append("\\r")
            '\t' -> builder.append("\\t")
            '\\' -> builder.append("\\\\")
            '\"' -> builder.append("\\\"")
            else -> builder.append(c)
        }
    }
    return "\"$builder\""
}

fun stringify(term: Term, minBp: UInt = 0u): String {
    return when (term) {
        is Term.Type -> "type"
        is Term.Bool -> "bool"
        is Term.BoolOf -> if (term.value) "true" else "false"
        is Term.If -> {
            val cond = stringify(term.cond)
            val thenBranch = stringify(term.thenBranch)
            val elseBranch = stringify(term.elseBranch)
            "if $cond then $thenBranch else $elseBranch"
        }

        is Term.Int64 -> "int64"
        is Term.Int64Of -> "${term.value}"
        is Term.Float64 -> "float64"
        is Term.Float64Of -> "${term.value}"
        is Term.Str -> "str"
        is Term.StrOf -> quote(term.value)
        is Term.Let -> {
            val binder = stringifyPattern(term.binder)
            val init = stringify(term.init)
            val body = stringify(term.body)
            "let $binder = $init $body"
        }

        is Term.LetFun -> {
            val name = term.name
            val binders = term.binders.joinToString(", ") { binder -> stringifyPattern(binder) }
            val body = stringify(term.body)
            val next = stringify(term.next)
            "fun $name($binders) = $body $next"
        }

        is Term.Fun -> {
            val params = term.binders.zip(term.params).joinToString(", ") { (binder, param) ->
                val binder = stringifyPattern(binder)
                val param = stringify(param, 50u)
                "$binder : $param"
            }
            val result = stringify(term.result, 50u)
            p(minBp, 50u, "fun($params) → $result")
        }

        is Term.FunOf -> {
            val binders = term.binders.joinToString(", ") { binder -> stringifyPattern(binder) }
            val body = stringify(term.body, 50u)
            p(minBp, 50u, "fun($binders) = $body")
        }

        is Term.Call -> {
            val func = stringify(term.func, 500u)
            val args = term.args.joinToString(", ") { arg -> stringify(arg) }
            p(minBp, 500u, "$func($args)")
        }

        is Term.Record -> {
            val fields = term.fields.entries.joinToString(", ") { (key, value) ->
                val value = stringify(value)
                "$key = $value"
            }
            if (fields.isEmpty()) "{}" else "{ $fields }"
        }

        is Term.RecordOf -> {
            val fields = term.fields.entries.joinToString(", ") { (key, value) ->
                val value = stringify(value)
                "$key = $value"
            }
            if (fields.isEmpty()) "{}" else "{ $fields }"
        }

        is Term.Access -> {
            val target = stringify(term.record, 300u)
            val field = term.field
            p(minBp, 300u, "$target.$field")
        }

        is Term.Refine -> {
            val binder = stringifyPattern(term.binder)
            val base = stringify(term.base, 200u)
            val property = stringify(term.predicate, 200u)
            p(minBp, 200u, "$binder : $base @ $property")
        }

        is Term.Var -> term.text
        is Term.Check -> stringify(term.target)
        is Term.Meta -> "?"
        is Term.Err -> "error"
    }
}

private fun stringifyPattern(pattern: Pattern, minBp: UInt = 0u): String {
    return pattern
}

private fun p(minBp: UInt, bp: UInt, s: String): String {
    return if (minBp > bp) "($s)" else s
}
