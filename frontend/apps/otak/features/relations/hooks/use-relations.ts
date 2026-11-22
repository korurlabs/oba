"use client";

import {
  useMutation,
  useQuery,
  useQueryClient,
  type QueryClient,
  type QueryKey,
} from "@tanstack/react-query";
import { toast } from "sonner";
import { useTranslations } from "next-intl";
import { isApiError } from "@oba/shared/api/axios";
import { useOrganizationId } from "@/hooks/use-organization-id";
import {
  changeRelationStage,
  changeRelationType,
  createRelation,
  deleteRelation,
  getRelation,
  getRelationBattlefield,
  listRelationAttachments,
  listRelationOwnerCandidates,
  listRelations,
  updateRelation,
} from "../api";
import { applyBattlefieldMove, rollbackBattlefieldMove } from "../lib/battlefield";
import { OWNER_WITHOUT_OTAK_ACCESS_CODE, relationErrorText } from "../lib/relation-error";
import {
  PipelineStage,
  type RelationCreateRequest,
  type RelationQueryRequest,
  type RelationType,
  type RelationUpdateRequest,
} from "../schemas";
import { RELATION_KEYS } from "./query-keys";

export { RELATION_KEYS };

/** One page of relations, of one type or of every type; waits for the organization. */
export function useRelations(params: RelationQueryRequest) {
  const organizationId = useOrganizationId();
  return useQuery({
    queryKey: RELATION_KEYS.list(organizationId ?? "", params),
    queryFn: () => listRelations(params),
    enabled: !!organizationId,
    // A new filter or page keeps the current rows until its answer arrives,
    // but never the rows of an organization the user just left.
    placeholderData: (previous, previousQuery) =>
      previousQuery?.queryKey[1] === organizationId ? previous : undefined,
  });
}

export function useRelation(relationId: string) {
  const organizationId = useOrganizationId();
  return useQuery({
    queryKey: RELATION_KEYS.detail(organizationId ?? "", relationId),
    queryFn: () => getRelation(relationId),
    enabled: !!organizationId && relationId !== "",
  });
}

/**
 * The files of the relation and of its mails; waits for the organization.
 * Uploads, deletes and snapshots change this list from other views and from
 * the CLI, so every mount reads it again.
 */
export function useRelationAttachments(relationId: string) {
  const organizationId = useOrganizationId();
  return useQuery({
    queryKey: RELATION_KEYS.attachments(organizationId ?? "", relationId),
    queryFn: () => listRelationAttachments(relationId),
    enabled: !!organizationId && relationId !== "",
    refetchOnMount: "always",
  });
}

/**
 * The members the owner select offers, each with whether they have Otak
 * access; waits for the organization.
 */
export function useRelationOwnerCandidates() {
  const organizationId = useOrganizationId();
  return useQuery({
    queryKey: RELATION_KEYS.ownerCandidates(organizationId ?? ""),
    queryFn: () => listRelationOwnerCandidates(),
    enabled: !!organizationId,
  });
}

/**
 * After a save is refused because the new owner has no Otak access, the
 * owner select reloads, so it shows that member as one who cannot be picked.
 */
function refreshOwnerCandidatesOnAccessRefusal(
  queryClient: QueryClient,
  organizationId: string | null,
  error: unknown
): void {
  if (organizationId && isApiError(error, OWNER_WITHOUT_OTAK_ACCESS_CODE)) {
    void queryClient.invalidateQueries({
      queryKey: RELATION_KEYS.ownerCandidates(organizationId),
    });
  }
}

/**
 * Refreshes what a change of one relation alters: the relation, every list
 * (its row can move between pages, filters and tabs) and the battlefield.
 * Pass no relation id when the relation is gone or new.
 */
export async function invalidateRelationQueries(
  queryClient: QueryClient,
  organizationId: string,
  relationId?: string
): Promise<void> {
  const keys: QueryKey[] = [
    RELATION_KEYS.lists(organizationId),
    RELATION_KEYS.battlefield(organizationId),
  ];
  if (relationId !== undefined) {
    keys.push(RELATION_KEYS.detail(organizationId, relationId));
  }
  await Promise.all(keys.map((queryKey) => queryClient.invalidateQueries({ queryKey })));
}

export function useCreateRelation() {
  const queryClient = useQueryClient();
  const organizationId = useOrganizationId();
  const t = useTranslations("relations");

  return useMutation({
    mutationFn: (data: RelationCreateRequest) => createRelation(data),
    onSuccess: () => {
      toast.success(t("toast.created"));
      if (organizationId) {
        void invalidateRelationQueries(queryClient, organizationId);
      }
    },
    onError: (error: unknown) => {
      toast.error(relationErrorText(error, t, "toast.saveError"));
      refreshOwnerCandidatesOnAccessRefusal(queryClient, organizationId, error);
    },
  });
}

