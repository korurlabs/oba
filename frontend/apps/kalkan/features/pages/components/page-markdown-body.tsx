"use client";

import { MarkdownEditor } from "@oba/shared/components/markdown-editor-lazy";

/** The editor never changes a read-only body, so its change callback has nothing to do. */
const ignoreChange = (): undefined => undefined;

/**
 * A page's Markdown body rendered read-only by the shared editor, so a page
 * reads exactly as its editor will show it.
 */
export function PageMarkdownBody({ markdown, label }: { markdown: string; label: string }) {
  return (
    <section aria-label={label}>
      <MarkdownEditor value={markdown} onChange={ignoreChange} readOnly />
    </section>
  );
}
