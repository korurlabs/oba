"use client";

import { useState, type ReactNode } from "react";
import { ChevronDown, ChevronRight, Folder, FolderGit2 } from "lucide-react";

import { Badge } from "@oba/ui/sky/badge";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@oba/ui/sky/tooltip";
import type { ReleaseResponse } from "@/features/releases/schemas";
import { useConfirmLeave } from "@/features/versions/components/draft-context";
import type { VersionDetailResponse } from "@/features/versions/schemas";
import { cn } from "@/lib/utils";

import type { ScopeState } from "../hooks/use-scope";
import { useNewestReleases, usePackVersions, type TreeMark } from "../hooks/use-tree-versions";
import { packTree, type PackNode } from "./owners";
import { scopeValue, type Scope, type ScopeOption } from "./scope-data";
import { packVersionMark, packVersionTip, releaseMark, releaseTip } from "./scope-source";

/** A row's grey version mark, and its tooltip with the file count and status. */
type RowMark = { mark: string | null; tip: string };

function markOf<T>(
  state: TreeMark<T> | undefined,
  what: string,
  mark: (value: T | null) => string | null,
  tip: (value: T | null) => string
): RowMark {
  if (state === undefined || state.status === "loading") {
    return { mark: null, tip: `Loading its ${what}.` };
  }
  if (state.status === "error") return { mark: null, tip: `Could not load its ${what}.` };
  return { mark: mark(state.value), tip: tip(state.value) };
}

/** A tree row's select button with its version details in a tooltip. */
function RowTip({ tip, children }: { tip: string; children: ReactNode }) {
  return (
    <TooltipProvider delayDuration={300}>
      <Tooltip>
        <TooltipTrigger asChild>{children}</TooltipTrigger>
        <TooltipContent side="right" className="max-w-xs">
          {tip}
        </TooltipContent>
      </Tooltip>
    </TooltipProvider>
  );
}

function Mark({ mark }: { mark: string | null }) {
  if (mark === null) return null;
  return <span className="text-muted-foreground shrink-0 text-xs font-normal">{mark}</span>;
}

type LevelProps = {
  nodes: readonly PackNode[];
  depth: number;
  selectedPackId: string | null;
  collapsed: ReadonlySet<string>;
  onToggle: (packId: string) => void;
  onSelect: (scope: Scope) => void;
  versions: ReadonlyMap<string, TreeMark<VersionDetailResponse>>;
};

function PackLevel({
  nodes,
  depth,
  selectedPackId,
  collapsed,
  onToggle,
  onSelect,
  versions,
}: LevelProps) {
  const indent = { paddingLeft: `${0.5 + depth * 1}rem` };
  return (
    <ul role={depth === 0 ? "tree" : "group"} aria-label={depth === 0 ? "Packs" : undefined}>
      {nodes.map((node: PackNode) => {
        const { owner } = node;
        const current = owner.id === selectedPackId;
        const hasChildren = node.children.length > 0;
        const open = !collapsed.has(owner.id);
        const Chevron = open ? ChevronDown : ChevronRight;
        const row = markOf(versions.get(owner.id), "version", packVersionMark, packVersionTip);
        return (
          <li
            key={owner.id}
            role="treeitem"
            aria-selected={current}
            aria-expanded={hasChildren ? open : undefined}
          >
            <div
              style={indent}
              className={cn(
                "flex w-full items-center gap-1 pr-2",
                current ? "bg-korur-blue-hover/15 text-korur-blue-deep" : "hover:bg-muted/50"
              )}
            >
              {hasChildren ? (
                <button
                  type="button"
                  onClick={() => onToggle(owner.id)}
                  aria-label={`${open ? "Collapse" : "Expand"} ${owner.name}`}
                  className="hover:bg-muted rounded p-1"
                >
                  <Chevron className="text-muted-foreground size-4 shrink-0" aria-hidden />
                </button>
              ) : (
                <span className="w-6 shrink-0" aria-hidden />
              )}
              <RowTip tip={row.tip}>
                <button
                  type="button"
                  onClick={() => onSelect({ kind: "PACK", packId: owner.id })}
                  aria-current={current}
                  className="flex min-w-0 flex-1 items-center gap-2 py-1.5 text-left"
                >
                  <Folder
                    className={cn(
                      "size-4 shrink-0",
                      current ? "text-korur-blue-hover" : "text-muted-foreground"
                    )}
                    aria-hidden
                  />
                  <span className={cn("truncate font-mono text-sm", current && "font-semibold")}>
                    {owner.name}
                  </span>
                  <Mark mark={row.mark} />
                  {owner.required && <Badge variant="secondary">Required</Badge>}
                </button>
              </RowTip>
            </div>
            {hasChildren && open && (
              <PackLevel
                nodes={node.children}
                depth={depth + 1}
                selectedPackId={selectedPackId}
                collapsed={collapsed}
                onToggle={onToggle}
                onSelect={onSelect}
                versions={versions}
              />
            )}
          </li>
        );
      })}
    </ul>
  );
}

