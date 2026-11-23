"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { useTranslations } from "next-intl";
import {
  listInvitations,
  createInvitation,
  revokeInvitation,
} from "../api";
import type { CreateInvitationForm } from "@oba/shared/features/organizations/schemas";
import { INVITATION_KEYS } from "./query-keys";

/**
 * Query keys live in the boundary-safe `./query-keys` module (no "use client").
 * Re-exported here for client consumers.
 */
export { INVITATION_KEYS };

export function useInvitations(organizationId: string | null | undefined) {
  return useQuery({
    queryKey: INVITATION_KEYS.list(organizationId ?? ""),
    queryFn: () => listInvitations(organizationId!),
    enabled: !!organizationId,
  });
}

export function useCreateInvitation(organizationId: string) {
  const t = useTranslations("organization");
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: CreateInvitationForm) =>
      createInvitation(organizationId, data),
    onSuccess: () => {
      toast.success(t("invitations.createSuccess"));
      queryClient.invalidateQueries({
        queryKey: INVITATION_KEYS.list(organizationId),
      });
    },
    onError: (error: Error) => {
      toast.error(error.message || t("invitations.createError"));
    },
  });
}

export function useRevokeInvitation(organizationId: string) {
  const t = useTranslations("organization");
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (invitationId: string) =>
      revokeInvitation(organizationId, invitationId),
    onSuccess: () => {
      toast.success(t("invitations.revokeSuccess"));
      queryClient.invalidateQueries({
        queryKey: INVITATION_KEYS.list(organizationId),
      });
    },
    onError: (error: Error) => {
      toast.error(error.message || t("invitations.revokeError"));
    },
  });
}
