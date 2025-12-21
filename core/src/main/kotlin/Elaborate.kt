package koto.core

/** de Bruijn index */
typealias Index = UInt

/** de Bruijn level */
typealias Level = UInt

sealed interface Abstract {
    val span: Span

    data class Type(override val span: Span) : Abstract

    data class Int64(override val span: Span) : Abstract

    data class Int64Lit(val value: Long, override val span: Span) : Abstract

    data class Var(val text: String, val index: Index, override val span: Span) : Abstract

    data class Err(val message: String, override val span: Span) : Abstract
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

private data class SynthResult(
    val term: Abstract,
    val type: Value,
)

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

private fun ElaborateState.synth(term: Concrete): SynthResult {
    return when (term) {
        is Concrete.Ident -> when (term.text) {
            "type" -> SynthResult(Abstract.Type(term.span), Value.Type)
            "int64" -> SynthResult(Abstract.Int64(term.span), Value.Int64)
            else -> when (val level = entries.indexOfLast { it.name == term.text }) {
                -1 -> when (val value = term.text.toLongOrNull()) {
                    null -> {
                        val message = "Unknown identifier: ${term.text}"
                        SynthResult(diagnose(message, term.span), Value.Err(message))
                    }

                    else -> SynthResult(Abstract.Int64Lit(value, term.span), Value.Int64Lit(value))
                }

                else -> {
                    val index = (entries.lastIndex - level).toUInt()
                    SynthResult(Abstract.Var(term.text, index, term.span), entries[level].type)
                }
            }
        }

        is Concrete.Err -> SynthResult(Abstract.Err(term.message, term.span), Value.Err(term.message))
    }
}

private fun ElaborateState.check(term: Concrete, expected: Value): Abstract {
    val synthesized = synth(term)
    return if (conv(synthesized.type, expected)) {
        synthesized.term
    } else {
        diagnose("Type mismatch", term.span)
    }
}

fun elaborate(input: ParseResult): ElaborateResult {
    return ElaborateState().run {
        val term = synth(input.term).term
        ElaborateResult(term, diagnostics)
    }
}
