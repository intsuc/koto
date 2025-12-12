package koto.lsp

import org.eclipse.lsp4j.DidChangeNotebookDocumentParams
import org.eclipse.lsp4j.DidCloseNotebookDocumentParams
import org.eclipse.lsp4j.DidOpenNotebookDocumentParams
import org.eclipse.lsp4j.DidSaveNotebookDocumentParams
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.NotebookDocumentService

internal class KotoNotebookDocumentService : LanguageClientAware, NotebookDocumentService {
    private lateinit var client: LanguageClient

    override fun connect(client: LanguageClient) {
        this.client = client
    }

    override fun didOpen(params: DidOpenNotebookDocumentParams) {
    }

    override fun didChange(params: DidChangeNotebookDocumentParams) {
    }

    override fun didSave(params: DidSaveNotebookDocumentParams) {
    }

    override fun didClose(params: DidCloseNotebookDocumentParams) {
    }
}
