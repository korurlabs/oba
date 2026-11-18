import { useLocalSearchParams } from "expo-router";
import * as React from "react";
import { ActivityIndicator, View } from "react-native";

import {
  candidateName,
  candidateSubtitle,
  memberCandidates,
} from "@/components/boards/settings/boardSettingsModel";
import {
  BOARD_MEMBER_ROLE_ICON,
  BOARD_MEMBER_ROLE_LABEL,
} from "@/components/boards/settings/MembersScreen";
import { BottomSheet } from "@/components/kaizen/BottomSheet";
import { InlineError } from "@/components/kaizen/EmptyState";
import { ListRow, ListSection } from "@/components/kaizen/List";
import { SearchField } from "@/components/kaizen/SearchField";
import { DismissWhen } from "@/components/ticket/DismissWhen";
import { Text } from "@/components/ui/text";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import { useAddBoardMember } from "@/hooks/queries/boardSettings";
import { useBoardMemberCandidates, useBoardMembers } from "@/hooks/queries/boards";
import { useOnlineWriteGuard } from "@/hooks/useOnlineWriteGuard";
import { useTranslation } from "@/i18n";
import { hasKaizenAccess } from "@/lib/kaizenAccess";
import {
  ASSIGNABLE_BOARD_ROLES,
  DEFAULT_MEMBER_ROLE,
  type AssignableBoardRole,
  type BoardMemberCandidate,
} from "@/lib/schemas/boards";

/**
 * Add member, as Kaizen web's dialog: the members of the board's organization
 * who are not on the board yet, searched by name or email; one is picked, then
 * shown with Change to pick another. A member without Kaizen is listed but
 * cannot be picked, and the row says why, because the server refuses to add
 * them. The role is Admin, Member (preselected) or Viewer. One `POST /members`
 * adds the person; a refusal keeps the choice.
 */
export function AddMemberSheet() {
  const { t } = useTranslation();
  const colors = useThemeColors();
  const online = useOnlineWriteGuard();
  const params = useLocalSearchParams<{ boardId?: string | string[] }>();
  const boardId: string =
    (Array.isArray(params.boardId) ? params.boardId[0] : params.boardId) ?? "";
  const users = useBoardMemberCandidates(boardId || undefined);
  const members = useBoardMembers(boardId || undefined);
  const add = useAddBoardMember(boardId);
  const [search, setSearch] = React.useState<string>("");
  const [selectedId, setSelectedId] = React.useState<string | null>(null);
  const [role, setRole] = React.useState<AssignableBoardRole>(DEFAULT_MEMBER_ROLE);
  const [added, setAdded] = React.useState<boolean>(false);

  const candidates: BoardMemberCandidate[] = React.useMemo(
    () => memberCandidates(users.data ?? [], members.data ?? [], search),
    [users.data, members.data, search]
  );
  const selected: BoardMemberCandidate | undefined = selectedId
    ? users.data?.find((user) => user.userId === selectedId)
    : undefined;

  const submit = () => {
    if (!selected || add.isPending || added) return;
    if (!online()) return;
    add.mutate(
      { userId: selected.userId, role },
      {
        onSuccess: () => setAdded(true),
        // The app's error layer toasts the refusal, as the web does; the choice stays.
        onError: (error: Error) => console.warn("[boardSettings] add member failed", error.message),
      }
    );
  };

  const userList = (() => {
    if (users.isPending) {
      return (
        <View className="items-center py-6">
          <ActivityIndicator color={colors.mutedForeground} />
        </View>
      );
    }
    if (users.isError && !users.data) {
      return (
        <InlineError
          message={t("boardMembersUsersError")}
          onRetry={() => void users.refetch()}
          className="mx-4 mt-3"
        />
      );
    }
    if (candidates.length === 0) {
      return (
        <Text variant="footnote" className="px-8 pt-6 text-center text-muted-foreground">
          {t("boardMembersNoUsers")}
        </Text>
      );
    }
    return (
      <ListSection header={t("boardMembersSelectUser")}>
        {candidates.map((user) => {
          const name: string = candidateName(user);
          const canAdd: boolean = hasKaizenAccess(user);
          return (
            <ListRow
              key={user.userId}
              avatar={{ name }}
              title={name}
              subtitle={candidateSubtitle(user, t("memberNoKaizenAccessHint"))}
              wrap={!canAdd}
              onPress={() => setSelectedId(user.userId)}
              accessory="none"
              disabled={add.isPending || !canAdd}
            />
          );
        })}
      </ListSection>
    );
  })();

  return (
    <BottomSheet
      title={t("boardMembersAdd")}
      confirmLabel={t("boardMembersAdd")}
      onConfirm={submit}
      confirmDisabled={!selected}
      confirmLoading={add.isPending || added}
      dirty={!added && selected !== undefined}
      scroll
    >
      <DismissWhen when={added} />
      <Text variant="footnote" className="px-6 pt-2 text-muted-foreground">
        {t("boardMembersAddBody")}
      </Text>
      {selected ? (
        <ListSection header={t("boardMembersSelectUser")}>
          <ListRow
            avatar={{ name: candidateName(selected) }}
            title={candidateName(selected)}
            subtitle={candidateName(selected) !== selected.email ? selected.email : undefined}
          />
          <ListRow
            title={t("boardMembersChange")}
            center
            onPress={() => setSelectedId(null)}
            accessory="none"
            disabled={add.isPending}
          />
        </ListSection>
      ) : (
        <>
          <View className="pt-3">
            <SearchField label={t("boardMembersSearch")} value={search} onChangeText={setSearch} />
          </View>
          {userList}
        </>
      )}
      <ListSection header={t("boardMembersRole")}>
        {ASSIGNABLE_BOARD_ROLES.map((option) => (
          <ListRow
            key={option}
            icon={BOARD_MEMBER_ROLE_ICON[option]}
            title={t(BOARD_MEMBER_ROLE_LABEL[option]!)}
            accessory="radio"
            checked={role === option}
            onPress={() => setRole(option)}
            disabled={add.isPending}
          />
        ))}
      </ListSection>
      <View className="h-10" />
    </BottomSheet>
  );
}
