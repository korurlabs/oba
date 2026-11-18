"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { useTranslations } from "next-intl";
import { invalidateAllOrganizationQueries } from "@oba/shared/hooks/use-invalidate-org-queries";
import { decideInvitation } from "@/features/pending-invitations/api";
import type { InvitationDecision } from "@/features/pending-invitations/schemas";
import { completeOnboarding } from "../api";
import type { OnboardingCompleteRequest } from "../schemas";

export function useCompleteOnboarding() {
  const t = useTranslations("onboarding");
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: OnboardingCompleteRequest) => completeOnboarding(data),
    onSuccess: async () => {
      await invalidateAllOrganizationQueries(queryClient);
    },
    onError: (error: Error) => {
      toast.error(error.message || t("error"));
    },
  });
}

/**
 * Sends the invitation decisions made during onboarding, one after the
 * other and in the given order, and resolves with the ids that failed.
 *
 * One failed decision does not stop the rest, so the mutation itself never
 * rejects and shows no toast: the caller reports a partial failure once.
 * Nothing is invalidated, because onboarding leaves the page with a full
 * navigation right after.
 */
export function useSubmitInvitationDecisions() {
  return useMutation({
    mutationFn: async (decisions: [string, InvitationDecision][]) => {
      const failedInvitationIds: string[] = [];

      for (const [invitationId, decision] of decisions) {
        try {
          await decideInvitation(invitationId, decision);
        } catch (error) {
          console.error(
            `[Onboarding] decideInvitation failed for ${invitationId}:`,
            error,
          );
          failedInvitationIds.push(invitationId);
        }
      }

      return failedInvitationIds;
    },
  });
}
