"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useSession } from "next-auth/react";
import { toast } from "sonner";
import { useTranslations } from "next-intl";
import { USERS_KEYS } from "@oba/shared/features/users/hooks/use-get-me";
import { invalidateAllOrganizationQueries } from "@oba/shared/hooks/use-invalidate-org-queries";
import { decideInvitation } from "../api";
import {
  InvitationDecision,
  type InvitationDecisionRequest,
} from "../schemas";

const REMIND_LATER_DURATION_MS = 60 * 60 * 1000; // 1 hour

export function useDecideInvitation() {
  const t = useTranslations("pendingInvitations");
  const queryClient = useQueryClient();
  const { update } = useSession();

  return useMutation({
    mutationFn: ({ invitationId, decision }: InvitationDecisionRequest) =>
      decideInvitation(invitationId, decision),
    onSuccess: async (_, { invitationId, decision }) => {
      toast.success(t("decisionSuccess"));

      // The session goes first: accepting can give the user a default
      // organization, and the refetches below must already carry it in the
      // X-Organization-ID header.
      try {
        const session = await update({ removedInvitationId: invitationId });
        if (!session) {
          console.error(
            "[useDecideInvitation] session update was not applied; the session still lists the invitation until its next refresh",
          );
        }
      } catch (error) {
        // rule-10-no-op -- reason: the server already stored the decision and
        // the success toast is shown. A failed session refresh must not turn
        // that into an error toast, nor skip the invalidation below.
        console.error("[useDecideInvitation] session update failed:", error);
      }

      // Awaited, so the mutation stays pending until the decided row is gone
      // and its buttons cannot be clicked a second time.
      if (decision === InvitationDecision.ACCEPT) {
        await invalidateAllOrganizationQueries(queryClient);
      } else {
        await queryClient.invalidateQueries({ queryKey: USERS_KEYS.me });
      }
    },
    onError: (error: Error) => {
      toast.error(error.message || t("decisionError"));
    },
  });
}

export function useRemindLater() {
  const t = useTranslations("pendingInvitations");
  const { update } = useSession();

  return useMutation({
    mutationFn: async (invitationId: string) => {
      const remindAt = Date.now() + REMIND_LATER_DURATION_MS;
      // `update()` never throws: it resolves with null when the request
      // failed and with undefined when another update was already in flight.
      // In both cases the token did not receive the reminder.
      const session = await update({
        remindLaterInvitationId: invitationId,
        remindAt,
      });
      if (!session) {
        throw new Error(t("remindLaterError"));
      }
      return session;
    },
    onSuccess: () => {
      toast.success(t("remindLaterSuccess"));
    },
    onError: (error: Error) => {
      toast.error(error.message || t("remindLaterError"));
    },
  });
}
