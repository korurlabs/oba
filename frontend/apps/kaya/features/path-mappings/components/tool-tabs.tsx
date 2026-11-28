"use client";

import { createSerializer, parseAsStringEnum, useQueryState } from "nuqs";

import { Tabs, TabsList, TabsTrigger } from "@oba/ui/sky/tabs";

import { AgentToolSchema, type AgentTool } from "../schemas";
import { TOOL_LABELS } from "./tool-labels";

/** The URL param that keeps the picked tool; without it the page shows the table by kind. */
export const TOOL_PARAM = "tool";

/** The tab value of the table by kind, which has no `tool` in the URL. */
export const BY_KIND = "by-kind";

const toolParser = parseAsStringEnum<AgentTool>([...AgentToolSchema.options]);

/** The search string of a view, so a link opens the same view; null drops `tool`. */
export const serializeToolView = createSerializer({ [TOOL_PARAM]: toolParser });

/** The tool in view, kept in the URL; null is the table by kind. */
export function useToolView() {
  return useQueryState(TOOL_PARAM, toolParser);
}

/** What a tab value means: a tool, null for the table by kind, or undefined for an unknown value. */
export function toolFromTab(value: string): AgentTool | null | undefined {
  if (value === BY_KIND) return null;
  const parsed = AgentToolSchema.safeParse(value);
  return parsed.success ? parsed.data : undefined;
}

/**
 * "By kind" and one tab per tool, for the list toolbar: each view is
 * another table, so the views are tabs rather than a filter.
 */
export function ToolTabs({
  tool,
  onChange,
}: {
  tool: AgentTool | null;
  onChange: (tool: AgentTool | null) => void;
}) {
  return (
    <Tabs
      value={tool ?? BY_KIND}
      onValueChange={(value: string) => {
        const next = toolFromTab(value);
        if (next !== undefined) onChange(next);
      }}
    >
      <TabsList aria-label="View" className="h-auto flex-wrap">
        <TabsTrigger value={BY_KIND}>By kind</TabsTrigger>
        {AgentToolSchema.options.map((entry: AgentTool) => (
          <TabsTrigger key={entry} value={entry}>
            {TOOL_LABELS[entry]}
          </TabsTrigger>
        ))}
      </TabsList>
    </Tabs>
  );
}
