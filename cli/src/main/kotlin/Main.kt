package koto.cli

import com.github.ajalt.clikt.core.*
import com.github.ajalt.clikt.parameters.arguments.argument
import koto.core.anf
import koto.core.elaborate
import koto.core.generate
import koto.core.parse
import koto.core.util.Severity
import koto.lsp.KotoLanguageServer
import kotlin.io.path.Path
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.system.exitProcess

class Koto : NoOpCliktCommand()

class Lsp : CliktCommand() {
    override fun help(context: Context): String = "Launch the language server"
    override fun run() = KotoLanguageServer.launch(System.`in`, System.out)
}

class Build : CliktCommand() {
    val input: String by argument()

    override fun run() {
        val inputPath = Path(input)
        echo("Building $input")
        val text = inputPath.readText()
        val parseResult = parse(text)
        val elaborateResult = elaborate(parseResult)
        val diagnostics = parseResult.diagnostics + elaborateResult.diagnostics
        var hasErrors = false
        for (diagnostic in diagnostics) {
            echo(diagnostic.toString())
            if (diagnostic.severity == Severity.ERROR) {
                hasErrors = true
            }
        }
        if (hasErrors) {
            echo("Build failed with errors.")
            exitProcess(1)
        } else {
            val anfTerm = anf(elaborateResult.term)
            val generateResult = generate(anfTerm)
            val outputPath = inputPath.resolveSibling("${inputPath.nameWithoutExtension}.html")
            outputPath.writeText(generateResult.code)
            echo("Build succeeded. Output written to $outputPath")
        }
    }
}

fun main(args: Array<String>) = Koto()
    .subcommands(
        Build(),
        Lsp(),
    )
    .main(args)
