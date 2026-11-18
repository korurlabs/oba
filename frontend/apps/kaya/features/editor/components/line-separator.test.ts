import { describe, expect, test } from "bun:test";
import { EditorState } from "@uiw/react-codemirror";

import { fromEditorText, keptSeparator, lineSeparatorOf, toEditorText } from "./line-separator";

/** Types `insert` at `at` in a CodeMirror state and returns the text as the editor reports it. */
function typeInEditor(doc: string, at: number, insert: string): string {
  const state = EditorState.create({ doc });
  return state.update({ changes: { from: at, insert } }).state.doc.toString();
}

describe("CRLF round trip", () => {
  test("an edit of a CRLF file is saved with CRLF on every line", () => {
    const saved = "# Rules\r\n\r\n- one\r\n- two\r\n";
    const separator = lineSeparatorOf(saved);
    const shown = toEditorText(saved);
    expect(shown).toBe("# Rules\n\n- one\n- two\n");

    const reported = typeInEditor(shown, shown.length, "- three\n");
    expect(fromEditorText(reported, separator)).toBe(
      "# Rules\r\n\r\n- one\r\n- two\r\n- three\r\n"
    );
  });

  test("an untouched CRLF file comes back byte for byte", () => {
    const saved = "a\r\nb\r\n";
    const reported = EditorState.create({ doc: toEditorText(saved) }).doc.toString();
    expect(fromEditorText(reported, lineSeparatorOf(saved))).toBe(saved);
  });

  test("an LF file stays LF", () => {
    const saved = "a\nb\n";
    const reported = typeInEditor(toEditorText(saved), 0, "x\n");
    expect(fromEditorText(reported, lineSeparatorOf(saved))).toBe("x\na\nb\n");
  });
});

describe("keptSeparator", () => {
  test("a CRLF file keeps CRLF after the edit removes every line break", () => {
    const read = keptSeparator(null, "AGENTS.md", "a\r\nb");
    expect(read.separator).toBe("\r\n");
    expect(keptSeparator(read, "AGENTS.md", "ab").separator).toBe("\r\n");
  });

  test("a file loaded after an empty start takes the file's separator", () => {
    const empty = keptSeparator(null, "AGENTS.md", "");
    expect(keptSeparator(empty, "AGENTS.md", "a\r\nb").separator).toBe("\r\n");
  });

  test("another path starts over", () => {
    const read = keptSeparator(null, "AGENTS.md", "a\r\nb");
    expect(keptSeparator(read, "CLAUDE.md", "a\nb")).toEqual({
      path: "CLAUDE.md",
      separator: "\n",
    });
  });
});
