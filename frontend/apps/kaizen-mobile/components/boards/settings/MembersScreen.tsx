import { Stack, useLocalSearchParams, useRouter } from "expo-router";
import { Eye, Pencil, Shield, UserMinus, UserPlus, type LucideIcon } from "lucide-react-native";
import * as React from "react";
import { RefreshControl, ScrollView, View } from "react-native";

import { canEditBoard, personName } from "@/components/boards/boardModel";
import { addBoardMemberHref } from "@/components/boards/boardRoutes";
import { memberActions, type MemberActions } from "@/components/boards/settings/boardSettingsModel";
import { ActionSheet, type ActionSheetAction } from "@/components/kaizen/ActionSheet";
import { EmptyState } from "@/components/kaizen/EmptyState";
import { ListRow, ListSection } from "@/components/kaizen/List";
import { IconButton } from "@/components/ui/icon-button";
import { SkeletonGroup, SkeletonRow } from "@/components/ui/skeleton";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import { useRemoveBoardMember, useUpdateBoardMemberRole } from "@/hooks/queries/boardSettings";
import { useBoardMembers, useMyBoardRole } from "@/hooks/queries/boards";
import { useOnlineWriteGuard } from "@/hooks/useOnlineWriteGuard";
import { useTranslation, type TranslationKey } from "@/i18n";
import type { AssignableBoardRole, BoardMember } from "@/lib/schemas/boards";

/** The spoken name of each board role, the web's `board.members.roles` texts. */
export const BOARD_MEMBER_ROLE_LABEL: Readonly<Record<string, TranslationKey>> = {
  OWNER: "ticketBoardRoleOwner",
  ADMIN: "ticketBoardRoleAdmin",
  MEMBER: "ticketBoardRoleMember",
  VIEWER: "ticketBoardRoleViewer",
};

/** The icon of each role a member can be given, as the web's role select shows it. */
export const BOARD_MEMBER_ROLE_ICON: Readonly<Record<AssignableBoardRole, LucideIcon>> = {
  ADMIN: Shield,
  MEMBER: Pencil,
  VIEWER: Eye,
};

/**
 * The members of a board, as Kaizen web's Board settings lists them: avatar,
 * name, email and role, in the server's order. Board owners and admins add a
 * member with `+` and, on any row but the owner's, give another role or
 * remove the member at once, without a confirmation, as the web does.
 */
export function MembersScreen() {
  const { t } = useTranslation();
  const colors = useThemeColors();
  const router = useRouter();
  const online = useOnlineWriteGuard();
  const params = useLocalSearchParams<{ boardId?: string | string[] }>();
  const boardId: string =
    (Array.isArray(params.boardId) ? params.boardId[0] : params.boardId) ?? "";
  const members = useBoardMembers(boardId || undefined);
  const canManage: boolean = canEditBoard(useMyBoardRole(boardId || undefined));
  const updateRole = useUpdateBoardMemberRole(boardId);
  const remove = useRemoveBoardMember(boardId);
  const [menuMember, setMenuMember] = React.useState<BoardMember | null>(null);
  const [refreshing, setRefreshing] = React.useState<boolean>(false);
  const busy: boolean = updateRole.isPending || remove.isPending;

  const changeRole = React.useCallback(
    (member: BoardMember, role: AssignableBoardRole) => {
      // The web saves only a different role: its Save stays disabled on the current one.
      if (role === member.role) return;
      if (!online()) return;
      updateRole.mutate(
        { userId: member.userId, role },
        {
          // The app's error layer toasts the server's message, as the web does.
          onError: (error: Error) =>
            console.warn("[boardSettings] member role change failed", error.message),
        }
      );
    },
    [online, updateRole]
  );

  const removeMember = React.useCallback(
    (member: BoardMember) => {
      if (!online()) return;
      remove.mutate(
        { userId: member.userId },
        {
          // The app's error layer toasts the server's message, as the web does.
          onError: (error: Error) =>
            console.warn("[boardSettings] member remove failed", error.message),
        }
      );
    },
    [online, remove]
  );

  const refresh = React.useCallback(() => {
    setRefreshing(true);
    members
      .refetch()
      .catch((error: unknown) => {
        // A failed read shows in the query state and the app's error layer; this only records the pull.
        console.warn(
          "[boardSettings] members refresh failed",
          error instanceof Error ? error.message : error
        );
      })
      .finally(() => setRefreshing(false));
  }, [members]);

  const menu: MemberActions | null = menuMember ? memberActions(menuMember, canManage) : null;
  const menuItems: ActionSheetAction[] =
    menuMember && menu
      ? [
          ...menu.roles.map(({ role, selected }) => ({
            label: t(BOARD_MEMBER_ROLE_LABEL[role]!),
            icon: BOARD_MEMBER_ROLE_ICON[role],
            selected,
            onPress: () => changeRole(menuMember, role),
          })),
          {
            label: t("boardMembersRemove"),
            icon: UserMinus,
            destructive: true,
            onPress: () => removeMember(menuMember),
          },
        ]
      : [];

  const header = (
    <Stack.Screen
      options={{
        headerRight: canManage
          ? () => (
              <IconButton
                icon={UserPlus}
                label={t("boardMembersAdd")}
                onPress={() => router.push(addBoardMemberHref(boardId))}
              />
            )
          : undefined,
      }}
    />
  );

  const sheet = (
    <ActionSheet
      visible={menuMember !== null}
      onClose={() => setMenuMember(null)}
      title={menuMember ? personName(menuMember) : undefined}
      actions={menuItems}
    />
  );

  if (members.isPending) {
    return (
      <View className="flex-1 bg-screen pt-6">
        {header}
        <SkeletonGroup>
          <SkeletonRow />
          <SkeletonRow />
          <SkeletonRow />
        </SkeletonGroup>
      </View>
    );
  }

  if (members.isError && !members.data) {
    return (
      <View className="flex-1 bg-screen">
        {header}
        <EmptyState
          variant="error"
          title={t("boardMembersError")}
          onAction={() => void members.refetch()}
          className="mt-16"
        />
      </View>
    );
  }

  return (
    <>
      {header}
      <ScrollView
        className="flex-1 bg-screen"
        contentInsetAdjustmentBehavior="automatic"
        contentContainerClassName="flex-grow pb-10"
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={refresh}
            tintColor={colors.mutedForeground}
          />
        }
      >
        <ListSection>
          {(members.data ?? []).map((member) => {
            const name: string = personName(member);
            const roleKey: TranslationKey | undefined = BOARD_MEMBER_ROLE_LABEL[member.role];
            const actionable: boolean = memberActions(member, canManage) !== null;
            return (
              <ListRow
                key={member.userId}
                avatar={{ name }}
                title={name}
                // The title already reads the email when the profile has no name.
                subtitle={member.email && member.email !== name ? member.email : undefined}
                value={roleKey ? t(roleKey) : member.role}
                onPress={actionable ? () => setMenuMember(member) : undefined}
                disabled={actionable && busy}
              />
            );
          })}
        </ListSection>
      </ScrollView>
      {sheet}
    </>
  );
}
