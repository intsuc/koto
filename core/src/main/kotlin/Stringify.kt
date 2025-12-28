package koto.core

fun stringify(term: Abstract, minBp: UInt): String {
    return when (term) {
        is Abstract.Type -> "type"
        is Abstract.Bool -> "bool"
        is Abstract.BoolOf -> if (term.value) "true" else "false"
        is Abstract.Int64 -> "int64"
        is Abstract.Int64Of -> "${term.value}"
        is Abstract.Float64 -> "float64"
        is Abstract.Float64Of -> "${term.value}"
        is Abstract.Let -> "let ${term.name} = ${stringify(term.init, 0u)}; ${stringify(term.body, 0u)}"
        is Abstract.Fun -> p(minBp, 5u, "${term.name?.let { "$it : " } ?: ""}${stringify(term.param, 6u)} → ${stringify(term.result, 5u)}")
        is Abstract.FunOf -> p(minBp, 5u, "${term.name} → ${stringify(term.result, 5u)}")
        is Abstract.Call -> "${stringify(term.func, 0u)}(${stringify(term.arg, 0u)})"
        is Abstract.Pair -> p(minBp, 10u, "${term.name?.let { "$it : " } ?: ""}${stringify(term.first, 11u)}, ${stringify(term.second, 10u)}")
        is Abstract.PairOf -> p(minBp, 10u, "${stringify(term.first, 11u)}, ${stringify(term.second, 10u)}")
        is Abstract.Var -> term.text
        is Abstract.Err -> "error"
    }
}

private fun p(minBp: UInt, bp: UInt, s: String): String {
    return if (minBp > bp) "($s)" else s
}
