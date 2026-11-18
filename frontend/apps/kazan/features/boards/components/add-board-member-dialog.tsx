"use client";

import { useEffect, useMemo, useState } from "react";
import { useTranslations } from "next-intl";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { UserAvatar } from "@/components/ui/user-avatar";
import { Input } from "@/components/ui/input";
import { Crown, Shield, Edit, Eye, UserPlus, Pencil, Search } from "lucide-react";
import { cn } from "@/lib/utils";
import { hasKazanAccess } from "@/lib/kazan-member-access";
import {
  useAddBoardMember,
  useBoardMemberCandidates,
  useBoardMembers,
  useUpdateBoardMember,
} from "../hooks/use-board-members";
import type { BoardMemberCandidateResponse, BoardMemberRole } from "../schemas";

// ── Types ────────────────────────────────────────────────────────────

interface AddBoardMemberDialogProps {
  boardId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** If provided, opens in edit mode for existing member */
  userId?: string | null;
}

// ── Helpers ──────────────────────────────────────────────────────────

const ROLE_OPTIONS: { value: BoardMemberRole; icon: typeof Crown }[] = [
  { value: "ADMIN", icon: Shield },
  { value: "MEMBER", icon: Edit },
  { value: "VIEWER", icon: Eye },
];

function candidateName(candidate: BoardMemberCandidateResponse): string {
  return `${candidate.firstName ?? ""} ${candidate.lastName ?? ""}`.trim();
}

// ── Component ────────────────────────────────────────────────────────

