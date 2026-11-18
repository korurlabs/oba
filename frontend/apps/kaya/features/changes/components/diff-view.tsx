"use client";

import { Badge } from "@oba/ui/sky/badge";
import { EmptyState, LoadError, TableLoading } from "@oba/ui/sky/page-states";
import { describeError } from "@/lib/api/error-text";
import { cn } from "@/lib/utils";

import { useVersionDiff } from "@/features/versions/hooks/use-versions";
import type { FileChange, FileDiffResponse } from "@/features/versions/schemas";
import { diffLines, diffNotes, diffStats, type DiffLine, type DiffLineKind } from "./change-data";

const CHANGE_BADGES: Readonly<
  Record<FileChange, { label: string; variant: "success" | "info" | "error" }>
> = {
  ADDED: { label: "Added", variant: "success" },
  MODIFIED: { label: "Changed", variant: "info" },
  DELETED: { label: "Deleted", variant: "error" },
};

const LINE_CLASSES: Readonly<Record<DiffLineKind, string>> = {
  header: "text-muted-foreground",
  hunk: "bg-info-light text-muted-foreground",
  added: "bg-success-light",
  removed: "bg-error-light",
  context: "",
  note: "text-muted-foreground italic",
};

/** One path's change: its kind, line counts, notes and the coloured unified diff. */
export function FileDiff({ file }: { file: FileDiffResponse }) {
  const badge = CHANGE_BADGES[file.change];
  const stats = diffStats(file.unifiedDiff);
  const notes = diffNotes(file);
  const lines = file.unifiedDiff
    ? diffLines(file.unifiedDiff).filter((line) => line.kind !== "header")
    : [];
  return (
    <section className="flex min-w-0 flex-col gap-2 rounded-md border" aria-label={file.path}>
      <header className="bg-muted/40 flex flex-wrap items-center gap-2 border-b px-3 py-2">
        <Badge variant={badge.variant}>{badge.label}</Badge>
        {(file.newMode ?? file.oldMode) === "EXECUTABLE" && (
          <Badge variant="warning">Executable</Badge>
        )}
        <code className="min-w-0 flex-1 font-mono text-sm break-all">{file.path}</code>
        {lines.length > 0 && (
          <span className="text-xs tabular-nums">
            <span className="text-success">+{stats.added}</span>{" "}
            <span className="text-error">-{stats.removed}</span>
          </span>
        )}
      </header>
      {notes.length > 0 && (
        <ul className="text-muted-foreground flex flex-col gap-0.5 px-3 text-sm">
          {notes.map((note: string) => (
            <li key={note} className="break-words">
              {note}
            </li>
          ))}
        </ul>
      )}
      {lines.length > 0 && (
        <pre className="max-h-[60vh] overflow-auto pb-2 font-mono text-xs leading-relaxed">
          {lines.map((line: DiffLine, index: number) => (
            <div
              key={index}
              className={cn("px-3 break-words whitespace-pre-wrap", LINE_CLASSES[line.kind])}
            >
              {line.text === "" ? " " : line.text}
            </div>
          ))}
        </pre>
      )}
    </section>
  );
}

export type DiffViewProps = {
  versionId: string;
  /** The version to compare with; the version's base when absent. */
  against?: string;
  /** What the comparison is against, for the empty text. */
  baseLabel: string;
};

/** Every changed path of a version against its base or another version, by path. */
export function DiffView({ versionId, against, baseLabel }: DiffViewProps) {
  const diff = useVersionDiff(versionId, against);
  const files = diff.data?.data ?? [];
  if (diff.error !== null && diff.data === undefined) {
    return (
      <LoadError
        title="Could not load the diff"
        message={describeError(diff.error)}
        onRetry={() => void diff.refetch()}
      />
    );
  }
  if (diff.data === undefined) return <TableLoading label="Loading the diff" />;
  if (files.length === 0) {
    return <EmptyState title="No file changed" description={`The files equal ${baseLabel}.`} />;
  }
  return (
    <div className="flex flex-col gap-4">
      <p className="text-muted-foreground text-sm">
        {files.length} {files.length === 1 ? "path changed" : "paths changed"} against {baseLabel}.
      </p>
      {files.map((file: FileDiffResponse) => (
        <FileDiff key={file.path} file={file} />
      ))}
    </div>
  );
}
