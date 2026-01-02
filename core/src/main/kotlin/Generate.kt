package koto.core

import koto.core.util.stringify

data class GenerateResult(
    val code: String,
)

private class GenerateState {
    val builder: StringBuilder = StringBuilder()
}

private fun GenerateState.append(str: String) {
    builder.append(str)
}

private fun escapeName(name: String): String {
    return name.replace("-", "_")
}

private fun GenerateState.generatePattern(pattern: Pattern) {
    return when (pattern) {
        is Pattern.Var -> append(escapeName(pattern.text))
        is Pattern.Err -> error("Unexpected pattern: $pattern")
    }
}

private fun GenerateState.generateTerm(term: Term) {
    when (term) {
        is Term.Type -> append("\"${stringify(term, 0u)}\"")
        is Term.Bool -> append("\"${stringify(term, 0u)}\"")
        is Term.BoolOf -> append(if (term.value) "true" else "false")
        is Term.If -> {
            append("(")
            generateTerm(term.cond)
            append(" ? ")
            generateTerm(term.thenBranch)
            append(" : ")
            generateTerm(term.elseBranch)
            append(")")
        }

        is Term.Int64 -> append("\"${stringify(term, 0u)}\"")
        is Term.Int64Of -> append("${term.value}n")
        is Term.Float64 -> append("\"${stringify(term, 0u)}\"")
        is Term.Float64Of -> append("${term.value}")
        is Term.Str -> append("\"${stringify(term, 0u)}\"")
        is Term.StrOf -> append(stringify(term, 0u))
        is Term.Let -> {
            // TODO: use native statements
            append("((")
            generatePattern(term.binder)
            append(") => {\nreturn ")
            generateTerm(term.body)
            append(";\n})(")
            generateTerm(term.init)
            append(")")
        }

        is Term.LetFun -> {
            // TODO: use native statements
            val name = escapeName(term.name)
            append("(() => {\nconst $name = (")
            generatePattern(term.binder)
            append(") => {\nreturn ")
            generateTerm(term.body)
            append(";\n};\nreturn ")
            generateTerm(term.next)
            append(";\n})()")
        }

        is Term.Fun -> append("\"${stringify(term, 0u)}\"")
        is Term.FunOf -> {
            append("((")
            generatePattern(term.binder)
            append(") => {\nreturn ")
            generateTerm(term.result)
            append(";\n})")
        }

        is Term.Call -> {
            generateTerm(term.func)
            append("(")
            generateTerm(term.arg)
            append(")")
        }

        is Term.Pair -> append("\"${stringify(term, 0u)}\"")
        is Term.PairOf -> {
            append("[")
            generateTerm(term.first)
            append(", ")
            generateTerm(term.second)
            append("]")
        }

        is Term.Refine -> append("\"${stringify(term, 0u)}\"")
        is Term.Var -> append(escapeName(term.text))
        is Term.Meta -> error("Unexpected term: $term")
        is Term.Err -> error("Unexpected term: $term")
    }
}

fun generate(input: ElaborateResult): GenerateResult {
    val state = GenerateState()
    state.generateTerm(input.term)
    val term = state.builder.toString()
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
    const adapter = await navigator.gpu.requestAdapter()
    const device = await adapter.requestDevice()
    const canvas = document.getElementById("main")
    const context = canvas.getContext("webgpu")
    const format = navigator.gpu.getPreferredCanvasFormat()
    context.configure({
      device,
      format,
    })
    const observer = new ResizeObserver(([{
      devicePixelContentBoxSize,
      contentBoxSize,
      target,
    }]) => {
      const width = devicePixelContentBoxSize?.[0].inlineSize ?? contentBoxSize[0].inlineSize * devicePixelRatio
      const height = devicePixelContentBoxSize?.[0].blockSize ?? contentBoxSize[0].blockSize * devicePixelRatio
      target.width = Math.max(1, Math.min(width, device.limits.maxTextureDimension2D))
      target.height = Math.max(1, Math.min(height, device.limits.maxTextureDimension2D))
    })
    try {
      observer.observe(canvas, { box: "device-pixel-content-box" })
    } catch {
      observer.observe(canvas, { box: "content-box" })
    }
    
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
