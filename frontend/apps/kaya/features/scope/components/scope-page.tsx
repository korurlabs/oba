"use client";

import Link from "next/link";
import { usePathname, useSearchParams } from "next/navigation";
import type { ReactNode } from "react";

import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@oba/ui/sky/tooltip";
import { EmptyState, LoadError, TableLoading } from "@oba/ui/sky/page-states";
import { describeError } from "@/lib/api/error-text";

import { useScopeContent, type ScopeContent } from "../hooks/use-scope-content";
import { useScope } from "../hooks/use-scope";
import { usePackEffective, useShowInherited } from "../hooks/use-effective";
import type { EffectiveItemResponse } from "../schemas";
import { useVersionDetails } from "@/features/versions/hooks/use-versions";
import { effectiveByItem, inheritedFiles, withInherited } from "./effective-items";
import { useDraftSession } from "@/features/versions/hooks/use-draft-session";
import { composeDraftFiles, fileOrigins, type FileOrigin } from "./scope-files";
import {
  LiveEditProvider,
  useGuardedLink,
  type DraftEdit,
} from "@/features/versions/components/draft-context";
import type { Scope, ScopeOptions } from "./scope-data";
import { scopeLabel } from "./scope-data";
import { newerRelease, scopeSourceLabel, scopeSourceText } from "./scope-source";
import { PackTree } from "./pack-tree";
import type { InheritedFilter } from "./list-toolbar";

export type ReadyScope = Extract<ScopeContent, { status: "ready" }> & {
  scope: Scope;
  options: ScopeOptions;
  /**
   * The member's open change of this scope's pack, or null when none is
   * open. While it is, `files` hold the change in place of the pack's files.
   */
  edit: DraftEdit | null;
  /** The override markers of the scope's files, by path; unmarked files are left out. */
  origins: ReadonlyMap<string, FileOrigin>;
  /**
   * A pack's items by item path (a file, or a skill directory ending in `/`),
   * own and inherited, with their override status and lock; empty for a
   * repository and while they load.
   */
  effective: ReadonlyMap<string, EffectiveItemResponse>;
  /** Whether a pack's inherited items are listed beside its own, each with its source pack. */
  inherited: boolean;
  /** "Show inherited" for the list's toolbar; null on a repository, which inherits nothing. */
  inheritedFilter: InheritedFilter | null;
};

const NO_ITEMS: readonly EffectiveItemResponse[] = [];

export type ScopePageProps = {
  title: string;
  description: string;
  /** The page body for a scope whose files have loaded. */
  children: (ready: ReadyScope) => ReactNode;
  /**
   * "list" frames a page of a scope's items with its heading and pack tree;
   * "detail" frames one item's page in a single column,
   * since the item already names its scope and the list chose what is shown.
   */
  variant?: "list" | "detail";
};

const EMPTY_TEXTS: Readonly<
  Record<"no-release" | "unknown-pack", { title: string; description: string }>
> = {
  "no-release": {
    title: "No release yet",
    description:
      "This repository has no config release. One is created when its packs are set or a change to one of its packs is approved.",
  },
  "unknown-pack": {
    title: "Pack not found",
    description: "This pack no longer exists in the organization. Choose another scope.",
  },
};

/**
 * Which release or version the page shows, with a way back to the newest
 * release. A list says only that it shows an older release, since the pack
 * tree already tells each scope's version; an item's page has a short label
 * that holds the whole source in its tooltip.
 */
function ScopeSource({ ready, compact = false }: { ready: ReadyScope; compact?: boolean }) {
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const guard = useGuardedLink();
  const name = scopeLabel(ready.scope, ready.options);
  const text = scopeSourceText(ready, name);
  const newest = newerRelease(ready);
  const newestParams = new URLSearchParams(searchParams.toString());
  newestParams.delete("release");
  const newestLink = (
    <Link
      href={`${pathname}?${newestParams.toString()}`}
      onClick={guard}
      className="text-foreground underline underline-offset-2"
    >
      Show the newest
    </Link>
  );
  if (compact) {
    return (
      <>
        <TooltipProvider delayDuration={0}>
          <Tooltip>
            <TooltipTrigger asChild>
              <button
                type="button"
                className="text-muted-foreground hover:text-foreground focus-visible:ring-ring rounded-sm font-medium outline-none focus-visible:ring-2"
              >
                {scopeSourceLabel(ready, name)}
              </button>
            </TooltipTrigger>
            <TooltipContent className="max-w-xs">
              {text}
              {newest !== null && ` An older release; the newest is ${newest}.`}
            </TooltipContent>
          </Tooltip>
        </TooltipProvider>
        {newest !== null && <span className="text-xs">{newestLink}</span>}
      </>
    );
  }
  if (newest === null || ready.release === null) return null;
  return (
    <p className="text-muted-foreground text-sm">
      {`Release ${ready.release.number}, an older release; the newest is ${newest}. `}
      {newestLink}
    </p>
  );
}

