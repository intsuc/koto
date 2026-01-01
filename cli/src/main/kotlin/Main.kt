package koto.cli

import com.github.ajalt.clikt.core.*
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import koto.lsp.KotoLanguageServer

class Koto : NoOpCliktCommand()

class Lsp : CliktCommand() {
    override fun help(context: Context): String = "Launch the language server"
    override fun run() = KotoLanguageServer.launch(System.`in`, System.out)
}

class Build : CliktCommand() {
    val input: String by option("-i", "--input").required()

    override fun run() {
        echo("Building $input")
    }
}

fun main(args: Array<String>) = Koto()
    .subcommands(
        Build(),
        Lsp(),
    )
    .main(args)
