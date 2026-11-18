"use client";

import { parseAsString, parseAsStringEnum, useQueryStates } from "nuqs";

export const EDITOR_VIEWS = ["edit", "preview", "split"] as const;
export type EditorView = (typeof EDITOR_VIEWS)[number];

export const EDITOR_PANELS = ["content", "links", "history", "checks"] as const;
export type EditorPanel = (typeof EDITOR_PANELS)[number];

/**
 * The document's state in the URL, beside `scope` and `draft`, so a link
 * opens the same view: `at` an older version shown read-only, `view` how a
 * Markdown file shows, `panel` the open panel (a side panel, or on a Rules
 * file's page one of its tabs, `content` among them), and `compare` the
 * version the shown one is compared with.
 */
export const editorParams = {
  at: parseAsString,
  view: parseAsStringEnum<EditorView>([...EDITOR_VIEWS]).withDefault("edit"),
  panel: parseAsStringEnum<EditorPanel>([...EDITOR_PANELS]),
  compare: parseAsString,
};

export function useEditorParams() {
  return useQueryStates(editorParams);
}
