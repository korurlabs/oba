/**
 * Which syntax the editor highlights for a path. Config files keep their
 * bytes exactly, so the choice only colours the text; it never changes it.
 */

export type EditorLanguage = "markdown" | "json" | "yaml" | "python" | "shell" | "text";

const BY_EXTENSION: Readonly<Record<string, EditorLanguage>> = {
  md: "markdown",
  markdown: "markdown",
  mdc: "markdown",
  json: "json",
  jsonc: "json",
  yaml: "yaml",
  yml: "yaml",
  py: "python",
  sh: "shell",
  bash: "shell",
  zsh: "shell",
};

/** The language of a path by its extension; a hook script without one is read as shell. */
export function languageOf(path: string): EditorLanguage {
  const name = path.split("/").pop() ?? "";
  const dot = name.lastIndexOf(".");
  if (dot > 0) {
    const found = BY_EXTENSION[name.slice(dot + 1).toLowerCase()];
    if (found) return found;
  }
  if (/^\.(agents|claude)\/hooks\//.test(path) && dot <= 0) return "shell";
  return "text";
}

/** Whether a path is Markdown, so its document offers a preview. */
export function isMarkdownPath(path: string): boolean {
  return languageOf(path) === "markdown";
}
