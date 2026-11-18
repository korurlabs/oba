"use client";

import { LoadError } from "@oba/ui/sky/page-states";
import { Violations } from "@/features/changes/components/violations";
import { useChange } from "@/features/changes/hooks/use-changes";
import { describeError } from "@/lib/api/error-text";

import { checksText } from "./document-panels-data";

export type ChecksPanelProps = {
  /** The viewer's draft of the file's owner; null when none is open. */
  draftId: string | null;
};

/**
 * The rules the viewer's draft breaks as a submit and an approval would
 * check them now: paths, sizes, namespaces and composition. A save
 * reloads the change, so the list follows each save.
 */
export function ChecksPanel({ draftId }: ChecksPanelProps) {
  const change = useChange(draftId ?? "");
  if (draftId === null) {
    return (
      <p className="text-muted-foreground text-sm">
        Checks run on your own draft. Edit this file to start one.
      </p>
    );
  }
  const detail = change.data?.data ?? undefined;
  if (change.error !== null && detail === undefined) {
    return (
      <LoadError
        title="Could not load the checks"
        message={describeError(change.error)}
        onRetry={() => void change.refetch()}
      />
    );
  }
  if (detail === undefined) {
    return <div className="bg-muted h-24 animate-pulse rounded-md" aria-busy="true" />;
  }
  const violations = detail.violations ?? [];
  if (violations.length === 0) {
    return <p className="text-muted-foreground text-sm">{checksText(detail.violations)}</p>;
  }
  return <Violations violations={violations} title={checksText(violations)} />;
}
