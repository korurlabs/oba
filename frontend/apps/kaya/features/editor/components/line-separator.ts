/**
 * The line separator a file is saved with: CRLF when the file uses it, else
 * LF. CodeMirror always reports its text joined with LF, so a CRLF file goes
 * into the editor with LF lines and every edit comes back with CRLF lines;
 * otherwise the first keystroke would rewrite every line ending of the file.
 */
export function lineSeparatorOf(text: string): "\r\n" | "\n" {
  return text.includes("\r\n") ? "\r\n" : "\n";
}

/** The text as CodeMirror holds it: every CRLF line ending as LF. */
export function toEditorText(text: string): string {
  return text.replace(/\r\n/g, "\n");
}

/** The editor's LF text with the file's own line separator put back. */
export function fromEditorText(text: string, separator: "\r\n" | "\n"): string {
  return separator === "\r\n" ? text.replace(/\n/g, "\r\n") : text;
}

/** The separator kept for one file while it is edited. */
export type KeptSeparator = { path: string; separator: "\r\n" | "\n" };

/**
 * The separator to save `value` of `path` with. A file read with CRLF lines
 * keeps CRLF even when the edit removes every line break, so the lines typed
 * next are saved as the file has them; another path starts over.
 */
export function keptSeparator(
  kept: KeptSeparator | null,
  path: string,
  value: string
): KeptSeparator {
  const live = lineSeparatorOf(value);
  if (kept === null || kept.path !== path) return { path, separator: live };
  return live === "\r\n" ? { path, separator: live } : kept;
}
