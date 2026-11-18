"use client";

import Link from "next/link";

import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@oba/ui/sky/table";

import type { PathMappingResponse } from "../schemas";
import { filterMappingRows, kindPageHref, kindRowOf } from "./kind-pages";
import { HeadingWithInfo, NO_COUNT, NotDeliveredGroup, PathCell } from "./mapping-cells";
import {
  rootDeliveredAsLink,
  rowInfoLines,
  rowNotDelivered,
  splitDelivered,
} from "./mapping-groups";
import { fileCount } from "./tool-labels";

export type PathMappingTableProps = {
  rows: readonly PathMappingResponse[];
  /** The `scope` value in view, which every page link carries; null when none is chosen. */
  scope: string | null;
  /** Narrows the rows to the ones that match; empty keeps every row. */
  search?: string;
};

export const IN_SCOPE_HELP =
  "The number of files the scope holds at this path, linked to the page that edits them. A dash means it holds none, or no scope is chosen.";

function rowKey(row: PathMappingResponse): string {
  return `${row.kind}:${row.part ?? ""}:${row.placement ?? ""}:${row.link ?? ""}`;
}

function KindRows({
  rows,
  scope,
  rootAsLink,
}: {
  rows: readonly PathMappingResponse[];
  scope: string | null;
  rootAsLink: boolean;
}) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Path</TableHead>
          <TableHead>Delivered from</TableHead>
          <TableHead className="text-right">
            <HeadingWithInfo label="What does In scope show?" info={IN_SCOPE_HELP}>
              In scope
            </HeadingWithInfo>
          </TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {rows.map((row: PathMappingResponse) => {
          const kind = kindRowOf(row);
          const href = kind.page ? kindPageHref(kind.page, scope) : null;
          const count = row.count ?? null;
          return (
            <TableRow key={rowKey(row)} className="align-top">
              <TableCell className="whitespace-normal">
                <PathCell path={row.pathPattern} info={rowInfoLines(row, rootAsLink)} />
              </TableCell>
              <TableCell className="whitespace-normal">
                <span className="flex flex-col gap-0.5">
                  {kind.page && href ? (
                    <Link href={href} className="underline underline-offset-2">
                      {kind.page.label}
                    </Link>
                  ) : null}
                  <span className="text-muted-foreground text-xs">{kind.label}</span>
                </span>
              </TableCell>
              <TableCell className="text-right tabular-nums">
                {count !== null && count > 0 && kind.page && href ? (
                  <Link
                    href={href}
                    className="underline underline-offset-2"
                    aria-label={`${count} ${kind.label} files on ${kind.page.label}`}
                  >
                    {fileCount(count)}
                  </Link>
                ) : count !== null && count > 0 ? (
                  <span>{fileCount(count)}</span>
                ) : (
                  <span className="text-muted-foreground">{NO_COUNT}</span>
                )}
              </TableCell>
            </TableRow>
          );
        })}
      </TableBody>
    </Table>
  );
}

/**
 * One row per place a kind lives, in the server's order: its path pattern,
 * the page that edits it with the kind beneath, and the files the scope holds
 * there. The rest of a row (its note, where a mounted pack delivers it, its
 * formats, an owner review) sits in the path's info tip. Rows the scope holds
 * nothing for follow in a closed group.
 */
export function PathMappingTable({ rows, scope, search = "" }: PathMappingTableProps) {
  // Read from every row, so a search does not change how the root AGENTS.md is described.
  const rootAsLink = rootDeliveredAsLink(rows);
  const { delivered, notDelivered } = splitDelivered(
    filterMappingRows(rows, search),
    (row: PathMappingResponse) => rowNotDelivered(row, rootAsLink)
  );
  const searching = search.trim() !== "";
  return (
    <div className="flex flex-col gap-3">
      {delivered.length > 0 ? (
        <KindRows rows={delivered} scope={scope} rootAsLink={rootAsLink} />
      ) : (
        <p className="text-muted-foreground text-sm">
          {searching
            ? "No delivered path matches the search."
            : "This scope delivers none of these paths."}
        </p>
      )}
      <NotDeliveredGroup count={notDelivered.length}>
        <KindRows rows={notDelivered} scope={scope} rootAsLink={rootAsLink} />
      </NotDeliveredGroup>
    </div>
  );
}
