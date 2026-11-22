"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { useTranslations } from "next-intl";
import { isApiError } from "@oba/shared/api/axios";
import {
  createLegalEntity,
  deleteLegalEntity,
  getLegalEntity,
  listLegalEntities,
  updateLegalEntity,
} from "../api";
import type { LegalEntityRequest } from "../schemas";
import { LEGAL_ENTITY_KEYS } from "./query-keys";

/**
 * Query keys live in the boundary-safe `./query-keys` module (no "use client").
 * Re-exported here for client consumers.
 */
export { LEGAL_ENTITY_KEYS };

/** The API's answer for a legal entity that is gone or belongs to another organization. */
export const LEGAL_ENTITY_NOT_FOUND = "LGE_NOT_001";

/** The API's answer when another legal entity of the organization has the short name. */
export const LEGAL_ENTITY_SHORT_NAME_TAKEN = "LGE_STA_001";

/** The API's answer for a body that fails bean validation; the values name the fields. */
export const VALIDATION_FAILED = "GEN_VAL_001";

export interface LegalEntityFormMutationOptions {
  /**
   * Error codes that the caller shows on its form as a field error. The hook
   * shows no toast for them, so the user does not read the same error twice.
   */
  formErrorCodes?: readonly string[];
}

function isFormError(error: Error, options: LegalEntityFormMutationOptions | undefined): boolean {
  return options?.formErrorCodes?.some((code) => isApiError(error, code)) ?? false;
}

export function useLegalEntities(organizationId: string | null | undefined) {
  return useQuery({
    queryKey: LEGAL_ENTITY_KEYS.list(organizationId ?? ""),
    queryFn: () => listLegalEntities(organizationId!),
    enabled: !!organizationId,
  });
}

export function useLegalEntity(
  organizationId: string | null | undefined,
  legalEntityId: string | null | undefined
) {
  return useQuery({
    queryKey: LEGAL_ENTITY_KEYS.detail(organizationId ?? "", legalEntityId ?? ""),
    queryFn: () => getLegalEntity(organizationId!, legalEntityId!),
    enabled: !!organizationId && !!legalEntityId,
  });
}

export function useCreateLegalEntity(
  organizationId: string,
  options?: LegalEntityFormMutationOptions
) {
  const t = useTranslations("organization");
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: LegalEntityRequest) => createLegalEntity(organizationId, data),
    onSuccess: () => {
      toast.success(t("legalEntities.createSuccess"));
      // Returned so the mutation stays pending until the list has refetched.
      return queryClient.invalidateQueries({
        queryKey: LEGAL_ENTITY_KEYS.list(organizationId),
      });
    },
    onError: (error: Error) => {
      if (isFormError(error, options)) return;
      toast.error(error.message || t("legalEntities.createError"));
    },
  });
}

export function useUpdateLegalEntity(
  organizationId: string,
  options?: LegalEntityFormMutationOptions
) {
  const t = useTranslations("organization");
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ legalEntityId, data }: { legalEntityId: string; data: LegalEntityRequest }) =>
      updateLegalEntity(organizationId, legalEntityId, data),
    onSuccess: () => {
      toast.success(t("legalEntities.updateSuccess"));
      return queryClient.invalidateQueries({
        queryKey: LEGAL_ENTITY_KEYS.organization(organizationId),
      });
    },
    onError: (error: Error, { legalEntityId }) => {
      if (isFormError(error, options)) return;
      if (isApiError(error, LEGAL_ENTITY_NOT_FOUND)) {
        toast.error(t("legalEntities.notFound"));
        return forgetLegalEntity(queryClient, organizationId, legalEntityId);
      }
      toast.error(error.message || t("legalEntities.updateError"));
    },
  });
}

export function useDeleteLegalEntity(organizationId: string) {
  const t = useTranslations("organization");
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (legalEntityId: string) => deleteLegalEntity(organizationId, legalEntityId),
    onSuccess: (_, legalEntityId) => {
      toast.success(t("legalEntities.deleteSuccess"));
      return forgetLegalEntity(queryClient, organizationId, legalEntityId);
    },
    onError: (error: Error, legalEntityId) => {
      // Someone else deleted it first: the row goes away all the same.
      if (isApiError(error, LEGAL_ENTITY_NOT_FOUND)) {
        toast.error(t("legalEntities.notFound"));
        return forgetLegalEntity(queryClient, organizationId, legalEntityId);
      }
      toast.error(error.message || t("legalEntities.deleteError"));
    },
  });
}

/**
 * Drops a legal entity that no longer exists. Its detail is removed, not
 * invalidated, because an invalidation refetches it and the API answers 404;
 * the list then refetches without it.
 */
function forgetLegalEntity(
  queryClient: ReturnType<typeof useQueryClient>,
  organizationId: string,
  legalEntityId: string
) {
  queryClient.removeQueries({
    queryKey: LEGAL_ENTITY_KEYS.detail(organizationId, legalEntityId),
  });
  return queryClient.invalidateQueries({
    queryKey: LEGAL_ENTITY_KEYS.list(organizationId),
  });
}
