package koto.lsp

import koto.core.Cache
import koto.core.CompletionEntry
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
    private val cache: Cache = Cache()

    override fun connect(client: LanguageClient) {
        this.client = client
    }

    override fun didOpen(params: DidOpenTextDocumentParams) {
        val _ = cache.update(params.textDocument.uri, params.textDocument.text)
    }

    override fun didChange(params: DidChangeTextDocumentParams) {
        val _ = cache.update(params.textDocument.uri, params.contentChanges.last().text)
    }

    override fun didClose(params: DidCloseTextDocumentParams) {
        cache.remove(params.textDocument.uri)
    }

    override fun didSave(params: DidSaveTextDocumentParams) {
    }

    override fun diagnostic(params: DocumentDiagnosticParams): CompletableFuture<DocumentDiagnosticReport> {
        val parseResult = cache.fetchParseResult(params.textDocument.uri)
        val elaborateResult = cache.fetchElaborateResult(params.textDocument.uri)
        return completedFuture(DocumentDiagnosticReport(RelatedFullDocumentDiagnosticReport(elaborateResult.diagnostics.map { diagnostic ->
            val range = diagnostic.span.toRange(parseResult.lineStarts)
            Diagnostic(range, diagnostic.message, diagnostic.severity.toLsp(), "ヿ")
        })))
    }

    override fun hover(params: HoverParams): CompletableFuture<Hover?> {
        val parseResult = cache.fetchParseResult(params.textDocument.uri)
        val elaborateResult = cache.fetchElaborateResult(params.textDocument.uri)
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
        val parseResult = cache.fetchParseResult(params.textDocument.uri)
        val elaborateResult = cache.fetchElaborateResult(params.textDocument.uri)
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