/**
 * The frame of the Rules, Skills, Allowlist and Conventions pages and of a
 * Rules file's page: title, the pack tree as the left column (on a list), and
 * the loading, error and empty states of the scope, so each page only
 * renders a loaded scope.
 */
export function ScopePage({ title, description, children, variant = "list" }: ScopePageProps) {
  const list = variant === "list";
  const scopeState = useScope();
  const owners = scopeState.loading || scopeState.error !== null ? undefined : scopeState.owners;
  const session = useDraftSession(scopeState.scope, owners);
  // A draft is edited against the newest release, which its owner's current version composes.
  const content = useScopeContent(
    scopeState.scope,
    scopeState.owners,
    session.draftId === null ? scopeState.release : null
  );
  const [showInherited, setShowInherited] = useShowInherited();
  const packId = scopeState.scope?.kind === "PACK" ? scopeState.scope.packId : "";
  // The effective view follows the files shown: the open draft, else (null) the current version.
  const effective = usePackEffective(packId, session.draft?.id ?? null);
  const items = effective.data?.data ?? NO_ITEMS;
  const sourceVersionIds = showInherited
    ? [
        ...new Set(
          items.flatMap((item: EffectiveItemResponse) =>
            !item.own && item.sourceVersionId ? [item.sourceVersionId] : []
          )
        ),
      ]
    : [];
  const sources = useVersionDetails(sourceVersionIds);
  const sourceFailure = sources.find((query) => query.error !== null && query.data === undefined);

  let body: ReactNode;
  if (scopeState.error !== null) {
    body = (
      <LoadError
        title="Could not load the repositories and packs"
        message={describeError(scopeState.error)}
        onRetry={scopeState.retry}
      />
    );
  } else if (scopeState.loading) {
    body = <TableLoading label={`Loading ${title.toLowerCase()}`} />;
  } else if (scopeState.scope === null) {
    body = (
      <EmptyState
        title="No repository or pack yet"
        description="Kaya shows agent configuration once a repository records sessions or a pack is created."
      />
    );
  } else if (content.status === "error") {
    body = (
      <LoadError
        title={`Could not load the ${title.toLowerCase()}`}
        message={describeError(content.error)}
        onRetry={content.retry}
      />
    );
  } else if (content.status === "loading") {
    body = <TableLoading label={`Loading ${title.toLowerCase()}`} />;
  } else if (content.status === "empty") {
    body = <EmptyState {...EMPTY_TEXTS[content.reason]} />;
  } else {
    const edit: DraftEdit | null =
      session.draft && session.target
        ? { draft: session.draft, target: session.target, session }
        : null;
    const ownFiles = edit
      ? composeDraftFiles(content.files, edit.target.id, edit.draft)
      : content.files;
    const listInherited = packId !== "" && showInherited;
    const files = listInherited
      ? withInherited(
          ownFiles,
          inheritedFiles(
            items,
            sources.flatMap((query) => (query.data?.data ? [query.data.data] : []))
          )
        )
      : ownFiles;
    const ready: ReadyScope = {
      ...content,
      files,
      scope: scopeState.scope,
      options: scopeState.options,
      edit,
      origins: fileOrigins(files, content.owners),
      effective: effectiveByItem(items),
      inherited: listInherited,
      inheritedFilter:
        packId === ""
          ? null
          : {
              shown: showInherited,
              onChange: (shown: boolean) => void setShowInherited(shown || null),
            },
    };
    const readFailure =
      effective.error !== null && effective.data === undefined ? effective : sourceFailure;
    body = (
      <>
        {list ? (
          <ScopeSource ready={ready} />
        ) : (
          <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm">
            <ScopeSource ready={ready} compact />
          </div>
        )}
        {readFailure && (
          <LoadError
            title="Could not load the inherited items"
            message={describeError(readFailure.error)}
            onRetry={() => void readFailure.refetch()}
          />
        )}
        {children(ready)}
      </>
    );
  }

  if (!list) {
    return (
      <LiveEditProvider>
        <div className="flex min-w-0 flex-col gap-6">{body}</div>
      </LiveEditProvider>
    );
  }

  // The provider holds the pack tree as well, so a change of scope asks before it closes an unsaved edit.
  return (
    <LiveEditProvider>
      <div className="flex flex-col gap-6">
        <div className="flex flex-col gap-1">
          <h1>{title}</h1>
          <p className="text-muted-foreground">{description}</p>
        </div>
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-[16rem_minmax(0,1fr)]">
          <aside className="lg:border-r lg:pr-4">
            {!scopeState.loading && scopeState.error === null && <PackTree state={scopeState} />}
          </aside>
          <div className="flex min-w-0 flex-col gap-6">{body}</div>
        </div>
      </div>
    </LiveEditProvider>
  );
}
