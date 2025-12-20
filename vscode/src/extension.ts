import type { ExtensionContext } from "vscode";
import { LanguageClient } from "vscode-languageclient/node";

let client: LanguageClient;

export function activate(_context: ExtensionContext) {
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
}

export function deactivate() {
  return client?.stop();
}
