"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { useTranslations } from "next-intl";
import { isApiError } from "@oba/shared/api/axios";
import {
  createAzureConnection,
  deleteAzureConnection,
  getAzureConnection,
  listAzureConnections,
  updateAzureConnection,
  verifyAzureConnection,
} from "../api";
import { AzureConnectionStatus, type AzureConnectionRequest } from "../schemas";
import { AZURE_CONNECTION_KEYS } from "./query-keys";

/**
 * Query keys live in the boundary-safe `./query-keys` module (no "use client").
 * Re-exported here for client consumers.
 */
export { AZURE_CONNECTION_KEYS };

/** The API's answer for a connection that is gone or belongs to another organization. */
export const AZURE_CONNECTION_NOT_FOUND = "AZR_NOT_001";

/** The API's answer on create when this deployment has no OpenID Connect issuer. */
export const AZURE_CONNECTIONS_UNAVAILABLE = "AZR_NOT_002";

/** The API's answer on delete while a backup source still reads through the connection. */
export const AZURE_CONNECTION_IN_USE = "AZR_STA_002";

/** The API's answer when another connection of the organization has the same tenant and client id. */
export const AZURE_CONNECTION_IDENTITY_TAKEN = "AZR_STA_003";

/** The API's answer when another connection of the organization has the name. */
export const AZURE_CONNECTION_NAME_TAKEN = "AZR_STA_004";

/** The API's answer on verify while the connection has no client id. */
export const AZURE_CONNECTION_CLIENT_ID_MISSING = "AZR_VAL_001";

/** The API's answer for a body that fails bean validation; the values name the fields. */
export const VALIDATION_FAILED = "GEN_VAL_001";

export interface AzureConnectionFormMutationOptions {
  /**
   * Error codes that the caller shows on its form as a field error. The hook
   * shows no toast for them, so the user does not read the same error twice.
   */
  formErrorCodes?: readonly string[];
}

function isFormError(
  error: Error,
  options: AzureConnectionFormMutationOptions | undefined
): boolean {
  return options?.formErrorCodes?.some((code) => isApiError(error, code)) ?? false;
}

export function useAzureConnections(organizationId: string | null | undefined) {
  return useQuery({
    queryKey: AZURE_CONNECTION_KEYS.list(organizationId ?? ""),
    queryFn: () => listAzureConnections(organizationId!),
    enabled: !!organizationId,
  });
}

export function useAzureConnection(
  organizationId: string | null | undefined,
  connectionId: string | null | undefined
) {
  return useQuery({
    queryKey: AZURE_CONNECTION_KEYS.detail(organizationId ?? "", connectionId ?? ""),
    queryFn: () => getAzureConnection(organizationId!, connectionId!),
    enabled: !!organizationId && !!connectionId,
  });
}

export function useCreateAzureConnection(
  organizationId: string,
  options?: AzureConnectionFormMutationOptions
) {
  const t = useTranslations("integrations");
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: AzureConnectionRequest) => createAzureConnection(organizationId, data),
    onSuccess: () => {
      toast.success(t("azure.createSuccess"));
      // Returned so the mutation stays pending until the list has refetched.
      return queryClient.invalidateQueries({
        queryKey: AZURE_CONNECTION_KEYS.list(organizationId),
      });
    },
    onError: (error: Error) => {
      if (isFormError(error, options)) return;
      toast.error(error.message || t("azure.createError"));
    },
  });
}

export function useUpdateAzureConnection(
  organizationId: string,
  options?: AzureConnectionFormMutationOptions
) {
  const t = useTranslations("integrations");
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ connectionId, data }: { connectionId: string; data: AzureConnectionRequest }) =>
      updateAzureConnection(organizationId, connectionId, data),
    onSuccess: () => {
      toast.success(t("azure.updateSuccess"));
      return queryClient.invalidateQueries({
        queryKey: AZURE_CONNECTION_KEYS.organization(organizationId),
      });
    },
    onError: (error: Error, { connectionId }) => {
      if (isFormError(error, options)) return;
      if (isApiError(error, AZURE_CONNECTION_NOT_FOUND)) {
        toast.error(t("azure.notFound"));
        return forgetAzureConnection(queryClient, organizationId, connectionId);
      }
      toast.error(error.message || t("azure.updateError"));
    },
  });
}

export function useDeleteAzureConnection(organizationId: string) {
  const t = useTranslations("integrations");
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (connectionId: string) => deleteAzureConnection(organizationId, connectionId),
    onSuccess: (_, connectionId) => {
      toast.success(t("azure.deleteSuccess"));
      return forgetAzureConnection(queryClient, organizationId, connectionId);
    },
    onError: (error: Error, connectionId) => {
      // Someone else deleted it first: the row goes away all the same.
      if (isApiError(error, AZURE_CONNECTION_NOT_FOUND)) {
        toast.error(t("azure.notFound"));
        return forgetAzureConnection(queryClient, organizationId, connectionId);
      }
      if (isApiError(error, AZURE_CONNECTION_IN_USE)) {
        toast.error(t("azure.inUse"));
        return;
      }
      toast.error(error.message || t("azure.deleteError"));
    },
  });
}

/**
 * Checks the connection against Azure now. The API answers 200 with the
 * stored result also when Azure refused, so the toast follows the status it
 * returns, not the HTTP outcome.
 */
export function useVerifyAzureConnection(organizationId: string) {
  const t = useTranslations("integrations");
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (connectionId: string) => verifyAzureConnection(organizationId, connectionId),
    onSuccess: (response) => {
      if (response.data?.status === AzureConnectionStatus.CONNECTED) {
        toast.success(t("azure.verifySuccess"));
      } else {
        toast.error(t("azure.verifyFailed"));
      }
      return queryClient.invalidateQueries({
        queryKey: AZURE_CONNECTION_KEYS.organization(organizationId),
      });
    },
    onError: (error: Error, connectionId) => {
      if (isApiError(error, AZURE_CONNECTION_NOT_FOUND)) {
        toast.error(t("azure.notFound"));
        return forgetAzureConnection(queryClient, organizationId, connectionId);
      }
      if (isApiError(error, AZURE_CONNECTION_CLIENT_ID_MISSING)) {
        toast.error(t("azure.clientIdMissing"));
        return;
      }
      toast.error(error.message || t("azure.verifyError"));
    },
  });
}

/**
 * Drops a connection that no longer exists. Its detail is removed, not
 * invalidated, because an invalidation refetches it and the API answers 404;
 * the list then refetches without it.
 */
function forgetAzureConnection(
  queryClient: ReturnType<typeof useQueryClient>,
  organizationId: string,
  connectionId: string
) {
  queryClient.removeQueries({
    queryKey: AZURE_CONNECTION_KEYS.detail(organizationId, connectionId),
  });
  return queryClient.invalidateQueries({
    queryKey: AZURE_CONNECTION_KEYS.list(organizationId),
  });
}
