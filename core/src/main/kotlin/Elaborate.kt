package koto.core

/** de Bruijn index */
typealias Index = UInt

/** de Bruijn level */
typealias Level = UInt

sealed interface Abstract {
    val type: Lazy<Value>
    val span: Span

    data class Type(override val span: Span) : Abstract {
        override val type: Lazy<Value> = lazyOf(Value.Type)
    }

    data class Int64(override val span: Span) : Abstract {
        override val type: Lazy<Value> = lazyOf(Value.Type)
    }

    data class Int64Lit(val value: Long, override val span: Span) : Abstract {
        override val type: Lazy<Value> = lazyOf(Value.Int64)
    }

    data class Var(val text: String, val index: Index, override val type: Lazy<Value>, override val span: Span) : Abstract

    data class Err(val message: String, override val span: Span) : Abstract {
        override val type: Lazy<Value> = lazyOf(Value.Err(message))
    }
}

sealed interface Value {
    data object Type : Value

    data object Int64 : Value

    data class Int64Lit(val value: Long) : Value

    data class Var(val text: String, val level: Level) : Value

    data class Err(val message: String) : Value
}

data class ElaborateResult(
    val term: Abstract,
    val diagnostics: List<Diagnostic>,
)

private data class Entry(
    val name: String,
    val type: Value,
)

private class ElaborateState {
    val entries: MutableList<Entry> = mutableListOf()
    val diagnostics: MutableList<Diagnostic> = mutableListOf()
}

private fun ElaborateState.diagnose(message: String, span: Span): Abstract {
    diagnostics.add(Diagnostic(message, span))
    return Abstract.Err(message, span)
}

private fun conv(term1: Value, term2: Value): Boolean {
    return when (term1) {
        is Value.Type if term2 is Value.Type -> true
        is Value.Int64 if term2 is Value.Int64 -> true
        is Value.Int64Lit if term2 is Value.Int64Lit -> term1.value == term2.value
        is Value.Var if term2 is Value.Var -> term1.level == term2.level
        else -> false
    }
}

private fun ElaborateState.synth(term: Concrete): Abstract {
    return when (term) {
        is Concrete.Ident -> when (term.text) {
            "type" -> Abstract.Type(term.span)
            "int64" -> Abstract.Int64(term.span)
            else -> when (val level = entries.indexOfLast { it.name == term.text }) {
                -1 -> when (val value = term.text.toLongOrNull()) {
                    null -> diagnose("Unknown identifier: ${term.text}", term.span)
                    else -> Abstract.Int64Lit(value, term.span)
                }

                else -> {
                    val index = (entries.lastIndex - level).toUInt()
                    val type = entries[level].type
                    Abstract.Var(term.text, index, lazyOf(type), term.span)
                }
            }
        }

        is Concrete.Err -> Abstract.Err(term.message, term.span)
    }
}

private fun ElaborateState.check(term: Concrete, expected: Value): Abstract {
    val synthesized = synth(term)
    return if (conv(synthesized.type.value, expected)) {
        synthesized
    } else {
        diagnose("Type mismatch", term.span)
    }
}

fun elaborate(input: ParseResult): ElaborateResult {
    return ElaborateState().run {
        val term = synth(input.term)
        ElaborateResult(term, diagnostics)
    }
}