export function AddBoardMemberDialog({
  boardId,
  open,
  onOpenChange,
  userId,
}: AddBoardMemberDialogProps) {
  const t = useTranslations();
  const isEditMode = !!userId;

  // ── State ──────────────────────────────────────────────────────────
  const [selectedUserId, setSelectedUserId] = useState<string | null>(null);
  const [role, setRole] = useState<BoardMemberRole>("MEMBER");
  const [search, setSearch] = useState("");

  // ── Data ───────────────────────────────────────────────────────────
  const { data: candidatesResponse, isLoading: usersLoading } = useBoardMemberCandidates(boardId, {
    enabled: open && !isEditMode,
  });
  const { data: membersResponse } = useBoardMembers(boardId, { enabled: open });

  const existingMemberIds = useMemo(
    () => new Set((membersResponse?.data ?? []).map((m) => m.userId)),
    [membersResponse?.data]
  );

  // In edit mode, find the existing member to pre-fill
  const existingMember = useMemo(() => {
    if (!isEditMode || !membersResponse?.data) return null;
    return membersResponse.data.find((m) => m.userId === userId) ?? null;
  }, [isEditMode, userId, membersResponse?.data]);

  // Filter out already-added members and apply search. Members without
  // Kazan access stay listed, so the admin sees why they cannot be added.
  const availableUsers = useMemo(() => {
    const candidates = candidatesResponse?.data ?? [];
    return candidates.filter((c) => {
      if (existingMemberIds.has(c.userId)) return false;
      if (!search) return true;
      return (
        candidateName(c).toLowerCase().includes(search.toLowerCase()) ||
        c.email.toLowerCase().includes(search.toLowerCase())
      );
    });
  }, [candidatesResponse?.data, existingMemberIds, search]);

  // ── Mutations ──────────────────────────────────────────────────────
  const addMutation = useAddBoardMember();
  const updateMutation = useUpdateBoardMember();
  const isSaving = addMutation.isPending || updateMutation.isPending;

  // ── Reset state when dialog opens/closes or mode changes ──────────
  useEffect(() => {
    if (open) {
      if (isEditMode && existingMember) {
        setRole(existingMember.role as BoardMemberRole);
        setSelectedUserId(existingMember.userId);
      } else {
        setSelectedUserId(null);
        setRole("MEMBER");
      }
      setSearch("");
    }
  }, [open, isEditMode, existingMember]);

  // ── Handlers ───────────────────────────────────────────────────────
  const handleSubmit = async () => {
    if (isEditMode && userId) {
      await updateMutation.mutateAsync({
        boardId,
        userId,
        data: { role },
      });
    } else if (selectedUserId) {
      await addMutation.mutateAsync({
        boardId,
        data: { userId: selectedUserId, role },
      });
    }
    onOpenChange(false);
  };

  const selectedUser = useMemo(() => {
    if (isEditMode && existingMember) {
      return {
        id: existingMember.userId,
        email: existingMember.email,
        name: `${existingMember.firstName} ${existingMember.lastName}`.trim(),
      };
    }
    if (!selectedUserId) return null;
    const candidate = (candidatesResponse?.data ?? []).find((c) => c.userId === selectedUserId);
    if (!candidate) return null;
    return {
      id: candidate.userId,
      email: candidate.email,
      name: candidateName(candidate) || undefined,
    };
  }, [isEditMode, existingMember, selectedUserId, candidatesResponse?.data]);

  const canSubmit = isEditMode ? role !== existingMember?.role : !!selectedUserId;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="rounded-2xl sm:max-w-[480px]">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            {isEditMode ? (
              <Pencil className="text-primary h-5 w-5" />
            ) : (
              <UserPlus className="text-primary h-5 w-5" />
            )}
            {isEditMode
              ? t("board.members.editMember")
              : t("board.members.addMember")}
          </DialogTitle>
          <DialogDescription>
            {isEditMode
              ? t("board.members.editMemberDescription")
              : t("board.members.addMemberDescription")}
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4 py-2">
          {/* User selection (add mode) or display (edit mode) */}
          {isEditMode ? (
            selectedUser && (
              <div className="flex items-center gap-3 rounded-lg border p-3">
                <UserAvatar
                  user={{ name: selectedUser.name, email: selectedUser.email }}
                  size="md"
                  showName={false}
                />
                <div>
                  <div className="text-sm font-medium">
                    {selectedUser.name || selectedUser.email}
                  </div>
                  <div className="text-muted-foreground text-xs">
                    {selectedUser.email} 
                  </div>
                </div>
              </div>
            )
          ) : (
            <div className="space-y-2">
              <Label>{t("board.members.selectUser")}</Label>

              {/* Selected user preview */}
              {selectedUser ? (
                <div className="flex items-center justify-between rounded-lg border p-3">
                  <div className="flex items-center gap-3">
                    <UserAvatar
                      user={{ name: selectedUser.name, email: selectedUser.email }}
                      size="md"
                      showName={false}
                    />
                    <div>
                      <div className="text-sm font-medium">
                        {selectedUser.name || selectedUser.email}
                      </div>
                      <div className="text-muted-foreground text-xs">
                        {selectedUser.email}
                      </div>
                    </div>
                  </div>
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    onClick={() => setSelectedUserId(null)}
                  >
                    {t("common.change")}
                  </Button>
                </div>
              ) : (
                <>
                  <div className="relative">
                    <Search className="text-muted-foreground absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2" />
                    <Input
                      value={search}
                      onChange={(e) => setSearch(e.target.value)}
                      placeholder={t("board.members.searchUsers")}
                      className="pl-9"
                    />
                  </div>

                  <div className="max-h-[200px] space-y-1 overflow-auto rounded-lg border p-1">
                    {usersLoading ? (
                      <div className="text-muted-foreground py-4 text-center text-sm">
                        {t("common.loading")}
                      </div>
                    ) : availableUsers.length === 0 ? (
                      <div className="text-muted-foreground py-4 text-center text-sm">
                        {t("board.members.noUsersFound")}
                      </div>
                    ) : (
                      availableUsers.map((user) => {
                        const name = candidateName(user);
                        const canAdd = hasKazanAccess(user);
                        return (
                          <button
                            key={user.userId}
                            type="button"
                            disabled={!canAdd}
                            className={cn(
                              "flex w-full items-center gap-3 rounded-md px-3 py-2 text-left transition-colors",
                              canAdd ? "hover:bg-muted" : "cursor-not-allowed opacity-60"
                            )}
                            onClick={() => setSelectedUserId(user.userId)}
                          >
                            <UserAvatar
                              user={{ name: name || undefined, email: user.email }}
                              size="sm"
                              showName={false}
                            />
                            <div className="min-w-0 flex-1">
                              <div className="truncate text-sm font-medium">
                                {name || user.email}
                              </div>
                              {name && (
                                <div className="text-muted-foreground truncate text-xs">
                                  {user.email}
                                </div>
                              )}
                              {!canAdd && (
                                <div className="text-muted-foreground text-xs">
                                  {t("kazan.memberAccess.noAccessHint")}
                                </div>
                              )}
                            </div>
                          </button>
                        );
                      })
                    )}
                  </div>
                </>
              )}
            </div>
          )}

          {/* Role selection */}
          <div className="space-y-2">
            <Label>{t("board.members.role")}</Label>
            <Select
              value={role}
              onValueChange={(v) => setRole(v as BoardMemberRole)}
            >
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {ROLE_OPTIONS.map(({ value, icon: Icon }) => (
                  <SelectItem key={value} value={value}>
                    <div className="flex items-center gap-2">
                      <Icon className="h-4 w-4" />
                      <span>{t(`board.members.roles.${value}`)}</span>
                    </div>
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        </div>

        <DialogFooter>
          <Button
            type="button"
            variant="ghost"
            onClick={() => onOpenChange(false)}
            disabled={isSaving}
          >
            {t("common.cancel")}
          </Button>
          <Button
            type="button"
            onClick={handleSubmit}
            disabled={!canSubmit || isSaving}
          >
            {isSaving
              ? t("common.saving")
              : isEditMode
                ? t("common.save")
                : t("board.members.addMember")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
