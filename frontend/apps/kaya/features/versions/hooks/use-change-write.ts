"use client";

import { useQueryClient } from "@tanstack/react-query";
import { useState } from "react";

import { listChanges } from "@/features/changes/api";
import { useSubmitDraft } from "@/features/changes/hooks/use-changes";
import type { ChangeResponse } from "@/features/changes/schemas";
import type { ReadyScope } from "@/features/scope/components/scope-page";
import { ownerById } from "@/features/scope/components/owners";
import { describeError } from "@/lib/api/error-text";
import { KayaApiError } from "@/lib/api/errors";
import { useIdentity } from "@/lib/identity/identity-context";
import { VERSIONED_KEYS } from "@/lib/versioned-keys";

import { getVersion } from "../api";
import { changeLockText, useOpenChange } from "../components/open-change";
import type { EditOrigin } from "../components/draft-context";
import type { VersionDetailResponse, VersionFileResponse } from "../schemas";
import { useDraftSession } from "./use-draft-session";
import { useCreateDraft, useDeleteDraft } from "./use-versions";

/**
 * Where an edit of a pack goes: into the member's open change of that pack,
 * into a one-edit change of the pack's current version that is submitted at
 * once, or nowhere while a change of another pack is open.
 */
export type EditSource =
  | { kind: "change"; draft: VersionDetailResponse }
  | { kind: "single"; packId: string; current: VersionDetailResponse | null }
  | { kind: "locked"; openPackName: string };

/** A one-edit change that is now in review. */
export type SubmittedChange = { id: string; number: number };

/** A member rarely holds more open changes of one pack than one page lists. */
const OWN_DRAFTS_PAGE = 50;

const READ_ONLY = "A repository is read only; choose a pack to edit.";
const REASON_MISSING = "Say why the change is needed before it is submitted.";
const UNNAMED = "Kaya created the change but did not name it.";
const IN_REVIEW_HINT = "Start a change to keep this edit and submit it later.";

/** Why a continued change cannot take over an unsaved edit of a file it no longer has. */
export function missingEditText(path: string): string {
  return `Your open change of this pack no longer has ${path}; save or discard your edit first.`;
}

/** Why a continued change cannot take over an unsaved edit of a file it already changed. */
export function changedEditText(path: string): string {
  return `Your open change of this pack already changed ${path}; save or discard your edit first.`;
}

/**
 * Why a continued change cannot take over an unsaved edit, given the files it
 * holds; null when the change holds the file as the edit started from it.
 */
export function editTakeoverRefusal(
  files: readonly VersionFileResponse[],
  edited: EditOrigin
): string | null {
  const held = files.find((file: VersionFileResponse) => file.path === edited.path);
  if (edited.isNew) return held === undefined ? null : changedEditText(edited.path);
  if (held === undefined) return missingEditText(edited.path);
  return (held.sha256 ?? null) === edited.sha256 ? null : changedEditText(edited.path);
}

/** Another change of the pack is already in review, so a new one cannot be submitted now. */
const IN_REVIEW_CODE = "KAY_CFG_015";

/** A refused one-edit change; `cause` keeps the refusal the server gave. */
export class ChangeWriteError extends Error {
  constructor(message: string, cause: unknown) {
    super(message, { cause });
    this.name = "ChangeWriteError";
  }
}

/**
 * Where the edits of a scope's page go; null for a repository scope, which is
 * read only, and while the member's open change is still being read. The
 * pack's current version is the one the page already shows.
 */
export function useEditSource(ready: ReadyScope): EditSource | null {
  const session = useDraftSession(ready.scope, ready.owners);
  const { change } = useOpenChange();
  const { target } = session;
  if (target === null || session.draftLoading) return null;
  if (change === null) return { kind: "single", packId: target.id, current: ready.version };
  if (change.packId === target.id) return { kind: "change", draft: change };
  return {
    kind: "locked",
    openPackName: ownerById(ready.owners, change.packId)?.name ?? "another pack",
  };
}

/** The newest of the member's own changes of a pack still in status DRAFT, if any. */
export function newestOwnDraft(
  changes: readonly ChangeResponse[],
  packId: string,
  userId: string
): string | null {
  const own = changes
    .map((entry: ChangeResponse) => entry.version)
    .filter((version) => version.proposedBy === userId && version.packId === packId)
    .sort((left, right) => right.createdAt.localeCompare(left.createdAt));
  return own[0]?.id ?? null;
}

