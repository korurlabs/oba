"use client";

import { editTarget, type Scope } from "@/features/scope/components/scope-data";
import type { Owner } from "@/features/scope/schemas";

import { useOpenChange } from "../components/open-change";
import type { VersionDetailResponse } from "../schemas";

export type DraftSession = {
  /** The scope the page shows; null while it loads. */
  scope: Scope | null;
  /** The id of `draft`; null when no change of the target is open. */
  draftId: string | null;
  /** The pack an edit in this scope changes; null for a repository scope, which is read only. */
  target: Owner | null;
  /** The member's open change, only when it changes the target. */
  draft: VersionDetailResponse | null;
  /** The open change is still being read, so whether it changes the target is not known yet. */
  draftLoading: boolean;
  /** The draft's base is no longer the target's current version, so it must be rebased before submit. */
  stale: boolean;
  /** Opens a change of the member, or forgets the open one with null. */
  setDraftId: (draftId: string | null) => void;
};

/**
 * The editing state of a versioned page: which pack an edit changes, and the
 * member's open change when it is of that pack. The open change is the
 * member's across every page; a page of another pack does not edit it.
 */
export function useDraftSession(
  scope: Scope | null,
  owners: readonly Owner[] | undefined
): DraftSession {
  const { change, loading, setChangeId } = useOpenChange();
  const target = scope && owners ? editTarget(scope, owners) : null;
  const draft = change !== null && target !== null && change.packId === target.id ? change : null;

  return {
    scope,
    draftId: draft?.id ?? null,
    target,
    draft,
    draftLoading: loading,
    stale: draft !== null && (draft.baseVersionId ?? null) !== (target?.currentVersionId ?? null),
    setDraftId: setChangeId,
  };
}
