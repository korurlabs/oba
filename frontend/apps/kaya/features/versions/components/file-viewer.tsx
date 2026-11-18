"use client";

import { Link2 } from "lucide-react";

import { Badge } from "@oba/ui/sky/badge";
import { LoadError } from "@oba/ui/sky/page-states";
import { DocumentBody } from "@/features/editor/components/document-body";
import type { EditorView } from "@/features/editor/hooks/use-editor-params";
import { describeError } from "@/lib/api/error-text";

import { useVersionFile } from "../hooks/use-versions";
import {
  formatBytes,
  type FileOrigin,
  type ScopeFile,
} from "@/features/scope/components/scope-files";

/** Mode, size and always-on flag of a file, as badges. */
export function FileBadges({ file }: { file: ScopeFile }) {
  return (
    <div className="flex flex-wrap items-center gap-1.5">
      {file.mode === "EXECUTABLE" && <Badge variant="warning">Executable</Badge>}
      {file.mode === "SYMLINK" && <Badge variant="info">Symlink</Badge>}
      {file.alwaysOn === true && <Badge variant="secondary">Always on</Badge>}
      {file.size !== null && (
        <span className="text-muted-foreground text-xs">{formatBytes(file.size)}</span>
      )}
    </div>
  );
}

/**
 * A file's override markers as a badge: a mounted pack's file and the root
 * file it replaces, or a root rule narrowed for a mounted pack.
 */
export function OriginBadge({ origin }: { origin: FileOrigin }) {
  switch (origin.kind) {
    case "OVERRIDES":
      return (
        <span className="flex min-w-0 flex-wrap items-center gap-1.5">
          <Badge variant="info">Overrides</Badge>
          <code className="text-muted-foreground font-mono text-xs break-all">{origin.target}</code>
        </span>
      );
    case "NARROWED":
      return <Badge variant="secondary">{origin.label}</Badge>;
  }
}

/**
 * One file's content, read-only, highlighted by its path, and for Markdown
 * in the chosen view. A link shows its target and loads nothing; a file that
 * is not UTF-8 text is named as binary, since its base64 is no use to read.
 */
export function FileViewer({
  file,
  view = "edit",
  showHeader = true,
}: {
  file: ScopeFile;
  view?: EditorView;
  /** False where the document's own header already names the file. */
  showHeader?: boolean;
}) {
  const isLink = file.mode === "SYMLINK";
  const content = useVersionFile(isLink ? "" : file.versionId, file.path);
  const data = content.data?.data;

  return (
    <div className="flex min-w-0 flex-col gap-3">
      {showHeader && (
        <div className="flex flex-col gap-1.5">
          <code className="font-mono text-sm break-all">{file.path}</code>
          <FileBadges file={file} />
        </div>
      )}
      {isLink ? (
        <p className="flex items-center gap-2 text-sm">
          <Link2 className="text-primary size-4 shrink-0" aria-hidden />
          <span>
            Points to{" "}
            <code className="font-mono break-all">{file.target ?? "an unknown target"}</code>
          </span>
        </p>
      ) : content.error !== null && data === undefined ? (
        <LoadError
          title="Could not load the file"
          message={describeError(content.error)}
          onRetry={() => void content.refetch()}
        />
      ) : !data ? (
        <div className="bg-muted h-64 animate-pulse rounded-md" aria-busy="true" />
      ) : data.encoding === "BASE64" ? (
        <p className="text-muted-foreground text-sm">
          A binary file{data.size != null ? ` of ${formatBytes(data.size)}` : ""}; its content is
          not shown.
        </p>
      ) : (
        <DocumentBody
          path={file.path}
          text={data.content ?? ""}
          view={view}
          label={`Content of ${file.path}`}
        />
      )}
    </div>
  );
}
