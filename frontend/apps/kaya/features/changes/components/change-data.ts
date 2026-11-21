import type { VersionSource, VersionStatus } from "@/features/versions/schemas";
import { KayaApiError } from "@/lib/api/errors";
import type { Identity } from "@/lib/identity/identity-context";

import type { ChangeFilterParams } from "../hooks/use-change-filter-params";
import { CHANGES_PAGE_SIZE } from "../hooks/use-changes";
import { ownerLabel } from "@/features/scope/components/owner-labels";
import type { Owner } from "@/features/scope/schemas";
import {
  rebaseConflictSchema,
  type FileDiffResponse,
  type RebaseConflict,
  type VersionResponse,
} from "@/features/versions/schemas";
import {
  type ChangeDetailResponse,
  type ChangeQueryRequest,
  type ReviewDecision,
} from "../schemas";

type BadgeVariant = "muted" | "warning" | "success" | "error" | "info" | "secondary";

export const STATUS_BADGES: Readonly<
  Record<VersionStatus, { label: string; variant: BadgeVariant }>
> = {
  DRAFT: { label: "Open", variant: "muted" },
  IN_REVIEW: { label: "In review", variant: "warning" },
  APPROVED: { label: "Approved", variant: "success" },
  REJECTED: { label: "Rejected", variant: "error" },
  SUPERSEDED: { label: "Superseded", variant: "secondary" },
};

/** The statuses offered as a filter; REJECTED is never set, since a rejection reopens the change. */
export const STATUS_FILTERS: readonly VersionStatus[] = [
  "IN_REVIEW",
  "DRAFT",
  "APPROVED",
  "SUPERSEDED",
];

export const SOURCE_LABELS: Readonly<Record<VersionSource, string>> = {
  MANUAL: "Kaya web",
  CLI: "oba kaya propose",
  SEED: "Seed",
  ROLLBACK: "Restore",
};

export const DECISION_LABELS: Readonly<Record<ReviewDecision, string>> = {
  APPROVE: "approved",
  REJECT: "rejected",
  COMMENT: "commented",
};

/** The URL filters as the list request; an unknown page falls back to the first. */
export function toChangeQuery(filters: ChangeFilterParams): ChangeQueryRequest {
  const query: ChangeQueryRequest = {
    page: Math.max(1, filters.page),
    amount: CHANGES_PAGE_SIZE,
  };
  if (filters.status !== null) query.status = filters.status;
  if (filters.packId !== null && filters.packId !== "") query.packId = filters.packId;
  return query;
}

/** The label of the pack a version belongs to; packs load separately, so it may be unknown. */
export function versionOwnerLabel(
  version: Pick<VersionResponse, "packId">,
  owners: readonly Owner[] | undefined
): string {
  const owner = owners?.find((entry: Owner) => entry.id === version.packId);
  if (owner) return ownerLabel(owner);
  return owners === undefined ? "a pack" : "a pack that no longer exists";
}

/** "Version 7 of Pack village" as a change's title. */
export function changeTitle(
  version: Pick<VersionResponse, "number" | "packId">,
  owners: readonly Owner[] | undefined
): string {
  return `Version ${version.number} of ${versionOwnerLabel(version, owners)}`;
}

/** Who approves it: an owner for executable code, hooks or permissions; else an admin. */
export function approverText(requiredRole: string): string {
  return requiredRole === "OWNER" ? "An owner" : "An admin or owner";
}

/** A review control: not shown, usable, or shown greyed out with the reason. */
export type ActionState =
  { kind: "hidden" } | { kind: "enabled" } | { kind: "disabled"; reason: string };

export type ReviewActions = {
  approve: ActionState;
  reject: ActionState;
  comment: ActionState;
};

const HIDDEN: ActionState = { kind: "hidden" };
const ENABLED: ActionState = { kind: "enabled" };

/**
 * What the viewer may do on a change. The server's `canApprove` and
 * `canReview` decide; this only explains a refusal. A member below ADMIN
 * sees no review control, and nothing is reviewed outside IN_REVIEW.
 */
export function reviewActions(
  detail: Pick<
    ChangeDetailResponse,
    "version" | "requiredRole" | "violations" | "canApprove" | "canReview"
  >,
  identity: Pick<Identity, "can"> & { me: { id: string } }
): ReviewActions {
  if (detail.version.status !== "IN_REVIEW" || !identity.can("ADMIN")) {
    return { approve: HIDDEN, reject: HIDDEN, comment: HIDDEN };
  }
  const proposer = detail.version.proposedBy === identity.me.id;
  const ownProposal = "You proposed this change, so another admin or owner reviews it.";
  const review: ActionState = detail.canReview
    ? ENABLED
    : { kind: "disabled", reason: proposer ? ownProposal : "You cannot review this change now." };

  let approve: ActionState = ENABLED;
  if (!detail.canApprove) {
    const violations = detail.violations ?? [];
    let reason = "You cannot approve this change now.";
    if (proposer) reason = ownProposal;
    else if (detail.requiredRole === "OWNER" && !identity.can("OWNER")) {
      reason =
        "This change touches executable code, hooks or permissions; only an owner approves it.";
    } else if (violations.length > 0) {
      reason = "The change breaks the rules listed below, so it cannot be approved now.";
    }
    approve = { kind: "disabled", reason };
  }
  return { approve, reject: review, comment: review };
}

