"use client";

import { useState, type ReactNode } from "react";

import { LoadError, TableLoading } from "@oba/ui/sky/page-states";
import { useScope } from "@/features/scope/hooks/use-scope";
import { useScopeContent, type ScopeContent } from "@/features/scope/hooks/use-scope-content";
import { scopeValue } from "@/features/scope/components/scope-data";
import type { ScopeFile } from "@/features/scope/components/scope-files";
import { ListToolbar } from "@/features/scope/components/list-toolbar";
import { ScopeSelect } from "@/features/scope/components/scope-select";
import { describeError } from "@/lib/api/error-text";

import { PATH_MAPPING_VERSIONS_MAX, usePathMappings } from "../hooks/use-path-mappings";
import { useToolMappings } from "../hooks/use-tool-mappings";
import type { PathMappingResponse } from "../schemas";
import { PathMappingTable } from "./path-mapping-table";
import { ScopeCountNotes } from "./scope-count-notes";
import { ToolToggle, useToolView } from "./tool-toggle";
import { ToolViewBody } from "./tool-view";

/** Which versions the counts read: still loading, or the scope's versions (none for no scope). */
export type CountedVersions =
  | { status: "loading" }
  | { status: "error"; error: unknown; retry: () => void }
  | { status: "ready"; versionIds: string[] };

/**
 * The versions whose files a scope shows: every version its release or pack
 * version takes a file from. A scope that holds nothing counts no version, so
 * its counts read as unknown and the patterns still show.
 */
export function countedVersions(
  scopeLoading: boolean,
  hasScope: boolean,
  content: ScopeContent
): CountedVersions {
  if (scopeLoading) return { status: "loading" };
  if (!hasScope) return { status: "ready", versionIds: [] };
  if (content.status === "loading") return { status: "loading" };
  if (content.status === "error") {
    return { status: "error", error: content.error, retry: content.retry };
  }
  if (content.status === "empty") return { status: "ready", versionIds: [] };
  return {
    status: "ready",
    versionIds: [...new Set(content.files.map((file: ScopeFile) => file.versionId))],
  };
}

/** The ids the read sends: none above the server's limit, which then answers without counts. */
export function requestedVersionIds(versionIds: readonly string[]): string[] {
  return versionIds.length > PATH_MAPPING_VERSIONS_MAX ? [] : [...versionIds];
}

export type PathMappingBodyProps = {
  rows: readonly PathMappingResponse[] | undefined;
  loading: boolean;
  error: unknown;
  onRetry: () => void;
  scope: string | null;
  /** The number of versions the scope holds, to say why counts are missing above the limit. */
  versionCount: number;
  /** The toolbar's search, which narrows the rows; empty keeps every row. */
  search?: string;
};

/** The toolbar's search, then the table, or its loading or error state. */
function SearchedPathMappingBody(props: Omit<PathMappingBodyProps, "search">) {
  const [search, setSearch] = useState("");
  return (
    <div className="flex flex-col gap-4">
      <ListToolbar
        search={{
          value: search,
          onChange: setSearch,
          label: "Search the path mapping",
          placeholder: "Search by path, kind or page",
        }}
      />
      <PathMappingBody {...props} search={search} />
    </div>
  );
}

/** The table, or its loading or error state; the rows are static, so there is no empty state. */
export function PathMappingBody({
  rows,
  loading,
  error,
  onRetry,
  scope,
  versionCount,
  search = "",
}: PathMappingBodyProps) {
  if (error !== null && error !== undefined && !rows) {
    return (
      <LoadError
        title="Could not load the path mapping"
        message={describeError(error)}
        onRetry={onRetry}
      />
    );
  }
  if (loading || !rows) return <TableLoading label="Loading the path mapping" />;
  return (
    <div className="flex flex-col gap-3">
      <ScopeCountNotes scope={scope} versionCount={versionCount} withScope="count its files" />
      <PathMappingTable rows={rows} scope={scope} search={search} />
    </div>
  );
}

/**
 * Where every kind lives in a repository, with the number of its files in the
 * chosen scope and a link to the page that edits them; with a tool picked,
 * the paths that tool reads instead. The page only reads, so it has no draft
 * bar. Only the view in sight is read: the table by kind, or one answer that
 * holds every tool.
 */
export function PathMappingPage() {
  const scopeState = useScope();
  const [tool, setTool] = useToolView();
  const content = useScopeContent(scopeState.scope, scopeState.owners, scopeState.release);
  const counted = countedVersions(
    scopeState.loading || scopeState.error !== null,
    scopeState.scope !== null,
    content
  );
  const versionIds = counted.status === "ready" ? counted.versionIds : [];
  const requested = requestedVersionIds(versionIds);
  const mappings = usePathMappings(requested, counted.status === "ready" && tool === null);
  const tools = useToolMappings(requested, counted.status === "ready" && tool !== null);
  const scope = scopeState.scope === null ? null : scopeValue(scopeState.scope);

  let body: ReactNode;
  if (scopeState.error !== null) {
    body = (
      <LoadError
        title="Could not load the repositories and packs"
        message={describeError(scopeState.error)}
        onRetry={scopeState.retry}
      />
    );
  } else if (counted.status === "error") {
    body = (
      <LoadError
        title="Could not load the scope's files"
        message={describeError(counted.error)}
        onRetry={counted.retry}
      />
    );
  } else if (tool !== null) {
    body = (
      <ToolViewBody
        tool={tool}
        mappings={tools.data?.data ?? undefined}
        loading={counted.status === "loading" || tools.isPending}
        error={tools.error}
        onRetry={() => void tools.refetch()}
        scope={scope}
        versionCount={versionIds.length}
      />
    );
  } else {
    body = (
      <SearchedPathMappingBody
        rows={mappings.data?.data ?? undefined}
        loading={counted.status === "loading" || mappings.isPending}
        error={mappings.error}
        onRetry={() => void mappings.refetch()}
        scope={scope}
        versionCount={versionIds.length}
      />
    );
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div className="flex flex-col gap-1">
          <h1>Path mapping</h1>
          <p className="text-muted-foreground">
            Where Kaya delivers each kind of file in a repository.
          </p>
        </div>
        <div className="flex flex-wrap items-end gap-3 lg:flex-nowrap">
          <ScopeSelect state={scopeState} />
          <ToolToggle tool={tool} onChange={(next) => void setTool(next)} />
        </div>
      </div>
      {body}
    </div>
  );
}
