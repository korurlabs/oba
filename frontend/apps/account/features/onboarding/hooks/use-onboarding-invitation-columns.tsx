"use client";

import { useMemo } from "react";
import { useTranslations } from "next-intl";
import type { ColumnDef } from "@tanstack/react-table";
import { Check, X } from "lucide-react";
import type { InvitationResponse } from "@oba/shared/features/users/schema";
import { cn } from "@oba/ui/lib/utils";
import { Button } from "@oba/ui/sky/button";
import { OrganizationRoleBadge } from "@oba/shared/components/organization-role-badge";
import { InvitationActionDialog } from "@/features/pending-invitations/components/invitation-action-dialog";
import type { InvitationDecision } from "@/features/pending-invitations/schemas";

interface UseOnboardingInvitationColumnsOptions {
  decisions: Map<string, InvitationDecision>;
  onDecision: (id: string, decision: InvitationDecision | null) => void;
  onRemindLater: (id: string) => void;
  isPending: boolean;
}

export function useOnboardingInvitationColumns({
  decisions,
  onDecision,
  onRemindLater,
  isPending,
}: UseOnboardingInvitationColumnsOptions) {
  const t = useTranslations("onboarding");
  const tPending = useTranslations("pendingInvitations");

  return useMemo<ColumnDef<InvitationResponse, unknown>[]>(
    () => [
      {
        id: "decision",
        header: t("invitationsStep.decision"),
        cell: ({ row }) => {
          const id = row.original.id;
          const current = decisions.get(id);

          return (
            <div className="flex items-center gap-1.5">
              <Button
                type="button"
                size="sm"
                variant={current === "ACCEPT" ? "default" : "outline"}
                className={cn(
                  "h-8 gap-1 text-xs border-muted",
                  current === "ACCEPT" &&
                    "bg-green-600 text-white hover:bg-green-700",
                )}
                disabled={isPending}
                onClick={() =>
                  onDecision(id, current === "ACCEPT" ? null : "ACCEPT")
                }
              >
                <Check className="h-3.5 w-3.5" />
                {t("invitationsStep.accept")}
              </Button>
              <Button
                type="button"
                size="sm"
                variant={current === "DECLINE" ? "destructive" : "outline"}
                className="h-8 gap-1 text-xs border-muted"
                disabled={isPending}
                onClick={() =>
                  onDecision(id, current === "DECLINE" ? null : "DECLINE")
                }
              >
                <X className="h-3.5 w-3.5" />
                {t("invitationsStep.decline")}
              </Button>
              <InvitationActionDialog
                trigger={t("invitationsStep.remindLater")}
                title={tPending("remindLaterConfirmTitle")}
                description={tPending("remindLaterConfirmDescription")}
                confirmLabel={t("invitationsStep.remindLater")}
                confirmVariant="outline"
                onConfirm={() => onRemindLater(id)}
                disabled={isPending}
              />
            </div>
          );
        },
      },
      {
        id: "organizationName",
        accessorKey: "organizationName",
        header: tPending("columns.organization"),
        cell: ({ row }) => (
          <span className="font-medium">{row.original.organizationName}</span>
        ),
      },
      {
        id: "role",
        accessorKey: "role",
        header: tPending("columns.role"),
        cell: ({ row }) => <OrganizationRoleBadge role={row.original.role} />,
      },
      {
        id: "invitedByEmail",
        accessorKey: "invitedByEmail",
        header: tPending("columns.invitedBy"),
        cell: ({ row }) => (
          <span className="text-muted-foreground text-sm">
            {row.original.invitedByEmail}
          </span>
        ),
      },
    ],
    [tPending, t, decisions, onDecision, onRemindLater, isPending],
  );
}
