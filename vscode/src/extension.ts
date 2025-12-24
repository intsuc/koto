import type { ExtensionContext } from "vscode";
import { LanguageClient } from "vscode-languageclient/node";
import { registerComposition } from "./composition";

let client: LanguageClient;

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
    ...registerComposition(),
  );
}

export function deactivate() {
  return client?.stop();
}
