package koto.core

import java.net.URI
import kotlin.io.path.readText
import kotlin.io.path.toPath

class Cache {
    typealias Uri = String

    private val parseResults: MutableMap<Uri, ParseResult> = hashMapOf()
    private val elaborateResults: MutableMap<Uri, ElaborateResult> = hashMapOf()

    fun update(uri: Uri, text: String): ElaborateResult {
        val parseResult = parse(text)
        parseResults[uri] = parseResult
        val path = URI.create(uri).toPath()
        val elaborateResult = elaborate(parseResult, this, path)
        elaborateResults[uri] = elaborateResult
        return elaborateResult
    }

    fun remove(uri: Uri) {
        elaborateResults.remove(uri)
    }

    fun fetchParseResult(uri: Uri): ParseResult = parseResults[uri]!!

    fun fetchElaborateResult(uri: Uri): ElaborateResult {
        return elaborateResults.getOrPut(uri) {
            val text = URI.create(uri).toPath().readText()
            update(uri, text)
        }
    }
}
