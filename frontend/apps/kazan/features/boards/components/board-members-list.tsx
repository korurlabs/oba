"use client";

import { Button } from "@/components/ui/button";
import { UserAvatar } from "@/components/ui/user-avatar";
import { Pencil, Trash2, UserPlus } from "lucide-react";
import { useMemo, useState } from "react";
import { AddBoardMemberDialog } from "./add-board-member-dialog";
import { BoardMemberRoleChip } from "./board-member-role-chip";
import { AgentBadge } from "@/components/shared/agent-badge";
import { isAgent } from "@/lib/agent-member";
import { useTranslations } from "next-intl";
import { useBoardMembers, useRemoveBoardMember } from "@/features/boards/hooks/use-board-members";
import type { BoardMemberRole } from "../schemas";

interface BoardMembersListProps {
  boardId: string;
  canManage: boolean;
}

type MemberDialogState = { type: "closed" } | { type: "add" } | { type: "edit"; userId: string };

export function BoardMembersList({ boardId, canManage }: BoardMembersListProps) {
  const t = useTranslations();
  const [dialog, setDialog] = useState<MemberDialogState>({ type: "closed" });

  const { data: membersResponse, isLoading } = useBoardMembers(boardId);
  const removeMutation = useRemoveBoardMember();

  const members = useMemo(
    () =>
      membersResponse?.data?.map((m) => ({
        userId: m.userId,
        role: m.role,
        isAgent: isAgent(m),
        agentRole: m.agentRole,
        agentProvider: m.agentProvider,
        user: {
          name: `${m.firstName} ${m.lastName}`.trim() || undefined,
          email: m.email || undefined,
          avatar: null as string | null,
          kind: m.kind,
          agentRole: m.agentRole,
        },
      })) ?? [],
    [membersResponse]
  );

  if (isLoading) {
    return <div className="text-muted-foreground text-sm">{t("board.members.loading")}</div>;
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-foreground text-sm font-medium">{t("board.members.title")}</h3>
        {canManage && (
          <Button
            size="sm"
            variant="ghost"
            onClick={() => setDialog({ type: "add" })}
            className="h-7 text-xs"
          >
            <UserPlus className="h-4 w-4" />
            {t("board.members.addMember")}
          </Button>
        )}
      </div>

      <div className="space-y-2">
        {members?.map((member) => (
            <div key={member.userId} className="flex items-center justify-between rounded-lg p-2">
              <div className="flex items-center gap-3">
                <UserAvatar user={member.user} showName={false} size="md" />
                <div className="min-w-0">
                  <div className="flex min-w-0 flex-wrap items-center gap-x-2 gap-y-0.5">
                    <span className="text-sm font-medium">{member.user.name ?? member.user.email}</span>
                    {member.isAgent && (
                      <AgentBadge agentRole={member.agentRole} agentProvider={member.agentProvider} />
                    )}
                  </div>
                  <div className="text-muted-foreground text-xs">{member.user.email}</div>
                </div>
              </div>
              <div className="flex items-center gap-2">
                <BoardMemberRoleChip role={member.role as BoardMemberRole} />
                {canManage && member.role !== "OWNER" && (
                  <>
                    {/* An agent's board role is always MEMBER, so it has no role to edit. */}
                    {!member.isAgent && (
                      <Button
                        size="sm"
                        variant="ghost"
                        className="text-muted-foreground hover:text-foreground h-8 w-8 p-0"
                        onClick={() => setDialog({ type: "edit", userId: member.userId })}
                      >
                        <Pencil className="h-4 w-4" />
                      </Button>
                    )}
                    <Button
                      size="sm"
                      variant="ghost"
                      className="text-muted-foreground hover:text-destructive h-8 w-8 p-0"
                      onClick={() => removeMutation.mutate({ boardId, userId: member.userId })}
                    >
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  </>
                )}
              </div>
            </div>
        ))}
      </div>

      <AddBoardMemberDialog
        boardId={boardId}
        open={dialog.type !== "closed"}
        onOpenChange={(open) => {
          if (!open) setDialog({ type: "closed" });
        }}
        userId={dialog.type === "edit" ? dialog.userId : null}
      />
    </div>
  );
}
