package koto.core

fun stringify(term: Abstract, minBp: UInt): String {
    return when (term) {
        is Abstract.Type -> "type"
        is Abstract.Bool -> "bool"
        is Abstract.BoolOf -> if (term.value) "true" else "false"
        is Abstract.Int64 -> "int64"
        is Abstract.Int64Of -> "${term.value}"
        is Abstract.Let -> "let ${term.name} = ${stringify(term.init, 0u)}; ${stringify(term.body, 0u)}"
        is Abstract.Fun -> "fun(${term.name} : ${stringify(term.param, 0u)}) → ${stringify(term.result, 0u)}"
        is Abstract.FunOf -> "fun(${term.name}) { ${stringify(term.body, 0u)} }"
        is Abstract.Call -> "${stringify(term.func, 0u)}(${stringify(term.arg, 0u)})"
        is Abstract.Pair -> "${stringify(term.first, 10u)}, ${stringify(term.second, 10u)}"
        is Abstract.PairOf -> "${stringify(term.first, 10u)}, ${stringify(term.second, 10u)}"
        is Abstract.Var -> term.text
        is Abstract.Err -> term.message
    }
}