/**
 * The left column of a config page: the organization's packs as a folder
 * tree by parent, all levels open at start, and below it the repositories,
 * whose releases are shown read only. Each row names its pack's current
 * version or its repository's newest release in grey, with the file count
 * and status in its tooltip. Another scope closes the open editor, so it
 * asks first while an edit is unsaved.
 */
export function PackTree({ state }: { state: ScopeState }) {
  const { scope, options, owners, setScope } = state;
  const confirmLeave = useConfirmLeave();
  const [collapsed, setCollapsed] = useState<ReadonlySet<string>>(new Set());
  const selectedPackId = scope?.kind === "PACK" ? scope.packId : null;
  const selectedValue = scope === null ? null : scopeValue(scope);
  const tree = packTree(owners);
  const versions = usePackVersions(owners);
  const releases = useNewestReleases(
    options.repositories.flatMap((option: ScopeOption) =>
      option.scope.kind === "REPOSITORY" ? [option.scope.repositoryId] : []
    )
  );

  const select = (next: Scope) => {
    if (scopeValue(next) !== selectedValue && confirmLeave()) setScope(next);
  };
  const toggle = (packId: string) =>
    setCollapsed((previous: ReadonlySet<string>) => {
      const next = new Set(previous);
      if (next.has(packId)) next.delete(packId);
      else next.add(packId);
      return next;
    });

  return (
    <nav aria-label="Scope" className="flex flex-col gap-4">
      <section className="flex flex-col gap-2">
        <h2 className="text-muted-foreground text-xs font-medium uppercase">Packs</h2>
        {tree.length === 0 ? (
          <p className="text-muted-foreground text-sm">No pack yet.</p>
        ) : (
          <PackLevel
            nodes={tree}
            depth={0}
            selectedPackId={selectedPackId}
            collapsed={collapsed}
            onToggle={toggle}
            onSelect={select}
            versions={versions}
          />
        )}
      </section>
      {options.repositories.length > 0 && (
        <section className="flex flex-col gap-2">
          <h2 className="text-muted-foreground text-xs font-medium uppercase">Repositories</h2>
          <ul aria-label="Repositories">
            {options.repositories.map((option: ScopeOption) => {
              const current = option.value === selectedValue;
              const row = markOf<ReleaseResponse>(
                option.scope.kind === "REPOSITORY"
                  ? releases.get(option.scope.repositoryId)
                  : undefined,
                "release",
                releaseMark,
                releaseTip
              );
              return (
                <li key={option.value}>
                  <RowTip tip={row.tip}>
                    <button
                      type="button"
                      onClick={() => select(option.scope)}
                      aria-current={current}
                      className={cn(
                        "flex w-full items-center gap-2 py-1.5 pr-2 pl-2 text-left",
                        current
                          ? "bg-korur-blue-hover/15 text-korur-blue-deep font-semibold"
                          : "hover:bg-muted/50"
                      )}
                    >
                      <FolderGit2
                        className={cn(
                          "size-4 shrink-0",
                          current ? "text-korur-blue-hover" : "text-muted-foreground"
                        )}
                        aria-hidden
                      />
                      <span className="truncate text-sm">{option.label}</span>
                      <Mark mark={row.mark} />
                    </button>
                  </RowTip>
                </li>
              );
            })}
          </ul>
        </section>
      )}
    </nav>
  );
}
