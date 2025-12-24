import { Range, WorkspaceEdit, workspace } from "vscode";
import type { Disposable, ExtensionContext } from "vscode";
import { LanguageClient } from "vscode-languageclient/node";

let client: LanguageClient;

function arrowEdit(): Disposable {
  let applying = false;
  return workspace.onDidChangeTextDocument(async (event) => {
    if (applying) return;
    if (event.document.languageId !== "koto") return;

    const changes = [...event.contentChanges].sort(
      (a, b) => a.rangeOffset - b.rangeOffset,
    );

    let cumulativeDelta = 0;
    const replacements: { startOffset: number, range: Range }[] = [];

    for (const change of changes) {
      const delta = change.text.length - change.rangeLength;

      if (change.text === ">" && change.rangeLength === 0) {
        const endOffset = change.rangeOffset + cumulativeDelta + change.text.length;
        const startOffset = endOffset - "->".length;

        if (startOffset >= 0) {
          const range = new Range(
            event.document.positionAt(startOffset),
            event.document.positionAt(endOffset),
          );
          const text = event.document.getText(range);
          if (text === "->") {
            replacements.push({ startOffset, range });
          }
        }
      }

      cumulativeDelta += delta;
    }

    if (replacements.length === 0) return;

    replacements.sort((a, b) => b.startOffset - a.startOffset);

    const edit = new WorkspaceEdit();
    for (const { range } of replacements) {
      edit.replace(event.document.uri, range, "→");
    }
    try {
      applying = true;
      await workspace.applyEdit(edit);
    } finally {
      applying = false;
    }
  });
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
