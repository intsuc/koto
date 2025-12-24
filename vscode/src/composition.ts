import { Range, WorkspaceEdit, commands, window, workspace } from "vscode";
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

function documentKey(document: TextDocument): string {
  return document.uri.toString();
}

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

function clearPendingComposition(documentUriKey: string) {
  if (!pendingCompositionsByDocument.delete(documentUriKey)) return;
  renderPendingCompositionDecoration(documentUriKey);
}

function setPendingComposition(
  documentUriKey: string,
  candidates: PendingCompositionCandidate[],
) {
  pendingCompositionsByDocument.set(documentUriKey, {
    seq: changeSeq,
    candidates,
  });
  renderPendingCompositionDecoration(documentUriKey);
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

function insertionOffsetsOldDocument(
  changes: readonly TextDocumentContentChangeEvent[],
): Set<number> {
  return new Set<number>(changes.map((c) => c.rangeOffset));
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

function startCandidates(
  insertedChar: string,
  insertionOffsets: number[],
): PendingCompositionCandidate[] | null {
  const firstNode = compositionTrie.children.get(insertedChar);
  if (!firstNode) return null;

  return insertionOffsets.map((startOffset) => ({
    startOffset,
    node: firstNode,
    length: 1,
  }));
}

function extendCandidates(
  previous: PendingCompositions,
  insertedChar: string,
  changes: readonly TextDocumentContentChangeEvent[],
): PendingCompositionCandidate[] {
  const offsetsOldDoc = insertionOffsetsOldDocument(changes);
  const extended: PendingCompositionCandidate[] = [];

  for (const candidate of previous.candidates) {
    // Only continue if the user typed at the end of this candidate span.
    const expectedInsertOffsetOldDoc = candidate.startOffset + candidate.length;
    if (!offsetsOldDoc.has(expectedInsertOffsetOldDoc)) continue;

    const mappedStart = mapOffsetThroughChanges(candidate.startOffset, changes);
    if (mappedStart === null) continue;

    const nextNode = candidate.node.children.get(insertedChar);
    if (!nextNode) continue;

    extended.push({
      startOffset: mappedStart,
      node: nextNode,
      length: candidate.length + 1,
    });
  }

  return extended;
}

function findReplacements(
  document: TextDocument,
  candidates: PendingCompositionCandidate[],
) {
  const replacements: Array<{
    startOffset: number;
    range: Range;
    value: string;
  }> = [];
  const maxOffset = documentLength(document);

  for (const candidate of candidates) {
    const value = candidate.node.value;
    if (!value) continue;

    const startOffset = candidate.startOffset;
    const endOffset = startOffset + candidate.length;
    if (startOffset < 0 || endOffset < 0) continue;
    if (endOffset > maxOffset) continue;

    const range = new Range(
      document.positionAt(startOffset),
      document.positionAt(endOffset),
    );
    const keyText = document.getText(range);
    if (compositionMap.get(keyText) !== value) continue;

    replacements.push({ startOffset, range, value });
  }

  return replacements;
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

    const documentUriKey = documentKey(event.document);
    const insertedChar = getPureSingleCharInsertion(changes);
    if (!insertedChar) {
      clearPendingComposition(documentUriKey);
      return;
    }

    const previous = pendingCompositionsByDocument.get(documentUriKey);
    const canContinue = previous?.seq === changeSeq - 1;
    const insertionOffsetsFinal = getInsertionOffsetsInFinalDocument(changes);

    const extended =
      canContinue && previous
        ? extendCandidates(previous, insertedChar, changes)
        : [];

    const candidates =
      extended.length > 0
        ? extended
        : startCandidates(insertedChar, insertionOffsetsFinal);

    if (!candidates || candidates.length === 0) {
      clearPendingComposition(documentUriKey);
      return;
    }

    const replacements = findReplacements(event.document, candidates);
    if (replacements.length === 0) {
      setPendingComposition(documentUriKey, candidates);
      return;
    }

    clearPendingComposition(documentUriKey);

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
