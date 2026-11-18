"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import type { MouseEvent } from "react";

import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@oba/ui/sky/table";
import type { ReadyScope } from "@/features/scope/components";
import { effectiveOf } from "@/features/scope/components/effective-items";
import type { RuleTab, ScopeFile } from "@/features/scope/components/scope-files";
import type { Owner } from "@/features/scope/schemas";
import { useConfirmLeave, useGuardedLink } from "@/features/versions/components/draft-context";
import { OriginBadge } from "@/features/versions/components/file-viewer";
import { ItemStatusBadges } from "@/features/versions/components/override-status";

import { ruleDetailHref } from "./rule-links";
import { ROW_FIELD_LABELS, rowField, type RuleTabRow } from "./rule-rows";

const NO_EVENTS: readonly string[] = [];

/**
 * The pack a row comes from, when another pack delivers it: in a pack scope
 * a file the pack inherits, in a repository scope every file, since each one
 * comes from one of the repository's packs. Null for the pack's own files.
 */
function fromText(file: ScopeFile, ready: ReadyScope): string | null {
  if (ready.scope.kind === "PACK" && file.ownerId === ready.scope.packId) return null;
  const owner = ready.owners.find((entry: Owner) => entry.id === file.ownerId);
  // "pack" in lower case, since it follows "from"; it still names the owner a pack.
  return owner ? `from pack ${owner.name}` : null;
}

export type RulesTableProps = {
  tab: RuleTab;
  /** The tab's files in list order, each with what its kind list says about it. */
  rows: readonly RuleTabRow[];
  ready: ReadyScope;
  /** The hook events that run each hook script, by path; read on the Hooks tab only. */
  hookEvents: ReadonlyMap<string, readonly string[]>;
  /** A row's detail page; the tab's Rules detail page when absent. */
  detailHref?: (path: string, current: URLSearchParams) => string;
};

/** A tab's files as a full-width table; a row opens the file's detail page. */
export function RulesTable({ tab, rows, ready, hookEvents, detailHref }: RulesTableProps) {
  const router = useRouter();
  const searchParams = useSearchParams();
  const guard = useGuardedLink();
  const confirmLeave = useConfirmLeave();
  const current = new URLSearchParams(searchParams.toString());

  return (
    <Table aria-label="Files">
      <TableHeader>
        <TableRow>
          <TableHead>Path</TableHead>
          <TableHead>Title</TableHead>
          <TableHead>{ROW_FIELD_LABELS[tab]}</TableHead>
          <TableHead>From</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {rows.map((row: RuleTabRow) => {
          const { file, summary } = row;
          const href = detailHref
            ? detailHref(file.path, current)
            : ruleDetailHref(tab, file.path, current);
          const origin = ready.origins.get(file.path);
          const field = rowField(tab, row, hookEvents.get(file.path) ?? NO_EVENTS);
          return (
            <TableRow
              key={file.path}
              className="cursor-pointer align-top"
              onClick={(event: MouseEvent<HTMLTableRowElement>) => {
                // The path link navigates by itself; the rest of the row follows it.
                if ((event.target as HTMLElement).closest("a")) return;
                if (confirmLeave()) router.push(href);
              }}
            >
              <TableCell className="max-w-md whitespace-normal">
                <div className="flex flex-col items-start gap-1">
                  <Link
                    href={href}
                    onClick={guard}
                    className="font-mono text-xs break-all hover:underline"
                  >
                    {file.path}
                  </Link>
                  {origin && <OriginBadge origin={origin} />}
                  <ItemStatusBadges
                    item={effectiveOf(ready.effective, file.path)}
                    owners={ready.owners}
                  />
                </div>
              </TableCell>
              <TableCell className="max-w-sm break-words whitespace-normal">
                {summary?.title ?? null}
              </TableCell>
              <TableCell className="text-muted-foreground max-w-sm break-words whitespace-normal">
                {field}
              </TableCell>
              <TableCell className="text-muted-foreground whitespace-nowrap">
                {fromText(file, ready)}
              </TableCell>
            </TableRow>
          );
        })}
      </TableBody>
    </Table>
  );
}
