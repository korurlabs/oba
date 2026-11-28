"use client";

import type { ReactNode } from "react";
import { parseAsString, useQueryState } from "nuqs";

import { InfoTip } from "@oba/ui/sky/info-tip";
import { PageHeading } from "@oba/ui/sky/page-heading";
import { EmptyState, LoadError, TableLoading } from "@oba/ui/sky/page-states";
import { ListToolbar, NewButton } from "@/features/scope/components/list-toolbar";
import { NewPackDialog } from "@/features/scope/components/new-pack-dialog";
import { ownerById } from "@/features/scope/components/owners";
import { PackTree } from "@/features/scope/components/pack-tree";
import type { Scope } from "@/features/scope/components/scope-data";
import { useOwners } from "@/features/scope/hooks/use-owners";
import type { ScopeState } from "@/features/scope/hooks/use-scope";
import type { Owner } from "@/features/scope/schemas";
import type { RepositoryResponse } from "@/features/sessions/schemas";
import { describeError } from "@/lib/api/error-text";
import { KayaApiError } from "@/lib/api/errors";
import { useIdentity } from "@/lib/identity/identity-context";

import { usePackDetail } from "../hooks/use-packs";
import { shownPackId } from "./pack-data";
import { PackName } from "./pack-name";
import { PackRepositories } from "./pack-repositories";
import { PackVersions } from "./pack-versions";

const PACKS_HELP =
  "A pack holds agent configuration: rules, skills, conventions and settings. A child pack inherits every item of its parent packs. A repository receives the packs its pack list names, each with its parent packs.";

const PARENT_HELP =
  "A child pack inherits every item of its parent. The parent is fixed at creation.";

function PackDetail({
  packId,
  owners,
  repositories,
  onSelect,
}: {
  packId: string;
  owners: readonly Owner[];
  repositories: readonly RepositoryResponse[];
  onSelect: (packId: string) => void;
}) {
  const detail = usePackDetail(packId);
  const pack = detail.data?.data ?? undefined;

  if (detail.error !== null && detail.data === undefined) {
    if (detail.error instanceof KayaApiError && detail.error.status === 404) {
      return (
        <EmptyState
          title="Pack not found"
          description="This pack no longer exists in the organization. Choose another one."
        />
      );
    }
    return (
      <LoadError
        title="Could not load the pack"
        message={describeError(detail.error)}
        onRetry={() => void detail.refetch()}
      />
    );
  }
  if (pack === undefined) return <TableLoading label="Loading the pack" />;

  const parentId = pack.parentPackId ?? null;
  const parent = parentId ? ownerById(owners, parentId) : undefined;
  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col gap-2">
        <PackName key={`${pack.id}-${pack.name}`} pack={pack} />
        <div className="flex items-center gap-1.5 text-sm">
          <span className="text-muted-foreground">Parent pack</span>
          {parentId === null ? (
            <span>None</span>
          ) : parent ? (
            <button
              type="button"
              className="font-mono underline-offset-2 hover:underline"
              onClick={() => onSelect(parent.id)}
            >
              {parent.name}
            </button>
          ) : (
            <span className="font-mono">{parentId}</span>
          )}
          <InfoTip label="What is a parent pack?">{PARENT_HELP}</InfoTip>
        </div>
      </div>
      <PackRepositories pack={pack} repositories={repositories} />
      <PackVersions key={pack.id} packId={pack.id} />
    </div>
  );
}

/**
 * The organization's packs: the pack tree on the left, and for the pack in
 * the URL (`pack`) its name, parent, the repositories that compose it and
 * its versions. An owner adds a pack from the toolbar, by default as a child
 * of the selected pack.
 */
export function PacksPage() {
  const { can } = useIdentity();
  const [requested, setRequested] = useQueryState("pack", parseAsString);
  const { owners, repositories, error, retry } = useOwners();
  const loading = owners === undefined && error === null;
  const packId = owners ? shownPackId(requested, owners) : null;

  const select = (next: string) => void setRequested(next);
  const treeState: ScopeState = {
    scope: packId === null ? null : { kind: "PACK", packId },
    options: { repositories: [], packs: [] },
    owners: owners ?? [],
    release: null,
    setScope: (next: Scope) => {
      if (next.kind === "PACK") select(next.packId);
    },
    loading,
    error,
    retry,
  };

  const newPack = (trigger: ReactNode) =>
    owners !== undefined && (
      <NewPackDialog
        owners={owners}
        defaultParentId={packId}
        onCreated={(next: Scope) => {
          if (next.kind === "PACK") select(next.packId);
        }}
        trigger={trigger}
      />
    );

  let body;
  if (error !== null) {
    body = (
      <LoadError title="Could not load the packs" message={describeError(error)} onRetry={retry} />
    );
  } else if (owners === undefined) {
    body = <TableLoading label="Loading packs" />;
  } else if (packId === null) {
    body = (
      <EmptyState
        title="No packs yet"
        description={
          can("OWNER")
            ? "A pack holds the rules, skills and conventions your repositories share."
            : "An owner creates packs."
        }
        action={can("OWNER") ? newPack(<NewButton label="New pack" />) : undefined}
      />
    );
  } else {
    body = (
      <PackDetail
        key={packId}
        packId={packId}
        owners={owners}
        repositories={repositories ?? []}
        onSelect={select}
      />
    );
  }

  return (
    <div className="flex flex-col gap-6">
      <PageHeading
        title="Packs"
        description={
          <div className="text-muted-foreground flex items-center gap-2">
            <p>The packs your repositories compose, how they nest, and their versions.</p>
            <InfoTip label="What is a pack?">{PACKS_HELP}</InfoTip>
          </div>
        }
      />
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-[16rem_minmax(0,1fr)]">
        <aside className="lg:border-r lg:pr-4">
          {owners !== undefined && error === null && <PackTree state={treeState} />}
        </aside>
        <div className="flex min-w-0 flex-col gap-6">
          {owners !== undefined && error === null && can("OWNER") && (
            <ListToolbar create={newPack(<NewButton label="New pack" />)} />
          )}
          {body}
        </div>
      </div>
    </div>
  );
}
