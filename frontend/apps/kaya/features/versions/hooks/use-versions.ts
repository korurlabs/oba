"use client";

import {
  useMutation,
  useQueries,
  useQuery,
  useQueryClient,
  type QueryClient,
} from "@tanstack/react-query";

import { KayaApiError } from "@/lib/api/errors";
import { useIdentity } from "@/lib/identity/identity-context";
import { VERSIONED_KEYS } from "@/lib/versioned-keys";

import {
  clearOverride,
  createDraft,
  deleteDraft,
  deleteDraftFile,
  getVersion,
  getVersionDiff,
  getVersionFile,
  rebaseDraft,
  restoreVersion,
  saveDraftFile,
  setOverrideBase,
} from "../api";
import type {
  DraftCreateRequest,
  DraftFileSaveRequest,
  DraftRebaseRequest,
  ItemOverrideRequest,
} from "../schemas";

/** A file a content read names: the version that holds it and its path. */
export type FileRef = { versionId: string; path: string };

const KEYS = {
  version: VERSIONED_KEYS.version,
  file: (organizationId: string, versionId: string, path: string) =>
    [...VERSIONED_KEYS.files(organizationId, versionId), path] as const,
  diff: (
    organizationId: string,
    versionId: string,
    against: string | undefined,
    path: string | undefined
  ) => [...VERSIONED_KEYS.diffs(organizationId, versionId), against ?? "base", path ?? ""] as const,
};

/** A stored version never changes, and a draft's writes reload it by key, so reads stay fresh. */
const IMMUTABLE = { staleTime: Infinity } as const;

/**
 * A version by id. `live` reads it again whenever the window regains focus,
 * for a change whose status another tab or member may have moved on.
 */
export function useVersion(id: string, { live = false }: { live?: boolean } = {}) {
  const { organization } = useIdentity();
  return useQuery({
    queryKey: KEYS.version(organization.organizationId, id),
    queryFn: () => getVersion(id),
    enabled: id !== "",
    ...IMMUTABLE,
    ...(live ? { refetchOnWindowFocus: "always" as const } : {}),
  });
}

/** Several versions at once, such as the ancestors' versions an inherited view reads. */
export function useVersionDetails(ids: readonly string[]) {
  const { organization } = useIdentity();
  return useQueries({
    queries: ids.map((id: string) => ({
      queryKey: KEYS.version(organization.organizationId, id),
      queryFn: () => getVersion(id),
      ...IMMUTABLE,
    })),
  });
}

/** One file's content; disabled until both the version and the path are known. */
export function useVersionFile(versionId: string, path: string) {
  const { organization } = useIdentity();
  return useQuery({
    queryKey: KEYS.file(organization.organizationId, versionId, path),
    queryFn: () => getVersionFile(versionId, path),
    enabled: versionId !== "" && path !== "",
    ...IMMUTABLE,
  });
}

/** Several files' contents at once, such as the files of the skills a page lists. */
export function useVersionFiles(files: readonly FileRef[]) {
  const { organization } = useIdentity();
  return useQueries({
    queries: files.map((file: FileRef) => ({
      queryKey: KEYS.file(organization.organizationId, file.versionId, file.path),
      queryFn: () => getVersionFile(file.versionId, file.path),
      ...IMMUTABLE,
    })),
  });
}

/** The diff of a version against `against`, or its base when absent; `path` limits it to one file. */
export function useVersionDiff(versionId: string, against?: string, path?: string) {
  const { organization } = useIdentity();
  return useQuery({
    queryKey: KEYS.diff(organization.organizationId, versionId, against, path),
    queryFn: () => getVersionDiff(versionId, against, path),
    enabled: versionId !== "",
  });
}

/**
 * Reloads what a draft write changes: its files, diffs, kind lists, links and
 * the change keys, which hold the file histories and the draft's checks.
 */
export async function invalidateDraft(
  queryClient: QueryClient,
  organizationId: string,
  draftId: string
): Promise<void> {
  await Promise.all(
    [
      VERSIONED_KEYS.version(organizationId, draftId),
      VERSIONED_KEYS.files(organizationId, draftId),
      VERSIONED_KEYS.diffs(organizationId, draftId),
      VERSIONED_KEYS.kinds(organizationId),
      VERSIONED_KEYS.effective(organizationId),
      VERSIONED_KEYS.changes(organizationId),
    ].map((queryKey) => queryClient.invalidateQueries({ queryKey }))
  );
}

/**
 * A refusal for a moved base (409 KAY_CFG_002) means another change of the
 * pack was approved after the packs were read. Reloading them
 * lets the editor see the draft as stale and offer its rebase, and reloading
 * the change lists refreshes what they say about the base.
 * A stale override base (409 KAY_CFG_032) means the parent pack was approved
 * again after the effective items were read; reloading them gives the
 * override dialog the parent's current version and diff, so a retry can pass.
 */
