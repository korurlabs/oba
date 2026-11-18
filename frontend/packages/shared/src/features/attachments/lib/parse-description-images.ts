/**
 * Extracts every value of the `data-attachment-id` attribute from `<img>`
 * tags inside the supplied HTML string.
 *
 * The regex matches an `<img>` tag (word-boundary on `img`, then any
 * characters that are not a closing `>`) and captures the value of a
 * double-quoted `data-attachment-id` attribute. `<img>` tags without
 * the attribute are ignored. Returns an empty array when no matches
 * are found.
 *
 * Implementation note: a regex is preferred over a DOM parse here so the
 * helper works in non-browser contexts (Node, SSR, tests) without pulling
 * in a DOM polyfill. `String.prototype.matchAll` allocates a fresh
 * iterator state per call, so the helper is reentrant under concurrent
 * SSR rendering and nested calls -- avoiding the shared-`lastIndex`
 * footgun of using `.exec()` in a loop on a module-level `/g` regex.
 */
const ATTACHMENT_ID_REGEX = /<img\b[^>]*\sdata-attachment-id="([^"]+)"/gi;

export function parseAttachmentIds(html: string): string[] {
  if (!html) {
    return [];
  }
  const ids: string[] = [];
  for (const match of html.matchAll(ATTACHMENT_ID_REGEX)) {
    const id = match[1];
    if (id !== undefined) {
      ids.push(id);
    }
  }
  return ids;
}
