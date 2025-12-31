package koto.core

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
        is Term.Let -> {
            val binder = stringifyPattern(term.binder, 0u)
            val init = stringify(term.init, 0u)
            val body = stringify(term.body, 0u)
            "let $binder = $init $body"
        }

        is Term.Fun -> {
            val binder = stringifyPattern(term.binder, 0u)
            val param = stringify(term.param, 6u)
            val result = stringify(term.result, 5u)
            p(minBp, 5u, "$binder : $param → $result")
        }

        is Term.FunOf -> {
            val binder = stringifyPattern(term.binder, 0u)
            val result = stringify(term.result, 5u)
            p(minBp, 5u, "$binder → $result")
        }

        is Term.Call -> {
            val func = stringify(term.func, 30u)
            val arg = stringify(term.arg, 0u)
            "$func($arg)"
        }

        is Term.Pair -> {
            val binder = stringifyPattern(term.binder, 0u)
            val first = stringify(term.first, 11u)
            val second = stringify(term.second, 10u)
            p(minBp, 10u, "$binder : $first, $second")
        }

        is Term.PairOf -> {
            val first = stringify(term.first, 11u)
            val second = stringify(term.second, 10u)
            p(minBp, 10u, "$first, $second")
        }

        is Term.Refine -> {
            val binder = stringifyPattern(term.binder, 0u)
            val base = stringify(term.base, 15u)
            val property = stringify(term.property, 15u)
            p(minBp, 15u, "$binder : $base @ $property")
        }

        is Term.Var -> term.text
        is Term.Meta -> "?"
        is Term.Err -> "error"
    }
}

private fun stringifyPattern(pattern: Pattern, minBp: UInt): String {
    return when (pattern) {
        is Pattern.Var -> pattern.text
        is Pattern.Err -> "error"
    }
}

private fun p(minBp: UInt, bp: UInt, s: String): String {
    return if (minBp > bp) "($s)" else s
}
