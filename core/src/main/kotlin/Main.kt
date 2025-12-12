package koto

import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.main

class Koto : NoOpCliktCommand()

fun main(args: Array<String>) = Koto().main(args)