export async function refreshAfterRefusal(
  queryClient: QueryClient,
  organizationId: string,
  error: unknown
): Promise<void> {
  if (!(error instanceof KayaApiError)) return;
  const keys =
    error.code === "KAY_CFG_002"
      ? [VERSIONED_KEYS.owners(organizationId), VERSIONED_KEYS.changes(organizationId)]
      : error.code === "KAY_CFG_032"
        ? [VERSIONED_KEYS.effective(organizationId)]
        : [];
  await Promise.all(keys.map((queryKey) => queryClient.invalidateQueries({ queryKey })));
}

/**
 * A version mutation; the dialog or editor that ran it shows the refusal,
 * so the hook only logs it, and reloads what a stale base refusal makes outdated.
 */
export function useVersionMutation<TArgs, TResult>(
  mutationFn: (args: TArgs) => Promise<TResult>,
  refusal: string,
  onSuccess: (queryClient: QueryClient, organizationId: string, args: TArgs) => Promise<void>
) {
  const queryClient = useQueryClient();
  const { organization } = useIdentity();
  return useMutation({
    mutationFn,
    onSuccess: (_result: TResult, args: TArgs) =>
      onSuccess(queryClient, organization.organizationId, args),
    onError: (error: unknown) => {
      console.warn(`[versions] ${refusal}`, error);
      return refreshAfterRefusal(queryClient, organization.organizationId, error);
    },
  });
}

export function useRestoreVersion() {
  return useVersionMutation(
    (versionId: string) => restoreVersion(versionId),
    "Restore refused",
    async (queryClient, organizationId) => {
      await queryClient.invalidateQueries({ queryKey: VERSIONED_KEYS.changes(organizationId) });
    }
  );
}

export function useCreateDraft() {
  return useVersionMutation(
    (data: DraftCreateRequest) => createDraft(data),
    "Draft refused",
    async (queryClient, organizationId) => {
      await queryClient.invalidateQueries({ queryKey: VERSIONED_KEYS.changes(organizationId) });
    }
  );
}

export function useSaveDraftFile() {
  return useVersionMutation(
    ({ draftId, data }: { draftId: string; data: DraftFileSaveRequest }) =>
      saveDraftFile(draftId, data),
    "File save refused",
    (queryClient, organizationId, { draftId }) =>
      invalidateDraft(queryClient, organizationId, draftId)
  );
}

export function useDeleteDraftFile() {
  return useVersionMutation(
    ({ draftId, path }: { draftId: string; path: string }) => deleteDraftFile(draftId, path),
    "File delete refused",
    (queryClient, organizationId, { draftId }) =>
      invalidateDraft(queryClient, organizationId, draftId)
  );
}

/** The deleted draft's own entries are dropped first, so nothing refetches a 404. */
export function useDeleteDraft() {
  return useVersionMutation(
    (draftId: string) => deleteDraft(draftId),
    "Draft delete refused",
    async (queryClient, organizationId, draftId) => {
      [
        [...VERSIONED_KEYS.changes(organizationId), "detail", draftId],
        VERSIONED_KEYS.version(organizationId, draftId),
        VERSIONED_KEYS.files(organizationId, draftId),
        VERSIONED_KEYS.diffs(organizationId, draftId),
      ].forEach((queryKey) => queryClient.removeQueries({ queryKey }));
      await queryClient.invalidateQueries({ queryKey: VERSIONED_KEYS.changes(organizationId) });
    }
  );
}

export function useRebaseDraft() {
  return useVersionMutation(
    ({ draftId, data }: { draftId: string; data?: DraftRebaseRequest }) =>
      rebaseDraft(draftId, data),
    "Rebase refused",
    (queryClient, organizationId, { draftId }) =>
      invalidateDraft(queryClient, organizationId, draftId)
  );
}

/** Keep or Update: the draft's override of an item takes the parent's current version as its base. */
export function useSetOverrideBase() {
  return useVersionMutation(
    ({ draftId, data }: { draftId: string; data: ItemOverrideRequest }) =>
      setOverrideBase(draftId, data),
    "Override base refused",
    (queryClient, organizationId, { draftId }) =>
      invalidateDraft(queryClient, organizationId, draftId)
  );
}

/** Drops the override record of an item the parent removed, keeping the item as own. */
export function useClearOverride() {
  return useVersionMutation(
    ({ draftId, path }: { draftId: string; path: string }) => clearOverride(draftId, path),
    "Override clear refused",
    (queryClient, organizationId, { draftId }) =>
      invalidateDraft(queryClient, organizationId, draftId)
  );
}
