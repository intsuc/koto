package koto.core.util

import koto.core.Pattern
import koto.core.Term

fun stringify(term: Term, minBp: UInt): String {
    return when (term) {
        is Term.Type -> "type"
        is Term.Bool -> "bool"
        is Term.BoolOf -> if (term.value) "true" else "false"
        is Term.If -> {
            val cond = stringify(term.cond, 0u)
            val thenBranch = stringify(term.thenBranch, 0u)
            val elseBranch = stringify(term.elseBranch, 0u)
            "if $cond then $thenBranch else $elseBranch"
        }

        is Term.Int64 -> "int64"
        is Term.Int64Of -> "${term.value}"
        is Term.Float64 -> "float64"
        is Term.Float64Of -> "${term.value}"
        is Term.Str -> "str"
        is Term.StrOf -> {
            val builder = StringBuilder()
            for (c in term.value) {
                when (c) {
                    '\n' -> builder.append("\\n")
                    '\r' -> builder.append("\\r")
                    '\t' -> builder.append("\\t")
                    '\\' -> builder.append("\\\\")
                    '\"' -> builder.append("\\\"")
                    else -> builder.append(c)
                }
            }
            "\"$builder\""
        }

        is Term.Let -> {
            val binder = stringifyPattern(term.binder, 0u)
            val init = stringify(term.init, 0u)
            val body = stringify(term.body, 0u)
            "let $binder = $init $body"
        }

        is Term.LetFun -> {
            val name = term.name
            val binders = term.binders.joinToString(", ") { binder -> stringifyPattern(binder, 0u) }
            val body = stringify(term.body, 0u)
            val next = stringify(term.next, 0u)
            "fun $name($binders) = $body $next"
        }

        is Term.Fun -> {
            val params = term.binders.zip(term.params).joinToString(", ") { (binder, param) ->
                val binder = stringifyPattern(binder, 0u)
                val param = stringify(param, 50u)
                "$binder : $param"
            }
            val result = stringify(term.result, 50u)
            p(minBp, 50u, "fun($params) → $result")
        }

        is Term.FunOf -> {
            val binders = term.binders.joinToString(", ") { binder -> stringifyPattern(binder, 0u) }
            val body = stringify(term.body, 50u)
            p(minBp, 50u, "fun($binders) = $body")
        }

        is Term.Call -> {
            val func = stringify(term.func, 30u)
            val args = term.args.joinToString(", ") { arg -> stringify(arg, 0u) }
            "$func($args)"
        }

        is Term.Record -> {
            val fields = term.fields.entries.joinToString(", ") { (key, value) ->
                val value = stringify(value, 0u)
                "$key = $value"
            }
            "{ $fields }"
        }

        is Term.RecordOf -> {
            val fields = term.fields.entries.joinToString(", ") { (key, value) ->
                val value = stringify(value, 0u)
                "$key = $value"
            }
            "{ $fields }"
        }

        is Term.Refine -> {
            val binder = stringifyPattern(term.binder, 0u)
            val base = stringify(term.base, 200u)
            val property = stringify(term.property, 200u)
            p(minBp, 200u, "$binder : $base @ $property")
        }

        is Term.Var -> term.text
        is Term.Meta -> "?"
        is Term.Err -> "error"
    }
}

private fun stringifyPattern(pattern: Pattern, minBp: UInt): String {
    return pattern
}

private fun p(minBp: UInt, bp: UInt, s: String): String {
    return if (minBp > bp) "($s)" else s
}
