/**
 * How the path mapping tables order their rows and what a row's info tip
 * says. The tables show only the path, its source and what the scope holds,
 * so every other fact about a row lives in these lines.
 */

import type { FileFormat, PathMappingResponse, ToolLoading, ToolPathResponse } from "../schemas";
import { ToolLoadingSchema } from "../schemas";
import { DELIVERED_STATES, type DeliveredState, LOADING_LABELS, stateLabelOf } from "./tool-labels";

/** The rows a scope delivers, in the server's order, and the rows it delivers nothing for. */
export type DeliveredSplit<T> = {
  delivered: T[];
  notDelivered: T[];
};

export function splitDelivered<T>(
  rows: readonly T[],
  isNotDelivered: (row: T) => boolean
): DeliveredSplit<T> {
  const delivered: T[] = [];
  const notDelivered: T[] = [];
  for (const row of rows) {
    (isNotDelivered(row) ? notDelivered : delivered).push(row);
  }
  return { delivered, notDelivered };
}

/** The reads of one loading kind, in the server's order. */
export type LoadingGroup = {
  loading: ToolLoading;
  reads: ToolPathResponse[];
};

/** The reads under At start, When used and When referenced, in that order; an empty group is left out. */
export function groupByLoading(reads: readonly ToolPathResponse[]): LoadingGroup[] {
  return ToolLoadingSchema.options
    .map((loading: ToolLoading) => ({
      loading,
      reads: reads.filter((read: ToolPathResponse) => read.loading === loading),
    }))
    .filter((group: LoadingGroup) => group.reads.length > 0);
}

/** A read the scope gives the tool nothing for; without a scope no read has a state. */
export function readNotDelivered(read: ToolPathResponse): boolean {
  return read.state === "NOT_DELIVERED";
}

/**
 * Whether the scope holds a regular file where the tool would pass a link,
 * such as a root AGENTS.md that is not the link to .agents/AGENTS.md. The
 * tool then reads that file and never reaches the link's target, so the row
 * must not show the chain.
 */
export function readsRegularFile(read: ToolPathResponse): boolean {
  return read.state === "DIRECT" && !!read.linkPath;
}

/** What a read's info tip says: the server's note and the link the tool passes on the way. */
export function readInfoLines(read: ToolPathResponse): string[] {
  const lines: string[] = [];
  if (read.note) lines.push(sentence(read.note));
  if (read.linkPath && read.linkTarget && !readsRegularFile(read)) {
    lines.push(`Read through the link ${read.linkPath}, which points to ${read.linkTarget}.`);
  }
  return lines;
}

/** What the root AGENTS.md row says when the scope delivers that path as the tool link instead. */
export const ROOT_AS_LINK_NOTE =
  "Delivered as the tool link AGENTS.md; this row counts a regular file only.";

/** What a row says for a kind a pack mounted in a folder may not deliver. */
export const NOT_IN_MOUNT = "A pack mounted in a folder may not deliver this kind.";

const FORMAT_LABELS: Readonly<Record<FileFormat, string>> = {
  MARKDOWN: "Markdown",
  JSON: "JSON",
  YAML: "YAML",
  ANY: "Any",
  SYMLINK: "Symlink",
};

/** Whether the scope delivers the root AGENTS.md as the tool link, which shares the regular file's path. */
export function rootDeliveredAsLink(rows: readonly PathMappingResponse[]): boolean {
  const link = rows.find(
    (row: PathMappingResponse) => row.kind === "TOOL_LINK" && row.link === "ROOT_AGENTS_FILE"
  );
  return (link?.count ?? 0) > 0;
}

export function isRootAgentsFile(row: PathMappingResponse): boolean {
  return row.kind === "AGENTS_FILE" && row.placement === "ROOT";
}

/**
 * A row the scope holds no file for. A row without a count is never one: no
 * scope is chosen, or the scope is too large to count. The root AGENTS.md
 * counts regular files only, so it stays delivered when the link stands there.
 */
export function rowNotDelivered(row: PathMappingResponse, rootAsLink: boolean): boolean {
  return row.count === 0 && !(rootAsLink && isRootAgentsFile(row));
}

/** What a row's info tip says: the note, the link case, where a mounted pack delivers it, the formats and the review. */
export function rowInfoLines(row: PathMappingResponse, rootAsLink: boolean): string[] {
  const lines: string[] = [];
  if (row.note) lines.push(sentence(row.note));
  if (rootAsLink && isRootAgentsFile(row)) lines.push(ROOT_AS_LINK_NOTE);
  lines.push(row.mountedPattern ? `In a mounted pack: ${row.mountedPattern}` : NOT_IN_MOUNT);
  const formats = row.formats.map((format: FileFormat) => FORMAT_LABELS[format] ?? format);
  if (formats.length > 0) lines.push(`Format: ${formats.join(", ")}`);
  if (row.executable) lines.push("A change here is an executable change, which an owner approves.");
  return lines;
}

/** What a loading kind's heading explains in its info tip. */
export function loadingHelp(loading: ToolLoading): string {
  return sentence(LOADING_LABELS[loading].description);
}

/** What the In scope column of a tool's table explains: each badge, then the dash. */
export function stateHelpLines(fromRepository: boolean): string[] {
  return [
    ...DELIVERED_STATES.map((state: DeliveredState) => {
      const label = stateLabelOf(state, fromRepository);
      return `${label.label}: ${sentence(label.description)}`;
    }),
    "A dash: the scope delivers nothing at this path, or no scope is chosen.",
  ];
}

/** The label of the collapsed group of rows the scope delivers nothing for. */
export function notDeliveredLabel(count: number): string {
  return `${count} not delivered by this scope`;
}

/** A server note or label description as a sentence: a capital first letter and a full stop. */
export function sentence(text: string): string {
  const trimmed = text.trim();
  if (trimmed === "") return trimmed;
  const capital = trimmed.charAt(0).toUpperCase() + trimmed.slice(1);
  return /[.!?]$/.test(capital) ? capital : `${capital}.`;
}
