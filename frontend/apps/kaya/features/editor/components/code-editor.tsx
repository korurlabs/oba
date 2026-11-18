"use client";

import dynamic from "next/dynamic";

export type CodeEditorProps = {
  /** The file's path, which picks the highlighted language. */
  path: string;
  value: string;
  onChange?: (value: string) => void;
  /** Ctrl+S or Cmd+S in the editor. */
  onSave?: () => void;
  readOnly?: boolean;
  /** The accessible name of the text area. */
  label: string;
  minHeight?: string;
  maxHeight?: string;
};

/**
 * The code editor of a document. CodeMirror needs the browser, and only the
 * document pages use it, so it loads in a chunk of its own after the page.
 */
export const CodeEditor = dynamic<CodeEditorProps>(() => import("./code-editor-view"), {
  ssr: false,
  loading: () => <div className="bg-muted h-64 animate-pulse rounded-md" aria-busy="true" />,
});
