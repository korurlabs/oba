"use client";

import { ResizableHandle, ResizablePanel, ResizablePanelGroup } from "@/components/ui/resizable";

import type { EditorView } from "../hooks/use-editor-params";
import { CodeEditor } from "./code-editor";
import { isMarkdownPath } from "./editor-language";
import { MarkdownPreview } from "./markdown-preview";

export type DocumentBodyProps = {
  path: string;
  text: string;
  /** How a Markdown file shows; any other file always shows as text. */
  view: EditorView;
  /** Absent for a read-only document. */
  onChange?: (text: string) => void;
  onSave?: () => void;
  label: string;
};

/** The text of a document in the chosen view: the editor, the preview, or both. */
export function DocumentBody({ path, text, view, onChange, onSave, label }: DocumentBodyProps) {
  const readOnly = onChange === undefined;
  const editor = (
    <CodeEditor
      path={path}
      value={text}
      onChange={onChange}
      onSave={onSave}
      readOnly={readOnly}
      label={label}
      minHeight={readOnly ? undefined : "50vh"}
      maxHeight={readOnly ? "70vh" : undefined}
    />
  );
  const shown = isMarkdownPath(path) ? view : "edit";
  if (shown === "preview") return <MarkdownPreview text={text} />;
  if (shown === "split") {
    return (
      <ResizablePanelGroup orientation="horizontal" className="min-h-[50vh] rounded-md">
        <ResizablePanel defaultSize="50%" minSize="20%" className="min-w-0 pr-2">
          {editor}
        </ResizablePanel>
        <ResizableHandle withHandle />
        <ResizablePanel defaultSize="50%" minSize="20%" className="min-w-0 overflow-auto pl-2">
          <MarkdownPreview text={text} />
        </ResizablePanel>
      </ResizablePanelGroup>
    );
  }
  return editor;
}
