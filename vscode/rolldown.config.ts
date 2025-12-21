import { defineConfig } from "rolldown";

export default defineConfig({
  input: "src/extension.ts",
  output: {
    file: "out/extension.js",
    format: "cjs",
  },
  external: ["vscode"],
});
