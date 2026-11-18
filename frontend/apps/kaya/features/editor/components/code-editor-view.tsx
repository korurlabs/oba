"use client";

import { useCallback, useMemo, useState, type KeyboardEvent } from "react";
import CodeMirror, { EditorView, type Extension } from "@uiw/react-codemirror";
import { json } from "@codemirror/lang-json";
import { markdown } from "@codemirror/lang-markdown";
import { python } from "@codemirror/lang-python";
import { yaml, yamlFrontmatter } from "@codemirror/lang-yaml";
import { StreamLanguage } from "@codemirror/language";
import { shell } from "@codemirror/legacy-modes/mode/shell";

import { languageOf, type EditorLanguage } from "./editor-language";
import { fromEditorText, keptSeparator, toEditorText, type KeptSeparator } from "./line-separator";
import type { CodeEditorProps } from "./code-editor";

function languageExtension(language: EditorLanguage): Extension[] {
  switch (language) {
    case "markdown":
      return [yamlFrontmatter({ content: markdown() })];
    case "json":
      return [json()];
    case "yaml":
      return [yaml()];
    case "python":
      return [python()];
    case "shell":
      return [StreamLanguage.define(shell)];
    case "text":
      return [];
  }
}

const KAYA_THEME = EditorView.theme({
  "&": { fontSize: "0.75rem", backgroundColor: "transparent" },
  ".cm-content": { fontFamily: "var(--font-mono), ui-monospace, monospace" },
  ".cm-gutters": {
    backgroundColor: "var(--muted)",
    color: "var(--muted-foreground)",
    borderRight: "1px solid var(--border)",
  },
  "&.cm-focused": { outline: "none" },
});

/**
 * The CodeMirror editor itself, loaded only in the browser. CodeMirror holds
 * and reports its text with LF lines, so a file written with CRLF lines goes
 * in with LF and every edit is handed back with CRLF, and the file is saved
 * with the line endings it has; Ctrl+S (Cmd+S) saves instead of the
 * browser's save, and Escape then Tab moves focus out of the editor.
 */
export default function CodeEditorView({
  path,
  value,
  onChange,
  onSave,
  readOnly = false,
  label,
  minHeight = "50vh",
  maxHeight,
}: CodeEditorProps) {
  const [kept, setKept] = useState<KeptSeparator>(() => keptSeparator(null, path, value));
  const next = keptSeparator(kept, path, value);
  if (next.path !== kept.path || next.separator !== kept.separator) setKept(next);
  const separator = next.separator;
  const language = languageOf(path);
  const editorText = useMemo(() => toEditorText(value), [value]);
  const onEditorChange = useCallback(
    (text: string) => onChange?.(fromEditorText(text, separator)),
    [onChange, separator]
  );

  const extensions = useMemo(
    () => [
      ...languageExtension(language),
      EditorView.lineWrapping,
      EditorView.contentAttributes.of({ "aria-label": label }),
      KAYA_THEME,
    ],
    [language, label]
  );

  // The editor binds no Mod-s, so the key reaches the wrapper; it saves
  // instead of opening the browser's save dialog.
  const onKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    if (event.key !== "s" || !(event.ctrlKey || event.metaKey) || event.altKey) return;
    event.preventDefault();
    onSave?.();
  };

  return (
    <div onKeyDown={onKeyDown}>
      <CodeMirror
        value={editorText}
        onChange={onEditorChange}
        readOnly={readOnly}
        editable={!readOnly}
        extensions={extensions}
        theme="light"
        minHeight={minHeight}
        maxHeight={maxHeight}
        basicSetup={{ foldGutter: false, highlightActiveLine: !readOnly }}
        className="overflow-hidden rounded-md border"
      />
    </div>
  );
}
