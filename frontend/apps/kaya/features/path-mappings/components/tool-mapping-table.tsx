"use client";

import Link from "next/link";

import { Badge } from "@oba/ui/sky/badge";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@oba/ui/sky/table";

import { parseScope } from "@/features/scope/components/scope-data";

import type { ToolPathResponse } from "../schemas";
import { kindPageHref, kindRowOf } from "./kind-pages";
import { HeadingWithInfo, InfoLines, NO_COUNT, NotDeliveredGroup, PathCell } from "./mapping-cells";
import {
  groupByLoading,
  loadingHelp,
  readInfoLines,
  readNotDelivered,
  readsRegularFile,
  splitDelivered,
  stateHelpLines,
  type LoadingGroup,
} from "./mapping-groups";
import { fileCount, LOADING_LABELS, stateLabelOf } from "./tool-labels";

export type ToolMappingTableProps = {
  reads: readonly ToolPathResponse[];
  /** The `scope` value in view, which every page link carries; null when none is chosen. */
  scope: string | null;
};

/**
 * Whether the scope in view may take its tool links from another pack: a
 * pack scope shows one pack's files, and the repository it is composed into
 * may get its links from another pack at its root. Only a repository scope,
 * which shows the whole release, can really miss one.
 */
export function linkFromRepository(scope: string | null): boolean {
  return scope !== null && parseScope(scope)?.kind !== "REPOSITORY";
}

/** The file the read ends at, linked to the page that edits it, with its kind beneath. */
function Source({ read, scope }: { read: ToolPathResponse; scope: string | null }) {
  const kind = kindRowOf(read);
  const file = readsRegularFile(read) ? read.readPath : read.sourcePattern;
  const path = <code className="font-mono text-xs break-all">{file}</code>;
  return (
    <span className="flex flex-col gap-0.5">
      {kind.page ? (
        <Link
          href={kindPageHref(kind.page, scope)}
          className="underline underline-offset-2"
          aria-label={`${file}, edited on ${kind.page.label}`}
        >
          {path}
        </Link>
      ) : (
        path
      )}
      <span className="text-muted-foreground text-xs">{kind.label}</span>
    </span>
  );
}

/** The files the scope gives the read and how, or a dash when it gives none or is not counted. */
function InScope({ read, fromRepository }: { read: ToolPathResponse; fromRepository: boolean }) {
  if (!read.state || read.state === "NOT_DELIVERED") {
    return <span className="text-muted-foreground">{NO_COUNT}</span>;
  }
  const state = stateLabelOf(read.state, fromRepository);
  return (
    <span className="inline-flex flex-wrap items-center justify-end gap-2">
      <span className="tabular-nums">
        {read.count === null || read.count === undefined ? NO_COUNT : fileCount(read.count)}
      </span>
      <Badge variant={state.variant}>{state.label}</Badge>
    </span>
  );
}

function ReadGroups({
  groups,
  scope,
  fromRepository,
}: {
  groups: readonly LoadingGroup[];
  scope: string | null;
  fromRepository: boolean;
}) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Path</TableHead>
          <TableHead>Delivered from</TableHead>
          <TableHead className="text-right">
            <HeadingWithInfo
              label="What does In scope show?"
              info={<InfoLines lines={stateHelpLines(fromRepository)} />}
            >
              In scope
            </HeadingWithInfo>
          </TableHead>
        </TableRow>
      </TableHeader>
      {groups.map((group: LoadingGroup) => {
        const label = LOADING_LABELS[group.loading].label;
        return (
          <TableBody key={group.loading}>
            <TableRow className="hover:bg-transparent">
              <TableHead
                scope="rowgroup"
                colSpan={3}
                className="text-muted-foreground h-8 pt-4 text-xs font-semibold uppercase"
              >
                <HeadingWithInfo
                  label={`What does ${label} mean?`}
                  info={loadingHelp(group.loading)}
                >
                  {label}
                </HeadingWithInfo>
              </TableHead>
            </TableRow>
            {group.reads.map((read: ToolPathResponse) => (
              <TableRow key={read.readPath} className="align-top">
                <TableCell className="whitespace-normal">
                  <PathCell path={read.readPath} info={readInfoLines(read)} />
                </TableCell>
                <TableCell className="whitespace-normal">
                  <Source read={read} scope={scope} />
                </TableCell>
                <TableCell className="text-right">
                  <InScope read={read} fromRepository={fromRepository} />
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        );
      })}
    </Table>
  );
}

/**
 * Every path one tool reads, under At start, When used and When referenced:
 * the path, the file under `.agents/` it ends at, and what the scope delivers
 * of it. The link the tool passes on the way sits in the path's info tip.
 * Reads the scope delivers nothing for follow in a closed group.
 */
export function ToolMappingTable({ reads, scope }: ToolMappingTableProps) {
  const fromRepository = linkFromRepository(scope);
  const { delivered, notDelivered } = splitDelivered(reads, readNotDelivered);
  return (
    <div className="flex flex-col gap-3">
      {delivered.length > 0 ? (
        <ReadGroups
          groups={groupByLoading(delivered)}
          scope={scope}
          fromRepository={fromRepository}
        />
      ) : (
        <p className="text-muted-foreground text-sm">This scope delivers none of these paths.</p>
      )}
      <NotDeliveredGroup count={notDelivered.length}>
        <ReadGroups
          groups={groupByLoading(notDelivered)}
          scope={scope}
          fromRepository={fromRepository}
        />
      </NotDeliveredGroup>
    </div>
  );
}
