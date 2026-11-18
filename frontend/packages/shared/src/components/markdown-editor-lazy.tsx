"use client";

import type * as React from "react";
import dynamic from "next/dynamic";
import type { FieldValues } from "react-hook-form";
import { cn } from "@oba/ui/lib/utils";
import type { MarkdownEditorProps } from "./markdown-editor";

/**
 * Lazy boundary for the Tiptap-based MarkdownEditor.
 *
 * The underlying editor statically imports `@tiptap/react`, StarterKit and a
 * stack of Tiptap extensions (Link, TaskList, Image, CodeBlockLowlight,
 * lowlight, tiptap-markdown, ...). Importing it eagerly pulls all of that into
 * the route's main bundle even though the editor only renders inside a
 * form. `next/dynamic({ ssr: false })` splits it into its own chunk
 * that loads on first render of the editor, with a lightweight placeholder in
 * the meantime.
 *
 * The dynamic import erases the generic over `TFieldValues`, so this thin
 * generic wrapper restores it: consumers keep a single import path
 * (`@oba/shared/components/markdown-editor-lazy`) and full prop typing.
 */
const MarkdownEditorDynamic = dynamic(
  () =>
    import("./markdown-editor").then((mod) => mod.MarkdownEditor) as Promise<
      React.ComponentType<MarkdownEditorProps>
    >,
  {
    ssr: false,
    loading: () => (
      <div
        className={cn(
          "border-input bg-background text-muted-foreground flex min-h-[200px] w-full items-center rounded-md border px-3 py-2 text-sm",
        )}
        aria-busy="true"
      />
    ),
  },
);

export function MarkdownEditor<TFieldValues extends FieldValues = FieldValues>(
  props: MarkdownEditorProps<TFieldValues>,
) {
  return <MarkdownEditorDynamic {...(props as MarkdownEditorProps)} />;
}
