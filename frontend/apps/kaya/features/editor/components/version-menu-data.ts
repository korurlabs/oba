import type { FileHistoryResponse } from "@/features/changes/schemas";
import type { ApiResponse } from "@/types/api";

const CHANGE_WORDS: Readonly<Record<FileHistoryResponse["change"], string>> = {
  ADDED: "added",
  MODIFIED: "changed",
  DELETED: "deleted",
};

/** "Version 7, approved, changed": one menu entry's title. */
export function historyEntryTitle(entry: FileHistoryResponse): string {
  const status = entry.status === "IN_REVIEW" ? "in review" : entry.status.toLowerCase();
  return `Version ${entry.number}, ${status}, ${CHANGE_WORDS[entry.change]}`;
}

/** The entries of every loaded history page, newest first. */
export function historyEntriesOf(
  pages: readonly ApiResponse<FileHistoryResponse[]>[]
): FileHistoryResponse[] {
  return pages.flatMap((page: ApiResponse<FileHistoryResponse[]>) => page.data ?? []);
}

/**
 * The menu's note when it has nothing to choose: no version known yet, or
 * one version only, which holds the file as it is now. Older pages can still
 * hold versions of the file, so the note waits until the last page is loaded.
 */
export function historyEmptyText(
  entries: readonly FileHistoryResponse[],
  complete: boolean
): string | null {
  if (!complete) return null;
  if (entries.length === 0) return "No version of this file is known yet.";
  if (entries.length === 1) return `Only version ${entries[0]?.number ?? ""} holds this file.`;
  return null;
}

/**
 * What choosing an entry opens: the current version or the viewer's own
 * draft show as the live document, any other version read-only at `at`.
 */
export function historyTarget(entry: FileHistoryResponse): "current" | "draft" | "older" {
  if (entry.current) return "current";
  if (entry.status === "DRAFT") return "draft";
  return "older";
}

/**
 * What the decision time of an entry means: an approval for a version that
 * was approved, and a rejection for a draft a reviewer sent back; null when
 * no one decided yet.
 */
export function decisionText(entry: FileHistoryResponse): string | null {
  if (entry.decidedAt == null) return null;
  if (entry.status === "APPROVED" || entry.status === "SUPERSEDED") return "Approved";
  if (entry.status === "DRAFT" || entry.status === "REJECTED") return "Sent back";
  return null;
}
