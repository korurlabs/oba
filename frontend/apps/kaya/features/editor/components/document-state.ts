import { STATUS_BADGES } from "@/features/changes/components/change-data";
import type { FileHistoryResponse, ReviewResponse } from "@/features/changes/schemas";
import type { VersionStatus } from "@/features/versions/schemas";

/** A version as the document names it: its number and status, null while unknown. */
export type VersionName = { number: number; status: VersionStatus } | null;

/**
 * What a document shows and allows, from the version it shows and who may
 * edit it: the current version, the viewer's draft, a draft a reviewer sent
 * back, the current version while another version of the file is in review,
 * an older version, or a file of a pack the scope does not edit.
 */
export type DocumentState =
  | { kind: "CURRENT"; version: VersionName }
  | { kind: "DRAFT" }
  | { kind: "CHANGES_REQUESTED"; review: ReviewResponse | null }
  | { kind: "IN_REVIEW"; entry: FileHistoryResponse }
  | { kind: "OLDER"; version: VersionName }
  | { kind: "INHERITED" };

export type DocumentStateInput = {
  /** The older version shown read-only; null for the live document. */
  at: string | null;
  /** The number and status of `at`, or of the version the live document shows. */
  version: VersionName;
  /** Whether the file belongs to another pack than the one the scope edits. */
  inherited: boolean;
  /** Whether the file is open in the viewer's draft. */
  editing: boolean;
  /** When a reviewer decided on the viewer's draft; set once it was sent back. */
  draftDecidedAt: string | null | undefined;
  /** The draft's reviews, oldest first. */
  draftReviews: readonly ReviewResponse[];
  /** The newest entries of the file's history. */
  history: readonly FileHistoryResponse[];
};

/**
 * The reviewer's rejection a draft returned with: a draft with a decision
 * time whose latest approval or rejection is a rejection. The server never
 * sets REJECTED, since a rejection returns the version to DRAFT.
 */
export function rejectionOf(
  decidedAt: string | null | undefined,
  reviews: readonly ReviewResponse[]
): ReviewResponse | null {
  if (decidedAt == null) return null;
  const decision = [...reviews]
    .reverse()
    .find(
      (review: ReviewResponse) => review.decision === "APPROVE" || review.decision === "REJECT"
    );
  return decision?.decision === "REJECT" ? decision : null;
}

/** The version of the file's owner in review that changed the file, if any. */
export function inReviewEntry(history: readonly FileHistoryResponse[]): FileHistoryResponse | null {
  return history.find((entry: FileHistoryResponse) => entry.status === "IN_REVIEW") ?? null;
}

/**
 * The state of a document. An older version wins, since it is what the page
 * shows; a file of another owner is never edited here; the viewer's draft
 * comes before a version in review, which the draft does not wait for.
 */
export function documentState(input: DocumentStateInput): DocumentState {
  if (input.at !== null) return { kind: "OLDER", version: input.version };
  if (input.inherited) return { kind: "INHERITED" };
  if (input.editing) {
    if (input.draftDecidedAt == null) return { kind: "DRAFT" };
    const review = rejectionOf(input.draftDecidedAt, input.draftReviews);
    return review === null ? { kind: "DRAFT" } : { kind: "CHANGES_REQUESTED", review };
  }
  const entry = inReviewEntry(input.history);
  if (entry !== null) return { kind: "IN_REVIEW", entry };
  return { kind: "CURRENT", version: input.version };
}

type BadgeVariant = "muted" | "warning" | "success" | "error" | "info" | "secondary" | "outline";

function versionText(version: VersionName): string | null {
  if (version === null) return null;
  return `${STATUS_BADGES[version.status].label}, version ${version.number}`;
}

/** The header badge of a state: "Approved, version 7", "Open change", "In review" and so on. */
export function stateBadge(state: DocumentState): { label: string; variant: BadgeVariant } {
  switch (state.kind) {
    case "CURRENT":
      return {
        label: versionText(state.version) ?? "Current",
        variant: state.version ? STATUS_BADGES[state.version.status].variant : "success",
      };
    case "DRAFT":
      return { label: "Open change", variant: "muted" };
    case "CHANGES_REQUESTED":
      return { label: "Changes requested", variant: "error" };
    case "IN_REVIEW":
      return { label: `In review, version ${state.entry.number}`, variant: "warning" };
    case "OLDER":
      return { label: versionText(state.version) ?? "Older version", variant: "outline" };
    case "INHERITED":
      return { label: "Inherited", variant: "secondary" };
  }
}