/**
 * Writes an edit where its source sends it. `run` saves into the open change,
 * or, for a one-edit change, creates a change, writes, and submits it with
 * `reason`; a failed step deletes the created change again, so nothing is
 * left behind, and the refusal is thrown with what the member can do next.
 * `startChange` opens a change of a pack (the member's newest one in status
 * DRAFT, else a new one) as the open change and resolves to its id. A
 * refused start leaves the page as it was, an open editor's text included.
 * `edited` is the file an unsaved edit of that pack started from: a
 * continued change that no longer has it would close the editor and lose the
 * text, and one that holds other content there would have it overwritten by
 * a save, so either refuses the start before the change opens. A new change
 * starts from the current version, which the editor started from as well.
 */
export function useChangeWrite(source: EditSource | null) {
  const { me, organization } = useIdentity();
  const queryClient = useQueryClient();
  const { change, setChangeId } = useOpenChange();
  const create = useCreateDraft();
  const submit = useSubmitDraft();
  const remove = useDeleteDraft();
  const [starting, setStarting] = useState(false);

  const cleanUp = async (failure: unknown, created: SubmittedChange): Promise<ChangeWriteError> => {
    const notes = [describeError(failure)];
    if (failure instanceof KayaApiError && failure.code === IN_REVIEW_CODE)
      notes.push(IN_REVIEW_HINT);
    try {
      await remove.mutateAsync(created.id);
    } catch (deleteFailure: unknown) {
      console.warn("[versions] A refused one-edit change could not be deleted", deleteFailure);
      notes.push(`Version ${created.number} was kept; delete it under Changes.`);
    }
    return new ChangeWriteError(notes.join(" "), failure);
  };

  const run = async (
    write: (draftId: string) => Promise<unknown>,
    reason?: string
  ): Promise<SubmittedChange | null> => {
    if (source === null) throw new Error(READ_ONLY);
    if (source.kind === "locked") throw new Error(changeLockText(source.openPackName));
    if (source.kind === "change") {
      await write(source.draft.id);
      return null;
    }
    if (reason === undefined) throw new Error(REASON_MISSING);
    const response = await create.mutateAsync({ packId: source.packId });
    const version = response.data;
    if (!version?.id) throw new Error(UNNAMED);
    const created: SubmittedChange = { id: version.id, number: version.number };
    try {
      await write(created.id);
      await submit.mutateAsync({ draftId: created.id, data: { reason } });
    } catch (failure: unknown) {
      throw await cleanUp(failure, created);
    }
    return created;
  };

  const startChange = async (packId: string, edited: EditOrigin | null = null): Promise<string> => {
    if (source?.kind === "locked") throw new Error(changeLockText(source.openPackName));
    if (change !== null) {
      if (change.packId === packId) return change.id;
      throw new Error(changeLockText("another pack"));
    }
    setStarting(true);
    try {
      const drafts = await listChanges({
        status: "DRAFT",
        packId,
        page: 1,
        amount: OWN_DRAFTS_PAGE,
      });
      let id = newestOwnDraft(drafts.data ?? [], packId, me.id);
      const continued = id !== null;
      if (id === null) {
        const response = await create.mutateAsync({ packId });
        id = response.data?.id ?? null;
        if (id === null) throw new Error(UNNAMED);
      }
      // Read before it becomes the open change: an open change still loading
      // gives a page no edit source, which unmounts its editor and loses the
      // text the member has not saved yet.
      const changeId = id;
      const detail = await queryClient.fetchQuery({
        queryKey: VERSIONED_KEYS.version(organization.organizationId, changeId),
        queryFn: () => getVersion(changeId),
      });
      if (continued && edited !== null) {
        const refusal = editTakeoverRefusal(detail.data?.files ?? [], edited);
        if (refusal !== null) throw new Error(refusal);
      }
      setChangeId(id);
      return id;
    } finally {
      setStarting(false);
    }
  };

  return {
    run,
    startChange,
    isPending: starting || create.isPending || submit.isPending || remove.isPending,
  };
}
