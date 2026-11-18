"use client";

import { EmptyState, LoadError, TableLoading } from "@oba/ui/sky/page-states";
import { FileDiff } from "@/features/changes/components/diff-view";
import { useVersionDiff } from "@/features/versions/hooks/use-versions";
import type { FileDiffResponse } from "@/features/versions/schemas";
import { describeError } from "@/lib/api/error-text";

export type CompareViewProps = {
  /** The newer side: the current version or the viewer's draft. */
  versionId: string;
  /** The older version it is compared with. */
  against: string;
  path: string;
  /** "the current version" or "your draft", for the texts. */
  label: string;
};

/** The unified diff of one file between an older version and the current one or a draft. */
export function CompareView({ versionId, against, path, label }: CompareViewProps) {
  const diff = useVersionDiff(versionId, against, path);
  if (diff.error !== null && diff.data === undefined) {
    return (
      <LoadError
        title="Could not load the comparison"
        message={describeError(diff.error)}
        onRetry={() => void diff.refetch()}
      />
    );
  }
  if (diff.data === undefined) return <TableLoading label="Loading the comparison" />;
  const files = diff.data.data ?? [];
  if (files.length === 0) {
    return (
      <EmptyState
        title="No difference"
        description={`This file is the same in ${label} and in the version you opened.`}
      />
    );
  }
  return (
    <div className="flex flex-col gap-3">
      <p className="text-muted-foreground text-sm">From the version you opened to {label}.</p>
      {files.map((file: FileDiffResponse) => (
        <FileDiff key={file.path} file={file} />
      ))}
    </div>
  );
}
