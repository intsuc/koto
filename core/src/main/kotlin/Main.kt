package koto

import com.github.ajalt.clikt.core.*
import koto.lsp.KotoLanguageServer

class Koto : NoOpCliktCommand()

class Lsp : CliktCommand() {
    override fun help(context: Context): String = "Launch the language server"
    override fun run() = KotoLanguageServer.launch(System.`in`, System.out)
}

fun main(args: Array<String>) = Koto()
    .subcommands(
        Lsp(),
    )
    .main(args)
