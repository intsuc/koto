import { window, Range, workspace, WorkspaceEdit, commands } from "vscode";
import type {
  Disposable,
  TextDocument,
  TextDocumentContentChangeEvent,
  TextEditorDecorationType,
} from "vscode";

type TrieNode = {
  children: Map<string, TrieNode>;
  value?: string;
};

const compositionMap = new Map<string, string>([
  ["->", "→"],
  ["-->", "⟶"],
  ["=>", "⇒"],
]);

function buildTrie(compositions: Map<string, string>): TrieNode {
  const root: TrieNode = { children: new Map() };

  for (const [key, value] of compositions) {
    if (key.length === 0) continue;
    let node = root;
    for (const ch of key) {
      let next = node.children.get(ch);
      if (!next) {
        next = { children: new Map() };
        node.children.set(ch, next);
      }
      node = next;
    }
    node.value = value;
  }

  return root;
}

const compositionTrie = buildTrie(compositionMap);

let pendingCompositionDecoration: TextEditorDecorationType;
type PendingCompositionCandidate = {
  startOffset: number;
  node: TrieNode;
  length: number;
};
type PendingCompositions = {
  seq: number;
  candidates: PendingCompositionCandidate[];
};
const pendingCompositionsByDocument = new Map<string, PendingCompositions>();
let changeSeq = 0;

function mapOffsetThroughChanges(
  oldOffset: number,
  changes: readonly TextDocumentContentChangeEvent[],
): number | null {
  let newOffset = oldOffset;
  for (const change of changes) {
    const startOld = change.rangeOffset;
    const endOld = startOld + change.rangeLength;
    const delta = change.text.length - change.rangeLength;

    if (oldOffset < startOld) continue;
    if (oldOffset >= endOld) {
      newOffset += delta;
      continue;
    }

    return null;
  }
  return newOffset;
}

function documentLength(document: TextDocument): number {
  if (document.lineCount === 0) return 0;
  const lastLine = document.lineAt(document.lineCount - 1);
  return document.offsetAt(lastLine.range.end);
}

function renderPendingCompositionDecoration(documentUriKey: string) {
  const pending = pendingCompositionsByDocument.get(documentUriKey);

  for (const editor of window.visibleTextEditors) {
    if (editor.document.uri.toString() !== documentUriKey) continue;

    if (!pending || pending.candidates.length === 0) {
      editor.setDecorations(pendingCompositionDecoration, []);
      continue;
    }

    const maxOffset = documentLength(editor.document);
    const ranges: Range[] = [];

    const seen = new Set<string>();

    for (const candidate of pending.candidates) {
      const startOffset = candidate.startOffset;
      const endOffset = startOffset + candidate.length;

      if (candidate.length <= 0) continue;
      if (startOffset < 0) continue;
      if (endOffset > maxOffset) continue;

      const key = `${startOffset}:${endOffset}`;
      if (seen.has(key)) continue;
      seen.add(key);

      ranges.push(
        new Range(
          editor.document.positionAt(startOffset),
          editor.document.positionAt(endOffset),
        ),
      );
    }

    editor.setDecorations(pendingCompositionDecoration, ranges);
  }
}

function clearAllPendingCompositionDecorations() {
  pendingCompositionsByDocument.clear();
  for (const editor of window.visibleTextEditors) {
    editor.setDecorations(pendingCompositionDecoration, []);
  }
}

function getPureSingleCharInsertion(
  changes: readonly TextDocumentContentChangeEvent[],
): string | null {
  if (changes.length === 0) return null;
  const first = changes[0];
  if (!first) return null;
  if (first.rangeLength !== 0) return null;
  if (first.text.length !== 1) return null;

  const ch = first.text;
  if (!changes.every((c) => c.rangeLength === 0 && c.text === ch)) return null;
  return ch;
}

function getInsertionOffsetsInFinalDocument(
  changes: readonly TextDocumentContentChangeEvent[],
): number[] {
  // `rangeOffset` is relative to the document *before* the event.
  // For a pure insertion event (no deletions), we can translate offsets
  // into the final document by accumulating prior insert deltas.
  const sorted = [...changes].sort((a, b) => a.rangeOffset - b.rangeOffset);
  let cumulativeDelta = 0;
  const offsets: number[] = [];

  for (const change of sorted) {
    const delta = change.text.length - change.rangeLength;
    offsets.push(change.rangeOffset + cumulativeDelta);
    cumulativeDelta += delta;
  }

  return offsets;
}