export function useUpdateRelation(relationId: string) {
  const queryClient = useQueryClient();
  const organizationId = useOrganizationId();
  const t = useTranslations("relations");

  return useMutation({
    mutationFn: (data: RelationUpdateRequest) => updateRelation(relationId, data),
    onSuccess: (response) => {
      toast.success(t("toast.updated"));
      if (organizationId) {
        // The answer is the stored relation, so the detail shows it at once.
        queryClient.setQueryData(RELATION_KEYS.detail(organizationId, relationId), response);
        void invalidateRelationQueries(queryClient, organizationId, relationId);
      }
    },
    onError: (error: unknown) => {
      toast.error(relationErrorText(error, t, "toast.saveError"));
      refreshOwnerCandidatesOnAccessRefusal(queryClient, organizationId, error);
    },
  });
}

/** The relation is gone for good, so its detail entry is dropped, not reloaded into a 404. */
export function useDeleteRelation() {
  const queryClient = useQueryClient();
  const organizationId = useOrganizationId();
  const t = useTranslations("relations");

  return useMutation({
    mutationFn: (relationId: string) => deleteRelation(relationId),
    onSuccess: (_result, relationId) => {
      toast.success(t("toast.deleted"));
      if (organizationId) {
        const detailKey = RELATION_KEYS.detail(organizationId, relationId);
        queryClient.removeQueries({ queryKey: detailKey });
        void invalidateRelationQueries(queryClient, organizationId);
      }
    },
    onError: (error: unknown) => {
      toast.error(relationErrorText(error, t, "toast.deleteError"));
    },
  });
}

/** Every lead and customer of the organization for the battlefield, every stage. */
export function useRelationBattlefield() {
  const organizationId = useOrganizationId();
  return useQuery({
    queryKey: RELATION_KEYS.battlefield(organizationId ?? ""),
    queryFn: () => getRelationBattlefield(),
    enabled: !!organizationId,
  });
}

interface StageChange {
  relationId: string;
  stage: PipelineStage;
  /** Names the relation in the toast when the answer carries no relation. */
  name: string;
}

/**
 * Moves a lead to another stage. CLOSED makes the lead a customer in the same
 * request, and the battlefield shows that card as a customer at once. A
 * refused move puts the card back, as a lead again, and says why. A plain move
 * needs no success toast, since the card already stands where the user put
 * it. A move to CLOSED says that the lead is now a customer, or the user finds
 * it missing from the leads list without knowing why; the toast lives here, not
 * in the caller, because a caller's per-call callbacks run only for its latest
 * move and an earlier conversion would then pass in silence.
 */
export function useChangeRelationStage() {
  const queryClient = useQueryClient();
  const organizationId = useOrganizationId();
  const t = useTranslations("relations");
  const mutationKey = RELATION_KEYS.stageChanges(organizationId ?? "");

  return useMutation({
    mutationKey,
    mutationFn: ({ relationId, stage }: StageChange) => changeRelationStage(relationId, stage),
    onMutate: async ({ relationId, stage }: StageChange) => {
      if (!organizationId) return null;
      // A battlefield answer still on its way would overwrite the move.
      await queryClient.cancelQueries({ queryKey: RELATION_KEYS.battlefield(organizationId) });
      return applyBattlefieldMove(queryClient, organizationId, relationId, stage);
    },
    onSuccess: (response, { stage, name }: StageChange) => {
      if (stage === PipelineStage.CLOSED) {
        toast.success(t("battlefield.closedAsCustomer", { name: response.data?.name ?? name }));
      }
    },
    onError: (error: unknown, _variables, context) => {
      if (organizationId && context) {
        rollbackBattlefieldMove(queryClient, organizationId, context);
      }
      toast.error(relationErrorText(error, t, "toast.moveError"));
    },
    onSettled: (_response, _error, { relationId }) => {
      if (!organizationId) return;
      // While another move is still on its way, a fresh battlefield would show that
      // card at its old stage for a moment; the last move to settle reloads it.
      if (queryClient.isMutating({ mutationKey }) > 1) {
        void Promise.all([
          queryClient.invalidateQueries({ queryKey: RELATION_KEYS.lists(organizationId) }),
          queryClient.invalidateQueries({
            queryKey: RELATION_KEYS.detail(organizationId, relationId),
          }),
        ]);
        return;
      }
      void invalidateRelationQueries(queryClient, organizationId, relationId);
    },
  });
}

/**
 * Gives the relation another type. The relation moves to another tab, so the
 * lists and the battlefield reload with it. The caller says what became of
 * the stage, which depends on the type it came from.
 */
export function useChangeRelationType(relationId: string) {
  const queryClient = useQueryClient();
  const organizationId = useOrganizationId();
  const t = useTranslations("relations");

  return useMutation({
    mutationFn: (type: RelationType) => changeRelationType(relationId, type),
    onSuccess: (response) => {
      if (organizationId) {
        queryClient.setQueryData(RELATION_KEYS.detail(organizationId, relationId), response);
        void invalidateRelationQueries(queryClient, organizationId, relationId);
      }
    },
    onError: (error: unknown) => {
      toast.error(relationErrorText(error, t, "toast.typeChangeError"));
    },
  });
}
