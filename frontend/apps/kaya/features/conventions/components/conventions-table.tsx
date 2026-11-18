"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { type MouseEvent } from "react";
import { createParser, useQueryStates } from "nuqs";
import { ChevronDown, ChevronRight, Plus } from "lucide-react";

import { Button } from "@oba/ui/sky/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@oba/ui/sky/table";
import { CONVENTIONS_PREFIX } from "@/features/links/components/links";
import type { ReadyScope } from "@/features/scope/components";
import { effectiveOf } from "@/features/scope/components/effective-items";
import type { ScopeFile } from "@/features/scope/components/scope-files";
import type { Owner } from "@/features/scope/schemas";
import { useConfirmLeave, useGuardedLink } from "@/features/versions/components/draft-context";
import { OriginBadge } from "@/features/versions/components/file-viewer";
import { ItemStatusBadges } from "@/features/versions/components/override-status";

import type { ConventionGroup } from "./convention-files";
import {
  folderRows,
  OPEN_PARAM,
  parseOpenFolders,
  sameOpenFolders,
  serializeOpenFolders,
  toggledFolder,
  visibleRows,
  type VisibleRow,
} from "./convention-folders";
import { conventionDetailHref } from "./convention-links";

const NO_TITLES: ReadonlyMap<string, string> = new Map();

/**
 * The open folders, kept in the URL so a reload, a shared link and the back
 * link of a detail page open the same ones; a toggle replaces the address
 * rather than adding a step to the history.
 */
const openFolders = createParser<string[]>({
  parse: (value: string) => parseOpenFolders(value),
  serialize: (keys: string[]) => serializeOpenFolders(keys),
  eq: sameOpenFolders,
})
  .withDefault([])
  .withOptions({ history: "replace", scroll: false });
const folderParams = { [OPEN_PARAM]: openFolders } as const;

/**
 * The pack a doc comes from, when another pack delivers it: in a pack scope
 * a doc the pack inherits, in a repository scope every doc, since each one
 * comes from one of the repository's packs. Null for the pack's own docs.
 */
function fromText(file: ScopeFile, ready: ReadyScope): string | null {
  if (ready.scope.kind === "PACK" && file.ownerId === ready.scope.packId) return null;
  const owner = ready.owners.find((entry: Owner) => entry.id === file.ownerId);
  // "pack" in lower case, since it follows "from"; it still names the owner a pack.
  return owner ? `from pack ${owner.name}` : null;
}

export type ConventionsTableProps = {
  groups: readonly ConventionGroup[];
  ready: ReadyScope;
  /** Each doc's frontmatter title, by path. */
  titles?: ReadonlyMap<string, string>;
  /** Starts a new doc in a folder; absent where the scope cannot be edited. */
  onNew?: (folder: string) => void;
  /** Whether a new doc is being started, so the folder buttons wait. */
  starting?: boolean;
  /** Opens every folder, as while a search narrows the docs, so each match is in sight. */
  expanded?: boolean;
};

/**
 * The docs as a full-width table, one header row per folder that opens and
 * closes the folder's docs, every folder closed until opened; a doc's row
 * opens its detail page.
 */
export function ConventionsTable({
  groups,
  ready,
  titles = NO_TITLES,
  onNew,
  starting = false,
  expanded = false,
}: ConventionsTableProps) {
  const router = useRouter();
  const searchParams = useSearchParams();
  const guard = useGuardedLink();
  const confirmLeave = useConfirmLeave();
  const current = new URLSearchParams(searchParams.toString());
  const [{ open }, setParams] = useQueryStates(folderParams);
  const rows = folderRows(groups);
  const shownOpen = expanded ? rows.map((row) => row.key) : open;

  return (
    <Table aria-label="Convention docs">
      <TableHeader>
        <TableRow>
          <TableHead>Path</TableHead>
          <TableHead>Title</TableHead>
          <TableHead>From</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {visibleRows(rows, shownOpen).map((entry: VisibleRow) => {
          if (entry.kind === "folder") {
            const { row } = entry;
            const toggle = () => void setParams({ open: toggledFolder(open, row.key) });
            return (
              <TableRow
                key={`folder:${row.key}`}
                className="bg-muted/40 hover:bg-muted/60 cursor-pointer"
                onClick={(event: MouseEvent<HTMLTableRowElement>) => {
                  // The folder's own button toggles by itself, and "+" starts a new doc.
                  if ((event.target as HTMLElement).closest("button")) return;
                  toggle();
                }}
              >
                <TableCell colSpan={3} className="py-1.5">
                  <div
                    className="flex items-center gap-2"
                    style={{ paddingLeft: `${row.depth * 1.25}rem` }}
                  >
                    <button
                      type="button"
                      aria-expanded={entry.open}
                      onClick={toggle}
                      className="focus-visible:ring-ring flex min-w-0 flex-1 items-center gap-2 rounded-sm text-left outline-none focus-visible:ring-2"
                    >
                      {entry.open ? (
                        <ChevronDown aria-hidden className="size-4 shrink-0" />
                      ) : (
                        <ChevronRight aria-hidden className="size-4 shrink-0" />
                      )}
                      <span className="font-mono text-xs font-medium break-all">{row.label}</span>
                      <span className="text-muted-foreground text-xs tabular-nums">
                        {row.count}
                        <span className="sr-only"> {row.count === 1 ? "file" : "files"}</span>
                      </span>
                    </button>
                    {onNew && (
                      <Button
                        size="icon-sm"
                        variant="ghost"
                        aria-label={`New file in ${row.folder.slice(CONVENTIONS_PREFIX.length) || CONVENTIONS_PREFIX}`}
                        disabled={starting}
                        onClick={() => onNew(row.folder)}
                      >
                        <Plus aria-hidden />
                      </Button>
                    )}
                  </div>
                </TableCell>
              </TableRow>
            );
          }
          const { row, file } = entry;
          const href = conventionDetailHref(file.path, current);
          const origin = ready.origins.get(file.path);
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
                <div
                  className="flex flex-col items-start gap-1"
                  style={{ paddingLeft: `${row.depth * 1.25 + 1.5}rem` }}
                >
                  <Link
                    href={href}
                    onClick={guard}
                    className="font-mono text-xs break-all hover:underline"
                  >
                    {file.path.slice(row.folder.length)}
                  </Link>
                  {origin && <OriginBadge origin={origin} />}
                  <ItemStatusBadges
                    item={effectiveOf(ready.effective, file.path)}
                    owners={ready.owners}
                  />
                </div>
              </TableCell>
              <TableCell className="max-w-sm break-words whitespace-normal">
                {titles.get(file.path) ?? null}
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