function compositionHandler(): Disposable {
  let applying = false;
  return workspace.onDidChangeTextDocument(async (event) => {
    if (applying) return;
    if (event.document.languageId !== "koto") return;

    changeSeq += 1;

    const changes = [...event.contentChanges].sort(
      (a, b) => a.rangeOffset - b.rangeOffset,
    );

    const documentUriKey = event.document.uri.toString();

    const insertedChar = getPureSingleCharInsertion(changes);
    if (!insertedChar) {
      // Any other text edit invalidates pending compositions.
      if (pendingCompositionsByDocument.has(documentUriKey)) {
        pendingCompositionsByDocument.delete(documentUriKey);
        renderPendingCompositionDecoration(documentUriKey);
      }
      return;
    }

    const previousPending = pendingCompositionsByDocument.get(documentUriKey);

    // We only allow progressing an in-flight composition if it was started on the
    // immediately previous change event for this document.
    const canContinue = previousPending?.seq === changeSeq - 1;

    const insertionOffsets = getInsertionOffsetsInFinalDocument(changes);

    let nextCandidates: PendingCompositionCandidate[] = [];

    if (canContinue && previousPending) {
      // Extend existing candidates with the newly inserted character, but only
      // when the character was inserted exactly at the end of the candidate span.
      // This prevents continuing a composition if the user typed elsewhere.
      const insertionOffsetsOldDoc = new Set<number>(
        changes.map((c) => c.rangeOffset),
      );

      const extended: PendingCompositionCandidate[] = [];
      for (const candidate of previousPending.candidates) {
        const expectedInsertOffsetOldDoc =
          candidate.startOffset + candidate.length;
        if (!insertionOffsetsOldDoc.has(expectedInsertOffsetOldDoc)) continue;

        const mappedStart = mapOffsetThroughChanges(
          candidate.startOffset,
          changes,
        );
        if (mappedStart === null) continue;

        const nextNode = candidate.node.children.get(insertedChar);
        if (!nextNode) continue;

        extended.push({
          startOffset: mappedStart,
          node: nextNode,
          length: candidate.length + 1,
        });
      }

      nextCandidates = extended;
    }

    if (nextCandidates.length === 0) {
      // Either there was no pending composition, it wasn't immediate, or the
      // character didn't extend any existing candidates. Start fresh.
      const firstNode = compositionTrie.children.get(insertedChar);
      if (!firstNode) {
        if (previousPending) {
          pendingCompositionsByDocument.delete(documentUriKey);
          renderPendingCompositionDecoration(documentUriKey);
        }
        return;
      }

      nextCandidates = insertionOffsets.map((startOffset) => ({
        startOffset,
        node: firstNode,
        length: 1,
      }));
    }

    // Check which candidates form a complete composition key.
    const replacements: { startOffset: number; range: Range; value: string }[] =
      [];
    for (const candidate of nextCandidates) {
      if (!candidate.node.value) continue;

      const startOffset = candidate.startOffset;
      const endOffset = startOffset + candidate.length;
      if (startOffset < 0 || endOffset < 0) continue;
      if (endOffset > documentLength(event.document)) continue;

      const range = new Range(
        event.document.positionAt(startOffset),
        event.document.positionAt(endOffset),
      );
      const keyText = event.document.getText(range);
      const value = compositionMap.get(keyText);
      if (!value) continue;

      replacements.push({ startOffset, range, value });
    }

    if (replacements.length > 0) {
      // A composition applied; clear any pending underline/composition tracking.
      pendingCompositionsByDocument.delete(documentUriKey);
      renderPendingCompositionDecoration(documentUriKey);
    } else {
      // Keep tracking for exactly one event; any other edit cancels.
      pendingCompositionsByDocument.set(documentUriKey, {
        seq: changeSeq,
        candidates: nextCandidates,
      });
      renderPendingCompositionDecoration(documentUriKey);
      return;
    }

    replacements.sort((a, b) => b.startOffset - a.startOffset);

    const edit = new WorkspaceEdit();
    for (const { range, value } of replacements) {
      edit.replace(event.document.uri, range, value);
    }
    try {
      applying = true;
      await workspace.applyEdit(edit);
    } finally {
      applying = false;
    }
  });
}

export function registerComposition(): readonly Disposable[] {
  pendingCompositionDecoration = window.createTextEditorDecorationType({
    textDecoration: "underline",
  });

  return [
    pendingCompositionDecoration,
    window.onDidChangeActiveTextEditor(clearAllPendingCompositionDecorations),
    commands.registerCommand(
      "koto.cancelComposition",
      clearAllPendingCompositionDecorations,
    ),
    compositionHandler(),
  ];
}
