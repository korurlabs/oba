"use client";

import { useMemo } from "react";
import { useTranslations } from "next-intl";
import { useFormContext } from "react-hook-form";
import { Loader2 } from "lucide-react";
import { Button } from "@oba/ui/sky/button";
import type { OnboardingStepperFormValues } from "../schemas";
import type { InvitationResponse } from "@oba/shared/features/users/schema";
import type { InvitationDecision } from "@/features/pending-invitations/schemas";
import { StepInvitationsTable } from "./onboarding-step-invitations-table";
import { StepCreateOrgSection } from "./onboarding-step-create-org-section";

interface StepInvitationsProps {
  invitations: InvitationResponse[];
  decisions: Map<string, InvitationDecision>;
  onDecision: (id: string, decision: InvitationDecision | null) => void;
  remindedIds: Set<string>;
  onRemindLater: (id: string) => void;
  showOrgSection: boolean;
  onToggleOrgSection: () => void;
  onBack: () => void;
  onComplete: () => void;
  isPending: boolean;
}

export function StepInvitations({
  invitations,
  decisions,
  onDecision,
  remindedIds,
  onRemindLater,
  showOrgSection,
  onToggleOrgSection,
  onBack,
  onComplete,
  isPending,
}: StepInvitationsProps) {
  const t = useTranslations("onboarding");
  const { watch } = useFormContext<OnboardingStepperFormValues>();
  const orgName = watch("name") ?? "";

  // Filter out reminded invitations from the visible list
  const visibleInvitations = useMemo(
    () => invitations.filter((inv) => !remindedIds.has(inv.id)),
    [invitations, remindedIds],
  );

  // Validation: user must either accept at least one invitation OR fill in org name
  const hasAcceptedInvitation = Array.from(decisions.values()).some(
    (d) => d === "ACCEPT",
  );
  const hasOrgCreation = showOrgSection && orgName.trim().length > 0;

  const canComplete = hasAcceptedInvitation || hasOrgCreation;

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <h2 className="text-foreground mb-1 text-lg font-semibold">
          {t("invitationsStep.title")}
        </h2>
        <p className="text-muted-foreground text-sm">
          {t("invitationsStep.subtitle")}
        </p>
      </div>

      {/* Invitation Decisions Table */}
      <StepInvitationsTable
        invitations={visibleInvitations}
        decisions={decisions}
        onDecision={onDecision}
        onRemindLater={onRemindLater}
        isPending={isPending}
      />

      {/* Optional Org Creation Section */}
      <div className="border-border rounded-lg border">
        <StepCreateOrgSection
          open={showOrgSection}
          onToggle={onToggleOrgSection}
          isPending={isPending}
        />
      </div>

      {/* Actions */}
      <div className="flex items-center justify-between">
        <Button
          type="button"
          variant="outline"
          onClick={onBack}
          disabled={isPending}
          className="h-12 px-8 text-base"
        >
          {t("back")}
        </Button>
        <Button
          type="button"
          onClick={onComplete}
          disabled={!canComplete || isPending}
          className="h-12 px-8 text-base"
        >
          {isPending ? (
            <>
              <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              {t("completing")}
            </>
          ) : (
            t("getStarted")
          )}
        </Button>
      </div>
    </div>
  );
}
