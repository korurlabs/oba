import type { Violation } from "@/features/changes/schemas";

import type { EditorPanel } from "../hooks/use-editor-params";

/** The side panel a document opens with when the URL names none. */
export const DEFAULT_PANEL: EditorPanel = "links";

/** The side panels, in tab order. */
export const PANELS: readonly { id: EditorPanel; label: string }[] = [
  { id: "links", label: "Links" },
  { id: "history", label: "History" },
  { id: "checks", label: "Checks" },
];

/** The panel a tab value opens; null for a value no tab has. */
export function panelOf(value: string): EditorPanel | null {
  return PANELS.find((panel) => panel.id === value)?.id ?? null;
}

/**
 * What the Checks panel says about the draft's violations: none means it
 * may be submitted; null means the server did not check it, which happens
 * only for a draft that is not the viewer's.
 */
export function checksText(violations: readonly Violation[] | null | undefined): string {
  if (violations == null) return "Kaya checks only your own open change.";
  if (violations.length === 0) return "Your change breaks no rule, so it may be submitted.";
  return violations.length === 1
    ? "Your change breaks a rule; a submit refuses it until it is fixed."
    : `Your change breaks ${violations.length} rules; a submit refuses it until they are fixed.`;
}
