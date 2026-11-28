"use client";

import { createSerializer, parseAsStringEnum, useQueryState } from "nuqs";
import { useId } from "react";

import { ToggleGroup, ToggleGroupItem } from "@oba/ui/sky/toggle-group";

import { AgentToolSchema, type AgentTool } from "../schemas";
import { TOOL_LABELS } from "./tool-labels";

/** The URL param that keeps the picked tool; without it the page shows the table by kind. */
export const TOOL_PARAM = "tool";

/** The toggle's value for the table by kind, which has no `tool` in the URL. */
export const BY_KIND = "by-kind";

const toolParser = parseAsStringEnum<AgentTool>([...AgentToolSchema.options]);

/** The search string of a view, so a link opens the same view; null drops `tool`. */
export const serializeToolView = createSerializer({ [TOOL_PARAM]: toolParser });

/** The tool in view, kept in the URL; null is the table by kind. */
export function useToolView() {
  return useQueryState(TOOL_PARAM, toolParser);
}

/**
 * What a toggle value means: a tool, null for the table by kind, or undefined
 * for no change, which a single toggle group sends when its pressed item is
 * pressed again.
 */
export function toolFromToggle(value: string): AgentTool | null | undefined {
  if (value === BY_KIND) return null;
  const parsed = AgentToolSchema.safeParse(value);
  return parsed.success ? parsed.data : undefined;
}

/**
 * "By kind" and one item per tool. The items keep a small gap so that they
 * wrap onto a second line at phone width instead of widening the page.
 */
export function ToolToggle({
  tool,
  onChange,
}: {
  tool: AgentTool | null;
  onChange: (tool: AgentTool | null) => void;
}) {
  const labelId = useId();
  return (
    <div className="flex flex-col gap-1 text-xs font-medium">
      <span id={labelId} className="text-muted-foreground">
        View
      </span>
      <ToggleGroup
        type="single"
        variant="outline"
        size="sm"
        spacing={1}
        className="flex-wrap"
        value={tool ?? BY_KIND}
        onValueChange={(value: string) => {
          const next = toolFromToggle(value);
          if (next !== undefined) onChange(next);
        }}
        aria-labelledby={labelId}
      >
        <ToggleGroupItem value={BY_KIND}>By kind</ToggleGroupItem>
        {AgentToolSchema.options.map((entry: AgentTool) => (
          <ToggleGroupItem key={entry} value={entry}>
            {TOOL_LABELS[entry]}
          </ToggleGroupItem>
        ))}
      </ToggleGroup>
    </div>
  );
}
