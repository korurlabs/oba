"use client";

import { useTranslations } from "next-intl";
import type { InvitationResponse } from "@oba/shared/features/users/schema";
import { SenseyTable } from "@oba/shared/components/sensey-table";
import type { InvitationDecision } from "@/features/pending-invitations/schemas";
import { useOnboardingInvitationColumns } from "../hooks/use-onboarding-invitation-columns";

interface StepInvitationsTableProps {
  invitations: InvitationResponse[];
  decisions: Map<string, InvitationDecision>;
  onDecision: (id: string, decision: InvitationDecision | null) => void;
  onRemindLater: (id: string) => void;
  isPending: boolean;
}

export function StepInvitationsTable({
  invitations,
  decisions,
  onDecision,
  onRemindLater,
  isPending,
}: StepInvitationsTableProps) {
  const tPending = useTranslations("pendingInvitations");
  const columns = useOnboardingInvitationColumns({
    decisions,
    onDecision,
    onRemindLater,
    isPending,
  });

  return (
    <SenseyTable
      columns={columns}
      data={invitations}
      isLoading={false}
      emptyMessage={tPending("noInvitations")}
    />
  );
}
