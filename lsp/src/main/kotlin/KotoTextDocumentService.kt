package koto.lsp

import koto.core.CompletionEntry
import koto.core.elaborate
import koto.core.parse
import koto.core.util.Span
import koto.core.util.stringify
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.TextDocumentService
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.completedFuture

internal class KotoTextDocumentService : LanguageClientAware, TextDocumentService {
    private lateinit var client: LanguageClient
    private val documents: MutableMap<String, String> = hashMapOf()

    private fun getText(document: TextDocumentIdentifier): String {
        return documents[document.uri]!!
    }

    override fun connect(client: LanguageClient) {
        this.client = client
    }

    override fun didOpen(params: DidOpenTextDocumentParams) {
        val text = params.textDocument.text
        documents[params.textDocument.uri] = text
    }

    override fun didChange(params: DidChangeTextDocumentParams) {
        val text = params.contentChanges.last().text
        documents[params.textDocument.uri] = text
    }

    override fun didClose(params: DidCloseTextDocumentParams) {
        documents.remove(params.textDocument.uri)
    }

    override fun didSave(params: DidSaveTextDocumentParams) {
    }

    override fun diagnostic(params: DocumentDiagnosticParams): CompletableFuture<DocumentDiagnosticReport> {
        val text = getText(params.textDocument)
        val parseResult = parse(text)
        val elaborateResult = elaborate(parseResult)
        val diagnostics = parseResult.diagnostics + elaborateResult.diagnostics
        return completedFuture(DocumentDiagnosticReport(RelatedFullDocumentDiagnosticReport(diagnostics.map { diagnostic ->
            val range = diagnostic.span.toRange(parseResult.lineStarts)
            Diagnostic(range, diagnostic.message, diagnostic.severity.toLsp(), "ヿ")
        })))
    }

    override fun hover(params: HoverParams): CompletableFuture<Hover?> {
        val text = getText(params.textDocument)
        val parseResult = parse(text)
        val elaborateResult = elaborate(parseResult)
        val offset = params.position.toOffset(parseResult.lineStarts)
        val expected = elaborateResult.expectedTypes.getLeaf(offset)
        val actual = elaborateResult.actualTypes.getLeaf(offset)
        operator fun Span.contains(inner: Span): Boolean = start < inner.start || inner.endExclusive < endExclusive
        return completedFuture(
            Hover(
                MarkupContent(
                    MarkupKind.MARKDOWN, "```koto\n${
                        when {
                            expected != null && actual == null || expected != null && actual != null && expected.span in actual.span -> {
                                val expected = stringify(expected.value.value)
                                "⇐ $expected"
                            }

                            expected == null && actual != null || expected != null && actual != null && actual.span in expected.span -> {
                                val actual = stringify(actual.value.value)
                                "⇒ $actual"
                            }

                            expected != null && actual != null && expected.span == actual.span -> {
                                val expected = stringify(expected.value.value)
                                val actual = stringify(actual.value.value)
                                if (expected == actual) {
                                    "⇔ $expected"
                                } else {
                                    "⇐ $expected\n⇒ $actual"
                                }
                            }

                            else -> return@hover completedFuture(null)
                        }
                    }\n```"
                )
            )
        )
    }

    override fun completion(params: CompletionParams): CompletableFuture<Either<List<CompletionItem>, CompletionList>> {
        val text = getText(params.textDocument)
        val parseResult = parse(text)
        val elaborateResult = elaborate(parseResult)
        val offset = params.position.toOffset(parseResult.lineStarts)
        val entries = elaborateResult.completionEntries.getAll(offset)
        val deduplicatedEntries = mutableMapOf<String, CompletionEntry>()
        for (entry in entries) {
            deduplicatedEntries[entry.name] = entry
        }
        return completedFuture(Either.forRight(CompletionList(deduplicatedEntries.map { (_, entry) ->
            CompletionItem(entry.name).apply {
                kind = CompletionItemKind.Variable
                labelDetails = CompletionItemLabelDetails().apply {
                    detail = " : ${stringify(entry.type.value)}"
                }
            }
        })))
    }
}
