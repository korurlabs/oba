"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import type { MouseEvent } from "react";
import { AlertTriangle } from "lucide-react";

import { Badge } from "@oba/ui/sky/badge";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@oba/ui/sky/table";

import type { EffectiveItemResponse, Owner } from "@/features/scope/schemas";
import { useConfirmLeave, useGuardedLink } from "@/features/versions/components/draft-context";
import { ItemStatusBadges } from "@/features/versions/components/override-status";

import { firstSentence, skillOrigin, type SkillRow } from "./skill-rows";

export type SkillsTableProps = {
  rows: readonly SkillRow[];
  /** A skill's own page, by its folder. */
  hrefOf: (directory: string) => string;
  /** A pack's items by item path, for the override and lock badges; absent for a repository. */
  effective?: ReadonlyMap<string, EffectiveItemResponse>;
  owners?: readonly Owner[];
};

function DescriptionCell({ row }: { row: SkillRow }) {
  if (row.error !== null) {
    return (
      <span className="text-error flex items-start gap-1.5">
        <AlertTriangle className="mt-0.5 size-3.5 shrink-0" aria-hidden />
        <span>{row.error}</span>
      </span>
    );
  }
  if (row.description === undefined) {
    return <span className="bg-muted block h-4 w-48 animate-pulse rounded" aria-busy="true" />;
  }
  return (
    <span className={row.description ? "line-clamp-2" : "text-muted-foreground"}>
      {row.description ? firstSentence(row.description) : "No description."}
    </span>
  );
}

/**
 * One row per skill: name, description, the pack that delivers it, and its
 * file count; a row opens the skill's own page.
 */
export function SkillsTable({ rows, hrefOf, effective, owners = [] }: SkillsTableProps) {
  const router = useRouter();
  const guard = useGuardedLink();
  const confirmLeave = useConfirmLeave();
  return (
    <Table aria-label="Skills">
      <TableHeader>
        <TableRow>
          <TableHead>Name</TableHead>
          <TableHead>Description</TableHead>
          <TableHead>Origin</TableHead>
          <TableHead className="text-right">Files</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {rows.map((row: SkillRow) => {
          const href = hrefOf(row.directory);
          return (
            <TableRow
              key={row.directory}
              className="cursor-pointer align-top"
              onClick={(event: MouseEvent<HTMLTableRowElement>) => {
                // The name link navigates by itself; the rest of the row follows it.
                if ((event.target as HTMLElement).closest("a")) return;
                if (confirmLeave()) router.push(href);
              }}
            >
              <TableCell className="font-mono font-medium">
                <Link href={href} onClick={guard} className="hover:underline">
                  {row.name}
                </Link>
              </TableCell>
              <TableCell className="max-w-xl whitespace-normal">
                <DescriptionCell row={row} />
              </TableCell>
              <TableCell>
                <span className="flex flex-wrap items-center gap-1.5">
                  <Badge variant={skillOrigin(row).variant}>{skillOrigin(row).text}</Badge>
                  <ItemStatusBadges item={effective?.get(`${row.directory}/`)} owners={owners} />
                </span>
              </TableCell>
              <TableCell className="text-right tabular-nums">{row.fileCount}</TableCell>
            </TableRow>
          );
        })}
      </TableBody>
    </Table>
  );
}
