"use client";

import { ToggleGroup, ToggleGroupItem } from "@oba/ui/sky/toggle-group";

import { EDITOR_VIEWS, type EditorView } from "../hooks/use-editor-params";

const VIEW_LABELS: Readonly<Record<EditorView, string>> = {
  edit: "Edit",
  preview: "Preview",
  split: "Split",
};

/** Text, rendered Markdown, or both side by side; only Markdown documents offer it. */
export function ViewToggle({
  view,
  onChange,
}: {
  view: EditorView;
  onChange: (view: EditorView) => void;
}) {
  return (
    <ToggleGroup
      type="single"
      variant="outline"
      size="sm"
      value={view}
      onValueChange={(value: string) => {
        const next = EDITOR_VIEWS.find((entry: EditorView) => entry === value);
        if (next) onChange(next);
      }}
      aria-label="View"
    >
      {EDITOR_VIEWS.map((entry: EditorView) => (
        <ToggleGroupItem key={entry} value={entry} aria-label={VIEW_LABELS[entry]}>
          {VIEW_LABELS[entry]}
        </ToggleGroupItem>
      ))}
    </ToggleGroup>
  );
}
