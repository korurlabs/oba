"use client";

import { EmptyState, LoadError, TableLoading } from "@oba/ui/sky/page-states";
import { describeError } from "@/lib/api/error-text";

import type { AgentTool, ToolMappingResponse } from "../schemas";
import { sentence } from "./mapping-groups";
import { ScopeCountNotes } from "./scope-count-notes";
import { TOOL_LABELS } from "./tool-labels";
import { ToolMappingTable } from "./tool-mapping-table";
import { ToolNotDelivered } from "./tool-not-delivered";

export type ToolViewBodyProps = {
  tool: AgentTool;
  mappings: readonly ToolMappingResponse[] | undefined;
  loading: boolean;
  error: unknown;
  onRetry: () => void;
  scope: string | null;
  versionCount: number;
};

/**
 * One tool's view below its tab: how it finds the sources, the paths it reads with their
 * state in the scope, and what it reads that Kaya does not deliver.
 */
export function ToolViewBody({
  tool,
  mappings,
  loading,
  error,
  onRetry,
  scope,
  versionCount,
}: ToolViewBodyProps) {
  if (error !== null && error !== undefined && !mappings) {
    return (
      <LoadError
        title={`Could not load what ${TOOL_LABELS[tool]} reads`}
        message={describeError(error)}
        onRetry={onRetry}
      />
    );
  }
  if (loading || !mappings) {
    return <TableLoading label={`Loading what ${TOOL_LABELS[tool]} reads`} />;
  }
  const mapping = mappings.find((entry: ToolMappingResponse) => entry.tool === tool);
  if (!mapping) {
    return (
      <EmptyState
        title={`Kaya has no path table for ${TOOL_LABELS[tool]}`}
        description="Pick another tool, or By kind for every path Kaya delivers."
      />
    );
  }
  return (
    <section aria-label={`What ${mapping.name} reads`} className="flex flex-col gap-4">
      <p className="text-muted-foreground text-sm">{sentence(mapping.summary)}</p>
      <ScopeCountNotes
        scope={scope}
        versionCount={versionCount}
        withScope={`see which of these paths it gives ${mapping.name}`}
      />
      {mapping.reads.length === 0 ? (
        <p className="text-muted-foreground text-sm">
          {mapping.name} reads none of the paths Kaya delivers.
        </p>
      ) : (
        <ToolMappingTable reads={mapping.reads} scope={scope} />
      )}
      <ToolNotDelivered toolName={mapping.name} gaps={mapping.notDelivered} />
    </section>
  );
}