/**
 * The heading over the rules the viewer's own open change breaks: the server
 * checks an open change only for its proposer, as a submit would, so the heading
 * says the submit is refused until they are fixed.
 */
export function draftViolationsTitle(count: number): string {
  return count === 1
    ? "Your change breaks a rule; a submit refuses it until it is fixed."
    : `Your change breaks ${count} rules; a submit refuses it until they are fixed.`;
}

/**
 * Whether a version can be restored: it was released (approved or
 * superseded) and is not its owner's current version, which would change
 * nothing. While the current version is unknown, only a superseded one is
 * offered, since an approved one is usually current.
 */
export function mayRestore(
  version: Pick<VersionResponse, "id" | "status">,
  currentVersionId: string | null | undefined
): boolean {
  if (version.status === "SUPERSEDED") return version.id !== currentVersionId;
  if (version.status === "APPROVED") {
    return currentVersionId !== undefined && version.id !== currentVersionId;
  }
  return false;
}

/** The conflicts of a refused rebase or submit (409 KAY_CFG_002); null for any other error. */
export function rebaseConflictsOf(error: unknown): RebaseConflict[] | null {
  if (!(error instanceof KayaApiError) || error.code !== "KAY_CFG_002") return null;
  const raw = error.errorValues.conflicts;
  if (!Array.isArray(raw)) return [];
  return raw.flatMap((entry: unknown) => {
    const parsed = rebaseConflictSchema.safeParse(entry);
    return parsed.success ? [parsed.data] : [];
  });
}

// ── Diff ────────────────────────────────────────────────────────────

export type DiffLineKind = "header" | "hunk" | "added" | "removed" | "context" | "note";

export type DiffLine = { kind: DiffLineKind; text: string };

/** The lines of a git-format unified diff, each with what it is for colouring. */
export function diffLines(unifiedDiff: string): DiffLine[] {
  const lines = unifiedDiff.split("\n");
  if (lines.at(-1) === "") lines.pop();
  return lines.map((text: string): DiffLine => {
    if (text.startsWith("--- ") || text.startsWith("+++ ")) return { kind: "header", text };
    if (text.startsWith("@@")) return { kind: "hunk", text };
    if (text.startsWith("+")) return { kind: "added", text };
    if (text.startsWith("-")) return { kind: "removed", text };
    if (text.startsWith("\\")) return { kind: "note", text };
    return { kind: "context", text };
  });
}

/** Counts of added and removed lines, for a file's heading. */
export function diffStats(unifiedDiff: string | null | undefined): {
  added: number;
  removed: number;
} {
  if (!unifiedDiff) return { added: 0, removed: 0 };
  return diffLines(unifiedDiff).reduce(
    (stats, line: DiffLine) => ({
      added: stats.added + (line.kind === "added" ? 1 : 0),
      removed: stats.removed + (line.kind === "removed" ? 1 : 0),
    }),
    { added: 0, removed: 0 }
  );
}

const MODE_WORDS: Readonly<Record<string, string>> = {
  FILE: "a file",
  EXECUTABLE: "an executable",
  SYMLINK: "a symlink",
};

/**
 * What a diff entry shows besides its lines: a mode change, a symlink and its
 * target, or binary content. Empty when the line diff says it all.
 */
export function diffNotes(file: FileDiffResponse): string[] {
  const notes: string[] = [];
  if (file.change === "MODIFIED" && file.oldMode && file.newMode && file.oldMode !== file.newMode) {
    notes.push(`Changed from ${MODE_WORDS[file.oldMode]} to ${MODE_WORDS[file.newMode]}.`);
  }
  if (
    file.oldSymlinkTarget &&
    file.newSymlinkTarget &&
    file.oldSymlinkTarget !== file.newSymlinkTarget
  ) {
    notes.push(
      `The symlink pointed to ${file.oldSymlinkTarget} and now points to ${file.newSymlinkTarget}.`
    );
  } else if (file.newSymlinkTarget && file.newSymlinkTarget !== file.oldSymlinkTarget) {
    notes.push(`A symlink to ${file.newSymlinkTarget}.`);
  } else if (file.oldSymlinkTarget && file.change === "DELETED") {
    notes.push(`The symlink to ${file.oldSymlinkTarget} is removed.`);
  }
  if (file.binary) notes.push("Binary content; no line diff is shown.");
  if (file.unifiedDiff === "" && notes.length === 0) notes.push("Only the mode changed.");
  return notes;
}
