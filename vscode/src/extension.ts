import { Range, WorkspaceEdit, workspace } from "vscode";
import type { Disposable, ExtensionContext } from "vscode";
import { LanguageClient } from "vscode-languageclient/node";

let client: LanguageClient;

function arrowEdit(): Disposable {
  let applying = false;
  return workspace.onDidChangeTextDocument(async (event) => {
    if (applying) return;
    if (event.document.languageId !== "koto") return;
    if (event.contentChanges.length !== 1) return;

    const change = event.contentChanges[0]!;
    if (change.text !== ">") return;
    if (change.rangeLength !== 0) return;

    const endOffset = change.rangeOffset + change.text.length;
    const startOffset = endOffset - "->".length;
    if (startOffset < 0) return;

    const range = new Range(
      event.document.positionAt(startOffset),
      event.document.positionAt(endOffset),
    );
    const text = event.document.getText(range);
    if (text !== "->") return;

    applying = true;
    try {
      const edit = new WorkspaceEdit();
      edit.replace(event.document.uri, range, "→");
      await workspace.applyEdit(edit);
    } finally {
      applying = false;
    }
  })
}

export function activate(context: ExtensionContext) {
  client = new LanguageClient(
    "koto",
    "ヿ Language Server",
    {
      command: "koto",
      args: ["lsp"],
      options: {
        shell: true,
      },
    },
    {
      documentSelector: [{ scheme: "file", language: "koto" }],
    },
  );
  client.start();

  context.subscriptions.push(
    arrowEdit(),
  );
}

export function deactivate() {
  return client?.stop();
}
