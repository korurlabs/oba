"use client";

import { useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";

import { ListEmptyState, LoadError } from "@oba/ui/sky/page-states";
import { CLEAR_FILTERS_LABEL } from "@/components/common/list-parts";
import { ScopePage, type ReadyScope } from "@/features/scope/components";
import { ListToolbar, NewButton } from "@/features/scope/components/list-toolbar";
import type { ScopeFile } from "@/features/scope/components/scope-files";
import { editTarget } from "@/features/scope/components/scope-data";
import { useEditSource } from "@/features/versions/hooks/use-change-write";
import { describeError } from "@/lib/api/error-text";

import { CONVENTION_VERSIONS_MAX, useConventions } from "../hooks/use-conventions";
import {
  conventionFiles,
  conventionGroups,
  conventionTitles,
  conventionVersionIds,
} from "./convention-files";
import { conventionNewHref } from "./convention-links";
import { ConventionsTable } from "./conventions-table";

export const CONVENTIONS_DESCRIPTION =
  "The convention docs under docs/private/conventions/ that a repository's release or a pack delivers, with the links between them and the rules.";

/**
 * Opens the new-doc page, in a folder when one is named. Nothing is written
 * until the member submits the doc, so no change is opened first.
 */
function useNewConvention() {
  const router = useRouter();
  const searchParams = useSearchParams();
  return (folder: string | null) =>
    router.push(conventionNewHref(new URLSearchParams(searchParams.toString()), folder));
}

/**
 * The docs a search keeps: every term is found in the doc's path or title,
 * ignoring case. An empty search keeps every doc.
 */
function filterDocs(
  docs: readonly ScopeFile[],
  titles: ReadonlyMap<string, string> | undefined,
  search: string
): ScopeFile[] {
  const terms = search.toLowerCase().split(/\s+/).filter(Boolean);
  return docs.filter((doc: ScopeFile) => {
    const haystack = `${doc.path} ${titles?.get(doc.path) ?? ""}`.toLowerCase();
    return terms.every((term: string) => haystack.includes(term));
  });
}

function ConventionsView({ ready }: { ready: ReadyScope }) {
  const docs = conventionFiles(ready.files);
  const versionIds = conventionVersionIds(docs);
  const conventions = useConventions(versionIds);
  const rows = conventions.data?.data;
  const titles = rows ? conventionTitles(docs, rows) : undefined;
  const [search, setSearch] = useState("");
  const searching = search.trim() !== "";
  const visible = filterDocs(docs, titles, search);
  // The pack an edit changes; null in a repository scope, which is read only.
  const target = ready.edit?.target ?? editTarget(ready.scope, ready.owners);
  const source = useEditSource(ready);
  const onNew = useNewConvention();
  // A new doc can start only where an edit can go: not while loading, nor while a change of another pack is open.
  const mayCreate = source !== null && source.kind !== "locked";
  const create =
    target === null ? null : (
      <NewButton
        label="New convention"
        lockedBy={source?.kind === "locked" ? source.openPackName : null}
        disabled={!mayCreate}
        onClick={() => onNew(null)}
      />
    );

  return (
    <div className="flex flex-col gap-4">
      <ListToolbar
        filter={ready.inheritedFilter}
        search={{
          value: search,
          onChange: setSearch,
          label: "Search convention docs",
          placeholder: "Search by path or title",
        }}
        create={create}
      />
      {conventions.error !== null && !rows && (
        <LoadError
          title="Could not load the convention docs"
          message={describeError(conventions.error)}
          onRetry={() => void conventions.refetch()}
        />
      )}
      {versionIds.length > CONVENTION_VERSIONS_MAX && (
        <p className="text-muted-foreground text-sm">
          These docs come from more than {CONVENTION_VERSIONS_MAX} versions, more than Kaya
          describes at once, so the list shows their paths only.
        </p>
      )}
      {visible.length === 0 ? (
        <ListEmptyState
          filtered={docs.length > 0}
          onClearFilters={() => setSearch("")}
          create={create}
          labels={{
            emptyTitle: "No convention docs yet",
            emptyDescription: "This scope delivers no convention docs.",
            filteredTitle: "No convention doc matches",
            filteredDescription: "No convention docs match the search.",
            clearFilters: CLEAR_FILTERS_LABEL,
          }}
        />
      ) : (
        <ConventionsTable
          groups={conventionGroups(visible)}
          ready={ready}
          titles={titles}
          expanded={searching}
          onNew={target === null || !mayCreate ? undefined : (folder: string) => onNew(folder)}
        />
      )}
    </div>
  );
}

/** The Markdown convention docs a repository's release or a pack delivers, grouped by folder. */
export function ConventionsPage() {
  return (
    <ScopePage title="Conventions" description={CONVENTIONS_DESCRIPTION}>
      {(ready: ReadyScope) => <ConventionsView ready={ready} />}
    </ScopePage>
  );
}
