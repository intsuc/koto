package koto.core

import koto.core.util.quote

data class GenerateResult(
    val code: String,
)

private class GenerateState {
    private val builder: StringBuilder = StringBuilder()
    private var depth: Int = 0

    fun append(str: String) {
        builder.append(str)
    }

    fun newline() {
        builder.append('\n')
        repeat(depth) { builder.append("  ") }
    }

    private fun indent() {
        depth += 1
        newline()
    }

    private fun dedent() {
        depth -= 1
        newline()
    }

    inline fun indented(block: GenerateState.() -> Unit) {
        indent()
        block()
        dedent()
    }

    override fun toString(): String = builder.toString()
}

private fun escapeName(name: String): String {
    return name.replace("-", "_")
}

private fun GenerateState.generatePattern(pattern: Pattern) {
    append(escapeName(pattern))
}

private inline fun <T> GenerateState.generateCollection(elements: Collection<T>, generate: GenerateState.(T) -> Unit) {
    elements.forEachIndexed { index, element ->
        if (index > 0) append(", ")
        generate(element)
    }
}

private fun GenerateState.generateTerm(term: AnfTerm) {
    when (term) {
        is AnfTerm.If -> {
            append("if (")
            generateAtom(term.cond)
            append(") {")
            indented {
                generateTerm(term.thenBranch)
            }
            append("} else {")
            indented {
                generateTerm(term.elseBranch)
            }
            append("}")
            newline()
            generateTerm(term.next)
        }

        is AnfTerm.Let -> {
            append("const ")
            generatePattern(term.binder)
            append(" = ")
            generateAtom(term.init)
            newline()
            generateTerm(term.next)
        }

        is AnfTerm.LetFun -> {
            append("const ")
            generatePattern(term.name)
            append(" = (")
            generateCollection(term.binders) { binder ->
                generatePattern(binder)
            }
            append(") => {")
            indented {
                generateTerm(term.body)
            }
            append("}")
            newline()
            generateTerm(term.next)
        }

        is AnfTerm.Def -> {
            append("let ")
            append(escapeName(term.name))
            newline()
            generateTerm(term.next)
        }

        is AnfTerm.Set -> {
            append(escapeName(term.name))
            append(" = ")
            generateAtom(term.source)
        }

        is AnfTerm.Ret -> {
            append("return ")
            generateAtom(term.result)
        }

        is AnfTerm.Atom -> generateAtom(term.atom)
    }
}

private fun GenerateState.generateAtom(atom: AnfAtom) {
    when (atom) {
        is AnfAtom.Type -> append("type")
        is AnfAtom.Bool -> append("bool")
        is AnfAtom.BoolOf -> append(atom.value.toString())
        is AnfAtom.Int64 -> append("int64")
        is AnfAtom.Int64Of -> append(atom.value.toString())
        is AnfAtom.Float64 -> append("float64")
        is AnfAtom.Float64Of -> append(atom.value.toString())
        is AnfAtom.Str -> append("str")
        is AnfAtom.StrOf -> append(quote(atom.value))
        is AnfAtom.Fun -> {
            append("fun([")
            generateCollection(atom.params) { param ->
                generateAtom(param)
            }
            append("], ")
            generateAtom(atom.result)
            append(")")
        }

        is AnfAtom.FunOf -> {
            append("((")
            generateCollection(atom.binders) { binder ->
                generatePattern(binder)
            }
            append(") => ")
            if (atom.body is AnfTerm.Ret) {
                generateAtom(atom.body.result)
            } else {
                append("{")
                indented {
                    generateTerm(atom.body)
                }
                append("}")
            }
            append(")")
        }

        is AnfAtom.Call -> {
            generateAtom(atom.func)
            append("(")
            generateCollection(atom.args) { arg ->
                generateAtom(arg)
            }
            append(")")
        }

        is AnfAtom.Record -> {
            append("record({")
            generateCollection(atom.fields.entries) { (key, value) ->
                append(escapeName(key))
                append(": ")
                generateAtom(value)
            }
            append("})")
        }

        is AnfAtom.RecordOf -> {
            append("{")
            generateCollection(atom.fields.entries) { (key, value) ->
                append(escapeName(key))
                append(": ")
                generateAtom(value)
            }
            append("}")
        }

        is AnfAtom.Access -> {
            generateAtom(atom.record)
            append(".")
            append(atom.field)
        }

        is AnfAtom.Refine -> {
            append("refine(")
            generateAtom(atom.base)
            append(", (")
            generatePattern(atom.binder)
            append(") => ")
            if (atom.predicate is AnfTerm.Ret) {
                generateAtom(atom.predicate.result)
            } else {
                append("{")
                indented {
                    generateTerm(atom.predicate)
                }
                append("}")
            }

            append(")")
        }

        is AnfAtom.Check -> {
            append("checkType(")
            generateAtom(atom.target)
            append(", ")
            generateAtom(atom.type)
            append(")")
        }

        is AnfAtom.Var -> append(escapeName(atom.text))
    }
}

fun generate(input: AnfTerm): GenerateResult {
    val state = GenerateState()
    state.generateTerm(input)
    val runtime = GenerateResult::class.java.getResourceAsStream("/runtime.js")!!.bufferedReader().readText()
    val term = state.toString()
    val code = """<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
html,
body {
  margin: 0;
  height: 100%;
}
canvas {
  display: block;
  width: 100%;
  height: 100%;
}
</style>
<script type="module">
$runtime
$term
</script>
</head>
<body>
<canvas id="main"></canvas>
</body>
</html>
"""
    return GenerateResult(code)
}
