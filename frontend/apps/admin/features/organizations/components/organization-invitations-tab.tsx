"use client";

import { useCallback, useState } from "react";
import { useTranslations } from "next-intl";
import { Plus } from "lucide-react";
import { Button } from "@oba/ui/sky/button";
import { EmptyState } from "@oba/ui/sky/page-states";
import {
  useInvitations,
  useRevokeInvitation,
} from "../hooks/use-invitations";
import { useInvitationColumns } from "../hooks/use-invitation-columns";
import { CreateInvitationDialog } from "./create-organization-invitation-dialog";
import { DataTable } from "@/components/data-table";
import type { Invitation, OrganizationRole } from "@oba/shared/features/organizations/schemas";

interface InvitationsTabProps {
  organizationId: string;
  canEdit: boolean;
  currentUserRole: OrganizationRole | undefined;
}

export function InvitationsTab({
  organizationId,
  canEdit,
  currentUserRole,
}: InvitationsTabProps) {
  const t = useTranslations("organization");
  const [dialogOpen, setDialogOpen] = useState(false);

  const { data: invitationsResponse, isLoading } =
    useInvitations(organizationId);
  const { mutate: revokeInvitation, isPending: isRevoking } =
    useRevokeInvitation(organizationId);

  const invitations = invitationsResponse?.data ?? [];

  // A dependency of the column memo: a new identity would remount every cell
  // and close an open revoke confirmation.
  const handleRevoke = useCallback(
    (invitationId: string) => {
      revokeInvitation(invitationId);
    },
    [revokeInvitation],
  );

  const columns = useInvitationColumns({
    canEdit,
    onRevoke: handleRevoke,
    isRevoking,
  });

  return (
    <section className="flex flex-col gap-4" aria-labelledby="organization-invitations-title">
      <div className="flex min-h-9 flex-wrap items-center justify-between gap-3">
        <h2 id="organization-invitations-title" className="text-xl">
          {t("invitations.title")}
        </h2>
        {canEdit && (
          <Button size="sm" onClick={() => setDialogOpen(true)}>
            <Plus aria-hidden="true" />
            {t("invitations.invite")}
          </Button>
        )}
      </div>

      <DataTable<Invitation>
        columns={columns}
        data={invitations}
        isLoading={isLoading}
        loadingLabel={t("invitations.loading")}
        getRowId={(invitation: Invitation) => invitation.id}
        emptyMessage={
          <EmptyState
            title={t("invitations.noInvitations")}
            description={t("invitations.noInvitationsDescription")}
          />
        }
      />

      {canEdit && (
        <CreateInvitationDialog
          organizationId={organizationId}
          currentUserRole={currentUserRole}
          open={dialogOpen}
          onOpenChange={setDialogOpen}
        />
      )}
    </section>
  );
}
