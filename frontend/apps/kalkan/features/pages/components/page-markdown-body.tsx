"use client";

import "@oba/shared/styles/markdown-editor.css";
import { MarkdownEditor } from "@oba/shared/components/markdown-editor-lazy";

/** The editor never changes a read-only body, so its change callback has nothing to do. */
const ignoreChange = (): undefined => undefined;

/**
 * A page's Markdown body rendered read-only by the shared editor, so a page
 * reads exactly as its editor will show it. The editor has no reading mode
 * of its own: disabled, it keeps its frame, its toolbar and a dimmed text.
 * The classes below drop the frame and the toolbar (the only direct child
 * that holds buttons) and keep the text at full strength.
 */
export function PageMarkdownBody({ markdown, label }: { markdown: string; label: string }) {
  return (
    <section aria-label={label}>
      <MarkdownEditor
        value={markdown}
        onChange={ignoreChange}
        disabled
        className="border-0 [&_.wysiwyg-editor]:cursor-auto [&_.wysiwyg-editor]:opacity-100 [&>div:has(>button)]:hidden"
      />
    </section>
  );
}
