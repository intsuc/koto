package koto.lsp

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.launch.LSPLauncher
import org.eclipse.lsp4j.services.*
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.completedFuture

class KotoLanguageServer private constructor() : LanguageClientAware, LanguageServer {
    private val textDocumentService: KotoTextDocumentService = KotoTextDocumentService()
    private val workspaceService: KotoWorkspaceService = KotoWorkspaceService()
    private val notebookDocumentService: KotoNotebookDocumentService = KotoNotebookDocumentService()

    override fun connect(client: LanguageClient) {
        textDocumentService.connect(client)
        workspaceService.connect(client)
    }

    override fun initialize(params: InitializeParams): CompletableFuture<InitializeResult> {
        return completedFuture(InitializeResult().apply {
            capabilities = ServerCapabilities().apply {
                setTextDocumentSync(TextDocumentSyncOptions().apply {
                    openClose = true
                    change = TextDocumentSyncKind.Full
                })
                diagnosticProvider = DiagnosticRegistrationOptions(false, false)
                setHoverProvider(true)
                completionProvider = CompletionOptions(false, emptyList())
            }
        })
    }

    override fun shutdown(): CompletableFuture<in Any> {
        return completedFuture(null)
    }

    override fun exit() {
    }

    override fun getTextDocumentService(): TextDocumentService {
        return textDocumentService
    }

    override fun getWorkspaceService(): WorkspaceService {
        return workspaceService
    }

    override fun getNotebookDocumentService(): NotebookDocumentService {
        return notebookDocumentService
    }

    companion object {
        fun launch(input: InputStream, output: OutputStream) {
            val server = KotoLanguageServer()
            val launcher = LSPLauncher.createServerLauncher(server, input, output)
            server.connect(launcher.remoteProxy)
            launcher.startListening()
        }
    }
}
