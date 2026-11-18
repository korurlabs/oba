"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { useTranslations } from "next-intl";
import { useOrganizationId } from "@/hooks/use-organization-id";
import { RELATION_KEYS } from "@/features/relations/hooks/query-keys";
import { createIcp, deleteIcp, getIcp, listIcps, updateIcp } from "../api";
import { icpErrorText } from "../lib/icp-error";
import { ICP_OPTIONS_LIMIT, type IcpQueryRequest, type IcpRequest } from "../schemas";
import { ICP_KEYS } from "./query-keys";

export { ICP_KEYS };

/** One page of the organization's ICPs; waits for the organization. */
export function useIcps(params: IcpQueryRequest) {
  const organizationId = useOrganizationId();
  return useQuery({
    queryKey: ICP_KEYS.list(organizationId ?? "", params),
    queryFn: () => listIcps(params),
    enabled: !!organizationId,
    // A new search or page keeps the current rows until its answer arrives,
    // but never the rows of an organization the user just left.
    placeholderData: (previous, previousQuery) =>
      previousQuery?.queryKey[1] === organizationId ? previous : undefined,
  });
}

/** One ICP; waits for the organization and an id. */
export function useIcp(icpId: string, { enabled = true }: { enabled?: boolean } = {}) {
  const organizationId = useOrganizationId();
  return useQuery({
    queryKey: ICP_KEYS.detail(organizationId ?? "", icpId),
    queryFn: () => getIcp(icpId),
    enabled: enabled && !!organizationId && icpId !== "",
  });
}

const ICP_OPTIONS_PARAMS: IcpQueryRequest = { page: 1, amount: ICP_OPTIONS_LIMIT };

/**
 * The ICPs a relation can be linked to: the first ones by name, as select
 * options. An organization with more ICPs than that sees only these; a stored
 * link outside them is added by the form.
 */
export function useIcpOptions() {
  const organizationId = useOrganizationId();
  return useQuery({
    queryKey: ICP_KEYS.list(organizationId ?? "", ICP_OPTIONS_PARAMS),
    queryFn: () => listIcps(ICP_OPTIONS_PARAMS),
    enabled: !!organizationId,
    select: (response) => (response.data ?? []).map((icp) => ({ value: icp.id, label: icp.name })),
  });
}

/**
 * An ICP change refreshes every ICP query of the organization: the list, the
 * details and the relation form's options.
 */
function useInvalidateIcps() {
  const queryClient = useQueryClient();
  const organizationId = useOrganizationId();
  return () => {
    if (organizationId) {
      void queryClient.invalidateQueries({ queryKey: ICP_KEYS.all(organizationId) });
    }
  };
}

export function useCreateIcp() {
  const invalidate = useInvalidateIcps();
  const t = useTranslations("icps");

  return useMutation({
    mutationFn: (data: IcpRequest) => createIcp(data),
    onSuccess: () => {
      toast.success(t("toast.created"));
      invalidate();
    },
    onError: (error: unknown) => {
      toast.error(icpErrorText(error, t, "toast.saveError"));
    },
  });
}

export function useUpdateIcp() {
  const invalidate = useInvalidateIcps();
  const t = useTranslations("icps");

  return useMutation({
    mutationFn: ({ icpId, data }: { icpId: string; data: IcpRequest }) => updateIcp(icpId, data),
    onSuccess: () => {
      toast.success(t("toast.updated"));
      invalidate();
    },
    onError: (error: unknown) => {
      toast.error(icpErrorText(error, t, "toast.saveError"));
    },
  });
}

/**
 * Deleting an ICP unlinks its relations on the server, so the relation
 * queries are refreshed too.
 */
export function useDeleteIcp() {
  const queryClient = useQueryClient();
  const organizationId = useOrganizationId();
  const t = useTranslations("icps");

  return useMutation({
    mutationFn: (icpId: string) => deleteIcp(icpId),
    onSuccess: (_result, icpId) => {
      toast.success(t("toast.deleted"));
      if (organizationId) {
        queryClient.removeQueries({ queryKey: ICP_KEYS.detail(organizationId, icpId) });
        void queryClient.invalidateQueries({ queryKey: ICP_KEYS.all(organizationId) });
        void queryClient.invalidateQueries({ queryKey: RELATION_KEYS.all(organizationId) });
      }
    },
    onError: (error: unknown) => {
      toast.error(icpErrorText(error, t, "toast.deleteError"));
    },
  });
}
