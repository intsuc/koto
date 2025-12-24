package koto.lsp

import koto.core.elaborate
import koto.core.parse
import koto.core.stringify
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
            Diagnostic(range, diagnostic.message)
        })))
    }

    override fun hover(params: HoverParams): CompletableFuture<Hover> {
        val text = getText(params.textDocument)
        val parseResult = parse(text)
        val elaborateResult = elaborate(parseResult)
        val offset = params.position.toOffset(parseResult.lineStarts)
        val type = elaborateResult.types.getLeaf(offset)?.value ?: return completedFuture(null)
        val typeString = stringify(type, 0u)
        return completedFuture(Hover(MarkupContent(MarkupKind.MARKDOWN, "```koto\n$typeString\n```")))
    }

    override fun completion(params: CompletionParams): CompletableFuture<Either<List<CompletionItem>, CompletionList>> {
        val text = getText(params.textDocument)
        val parseResult = parse(text)
        val elaborateResult = elaborate(parseResult)
        val offset = params.position.toOffset(parseResult.lineStarts)
        val scopes = elaborateResult.scopes.getAll(offset)
        return completedFuture(Either.forRight(CompletionList(scopes.map { scope ->
            CompletionItem(scope)
        })))
    }
}
