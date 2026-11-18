"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { useTranslations } from "next-intl";
import { useOrganizationId } from "@/hooks/use-organization-id";
import {
  completeOutreach,
  createOutreach,
  deleteOutreach,
  getOutreach,
  listOutreaches,
  sendOutreach,
  updateOutreach,
} from "../api";
import { outreachErrorText } from "../lib/outreach-error";
import type { OutreachQueryRequest, OutreachRequest, OutreachUpdateRequest } from "../schemas";
import { OUTREACH_KEYS } from "./query-keys";

export { OUTREACH_KEYS };

/** One page of outreaches; waits for the organization. */
export function useOutreaches(params: OutreachQueryRequest, { enabled = true } = {}) {
  const organizationId = useOrganizationId();
  return useQuery({
    queryKey: OUTREACH_KEYS.list(organizationId ?? "", params),
    queryFn: () => listOutreaches(params),
    enabled: enabled && !!organizationId && params.relationId !== "",
    // Another page or filter keeps the current rows until its answer arrives,
    // but never the rows of an organization the user just left.
    placeholderData: (previous, previousQuery) =>
      previousQuery?.queryKey[1] === organizationId ? previous : undefined,
  });
}

/** One outreach with its mail; waits for the organization and an id. */
export function useOutreach(outreachId: string, { enabled = true }: { enabled?: boolean } = {}) {
  const organizationId = useOrganizationId();
  return useQuery({
    queryKey: OUTREACH_KEYS.detail(organizationId ?? "", outreachId),
    queryFn: () => getOutreach(outreachId),
    enabled: enabled && !!organizationId && outreachId !== "",
  });
}

/**
 * An outreach change refreshes every outreach query of the organization. The
 * mail's attachment list needs no refresh: whether it is locked follows the
 * outreach's status, which this refresh reads again.
 */
function useInvalidateOutreaches() {
  const queryClient = useQueryClient();
  const organizationId = useOrganizationId();
  return () => {
    if (organizationId) {
      void queryClient.invalidateQueries({ queryKey: OUTREACH_KEYS.all(organizationId) });
    }
  };
}

export function useCreateOutreach() {
  const invalidate = useInvalidateOutreaches();
  const t = useTranslations("outreaches");

  return useMutation({
    mutationFn: (data: OutreachRequest) => createOutreach(data),
    onSuccess: () => {
      toast.success(t("toast.created"));
      invalidate();
    },
    onError: (error: unknown) => {
      toast.error(outreachErrorText(error, t, "toast.saveError"));
    },
  });
}

export function useUpdateOutreach() {
  const invalidate = useInvalidateOutreaches();
  const t = useTranslations("outreaches");

  return useMutation({
    mutationFn: ({ outreachId, data }: { outreachId: string; data: OutreachUpdateRequest }) =>
      updateOutreach(outreachId, data),
    onSuccess: () => {
      toast.success(t("toast.updated"));
      invalidate();
    },
    onError: (error: unknown) => {
      toast.error(outreachErrorText(error, t, "toast.saveError"));
    },
  });
}

/**
 * Sends an outreach. A failure also refreshes, because a refused send after
 * the claim leaves the outreach FAILED; the dialog shows the error itself,
 * so no toast is raised for it.
 */
export function useSendOutreach() {
  const invalidate = useInvalidateOutreaches();
  const t = useTranslations("outreaches");

  return useMutation({
    mutationFn: ({ outreachId, mailSenderId }: { outreachId: string; mailSenderId: string }) =>
      sendOutreach(outreachId, { mailSenderId }),
    onSuccess: () => {
      toast.success(t("toast.sent"));
      invalidate();
    },
    onError: () => {
      invalidate();
    },
  });
}

/** Records a WHATSAPP or CALL outreach as done. */
export function useCompleteOutreach() {
  const invalidate = useInvalidateOutreaches();
  const t = useTranslations("outreaches");

  return useMutation({
    mutationFn: ({ outreachId }: { outreachId: string }) => completeOutreach(outreachId),
    onSuccess: () => {
      toast.success(t("toast.completed"));
      invalidate();
    },
    onError: (error: unknown) => {
      toast.error(outreachErrorText(error, t, "toast.completeError"));
    },
  });
}

export function useDeleteOutreach() {
  const queryClient = useQueryClient();
  const invalidate = useInvalidateOutreaches();
  const organizationId = useOrganizationId();
  const t = useTranslations("outreaches");

  return useMutation({
    mutationFn: ({ outreachId }: { outreachId: string }) => deleteOutreach(outreachId),
    onSuccess: (_result, { outreachId }) => {
      toast.success(t("toast.deleted"));
      if (organizationId) {
        queryClient.removeQueries({ queryKey: OUTREACH_KEYS.detail(organizationId, outreachId) });
      }
      invalidate();
    },
    onError: (error: unknown) => {
      toast.error(outreachErrorText(error, t, "toast.deleteError"));
    },
  });
}
