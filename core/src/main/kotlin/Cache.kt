package koto.core

import java.net.URI
import kotlin.io.path.readText
import kotlin.io.path.toPath

class Cache(
    private val debug: ((String) -> Unit)? = null,
) {
    typealias Uri = String

    private val texts: MutableMap<Uri, String> = hashMapOf()
    private val parseResults: MutableMap<Uri, ParseResult> = hashMapOf()
    private val elaborateResults: MutableMap<Uri, ElaborateResult> = hashMapOf()
    private val dependents: MutableMap<Uri, MutableSet<Uri>> = hashMapOf()

    private fun removeDependents(uri: Uri) {
        dependents[uri]?.forEach { dependentUri ->
            texts.remove(dependentUri)
            parseResults.remove(dependentUri)
            elaborateResults.remove(dependentUri)
            removeDependents(dependentUri)
        }
    }

    fun update(uri: Uri, text: String) {
        debug?.invoke("Update $uri")
        removeDependents(uri)
        texts[uri] = text
        parseResults.remove(uri)
        elaborateResults.remove(uri)
    }

    fun remove(uri: Uri) {
        debug?.invoke("Remove $uri")
        removeDependents(uri)
        texts.remove(uri)
        parseResults.remove(uri)
        elaborateResults.remove(uri)
    }

    fun fetchText(uri: Uri): String {
        return texts.getOrPut(uri) {
            debug?.invoke("Read $uri")
            try {
                URI.create(uri).toPath().readText()
            } catch (e: Exception) {
                debug?.invoke("Error reading $uri: ${e.message}")
                ""
            }
        }
    }

    fun fetchParseResult(uri: Uri): ParseResult {
        return parseResults.getOrPut(uri) {
            debug?.invoke("Parse $uri")
            val text = fetchText(uri)
            val parseResult = parse(text)
            parseResults[uri] = parseResult
            parseResult
        }
    }

    fun fetchElaborateResult(uri: Uri): ElaborateResult {
        return elaborateResults.getOrPut(uri) {
            debug?.invoke("Elaborate $uri")
            val parseResult = fetchParseResult(uri)
            val path = URI.create(uri).toPath()
            val elaborateResult = elaborate(parseResult, this, path)
            for (dependency in elaborateResult.dependencies) {
                dependents.getOrPut(dependency) { hashSetOf() }.add(uri)
            }
            elaborateResults[uri] = elaborateResult
            elaborateResult
        }
    }
}
