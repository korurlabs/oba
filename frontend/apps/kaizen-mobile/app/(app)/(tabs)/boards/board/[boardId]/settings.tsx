import { usePreventRemove } from "@react-navigation/native";
import { useQueryClient } from "@tanstack/react-query";
import { Stack, useLocalSearchParams, useNavigation, useRouter } from "expo-router";
import { Flag, Folder, GitBranch, Lock, Siren, Tag, Trash2, Users } from "lucide-react-native";
import * as React from "react";
import { ScrollView, View } from "react-native";

import {
  boardNameProblem,
  parentOptionLabel,
  type BoardNameProblem,
} from "@/components/boards/boardForms";
import {
  canDeleteBoard,
  canEditBoard,
  flattenBoardTree,
  parentCandidates,
  type BoardRow,
} from "@/components/boards/boardModel";
import { useBoardLinks } from "@/components/boards/boardNavigation";
import {
  boardMembersHref,
  boardMilestonesHref,
  boardOptionsHref,
  childBoardsHref,
  deleteBoardHref,
} from "@/components/boards/boardRoutes";
import { PickerRow } from "@/components/boards/PickerRow";
import { confirmDiscardChanges } from "@/components/kaizen/ActionSheet";
import { EmptyState } from "@/components/kaizen/EmptyState";
import { ListRow, ListSection } from "@/components/kaizen/List";
import { Button } from "@/components/ui/button";
import { SkeletonGroup, SkeletonRow } from "@/components/ui/skeleton";
import { TextField } from "@/components/ui/text-field";
import { toast } from "@/components/ui/toast";
import {
  boardKeys,
  useBoard,
  useBoardLabels,
  useBoardMembers,
  useBoardMilestones,
  useBoardProjects,
  useBoardTree,
  useMyBoardRoleState,
  useUpdateBoard,
} from "@/hooks/queries/boards";
import { useOnlineWriteGuard } from "@/hooks/useOnlineWriteGuard";
import { useTranslation, type TranslationKey } from "@/i18n";
import { isValidationError, toApiError } from "@/lib/api";
import { useActiveOrganizationId } from "@/lib/organizationStore";
import { BOARD_NAME_MAX, type BoardListItem } from "@/lib/schemas/boards";

const NAME_PROBLEM: Record<BoardNameProblem, TranslationKey> = {
  required: "newBoardNameRequired",
  "too-long": "newBoardNameTooLong",
};

/**
 * Board settings: rename the board, move it under another board, and open the
 * board's labels, milestones, projects, child boards and members. Only a board
 * owner or admin reaches it, as Kaizen web shows its settings dialog only to
 * them; any other reader who lands here by a link gets the no-access state.
 * Alert routes belong to the organization and open in Kaizen web. The owner
 * also deletes the board, in the Delete board sheet.
 */
export default function BoardSettingsScreen() {
  const { t } = useTranslation();
  const router = useRouter();
  const params = useLocalSearchParams<{ boardId?: string | string[] }>();
  const boardId: string | undefined = Array.isArray(params.boardId)
    ? params.boardId[0]
    : params.boardId;
  const board = useBoard(boardId);
  const access = useMyBoardRoleState(boardId);

  const noAccessState = (
    <View className="flex-1 bg-screen">
      <EmptyState
        variant="empty"
        icon={Lock}
        title={t("boardNoAccess")}
        actionLabel={t("boardBack")}
        onAction={() => router.back()}
        className="mt-16"
      />
    </View>
  );
  const failure: Error | null = board.error ?? access.error;
  if (failure) {
    const kind: string = toApiError(failure).kind;
    if (kind === "forbidden" || kind === "not-found") return noAccessState;
    return (
      <View className="flex-1 bg-screen">
        <EmptyState
          variant="error"
          title={t("boardError")}
          onAction={() => {
            if (board.isError) void board.refetch();
            access.refetch();
          }}
          className="mt-16"
        />
      </View>
    );
  }
  if (!board.data || !boardId || access.isPending) {
    return (
      <View className="flex-1 bg-screen pt-6">
        <SkeletonGroup>
          <SkeletonRow />
          <SkeletonRow />
          <SkeletonRow />
        </SkeletonGroup>
      </View>
    );
  }
  const role: string | null = access.role;
  if (role === null || !canEditBoard(role)) return noAccessState;
  return <SettingsContent boardId={boardId} board={board.data} role={role} />;
}

type SettingsContentProps = {
  boardId: string;
  board: BoardListItem;
  /** The reader's board role, OWNER or ADMIN. */
  role: string;
};

function SettingsContent({ boardId, board, role }: SettingsContentProps) {
  const { t } = useTranslation();
  const router = useRouter();
  const navigation = useNavigation();
  const queryClient = useQueryClient();
  const orgId: string | null = useActiveOrganizationId();
  const { openWebAlertRoutes } = useBoardLinks();
  const canDelete: boolean = canDeleteBoard(role);

  const tree = useBoardTree();
  const labels = useBoardLabels(boardId);
  const milestones = useBoardMilestones(boardId);
  const projects = useBoardProjects(boardId);
  const members = useBoardMembers(boardId);
  const update = useUpdateBoard(boardId);
  const online = useOnlineWriteGuard();

  const rows: BoardRow[] = React.useMemo(() => flattenBoardTree(tree.data ?? []), [tree.data]);
  const self: BoardRow | undefined = rows.find((row) => row.id === boardId);
  const candidates: BoardRow[] = React.useMemo(
    () => parentCandidates(rows, boardId),
    [rows, boardId]
  );

  const [name, setName] = React.useState<string>(board.name);
  const [nameError, setNameError] = React.useState<string | null>(null);
  const dirty: boolean = name.trim() !== board.name;
  const leaving = React.useRef<boolean>(false);

  usePreventRemove(dirty, ({ data }) => {
    // A deleted board drops its reads before its screens close: the unsaved
    // name has nothing left to be saved to, so leave without asking.
    const deleted: boolean =
      orgId !== null && queryClient.getQueryState(boardKeys.detail(orgId, boardId)) === undefined;
    if (leaving.current || deleted) {
      navigation.dispatch(data.action);
      return;
    }
    confirmDiscardChanges(() => {
      leaving.current = true;
      navigation.dispatch(data.action);
    });
  });

  const saveName = () => {
    const problem: BoardNameProblem | null = boardNameProblem(name);
    if (problem) {
      setNameError(t(NAME_PROBLEM[problem], { max: BOARD_NAME_MAX }));
      return;
    }
    setNameError(null);
    if (!online()) return;
    update.mutate(
      { name: name.trim() },
      {
        onSuccess: (saved) => {
          setName(saved.name);
          toast.success(t("boardSettingsSaved"));
        },
        onError: (error: Error) => {
          // A refused name shows under the field; other failures are toasted by the app's error layer.
          if (isValidationError(error)) {
            setNameError(error.fieldErrors.name ?? error.message);
          } else {
            console.warn("[boards] rename failed", error.message);
          }
        },
      }
    );
  };

  const changeParent = (parentId: string | null) => {
    if (parentId === (self?.parentId ?? null)) return;
    if (!online()) return;
    update.mutate(parentId ? { parentBoardId: parentId } : { clearParent: true }, {
      onSuccess: () => toast.success(t("boardSettingsSaved")),
      onError: (error: Error) => {
        // The app's error layer toasts the refusal (such as a parent that would close a loop).
        console.warn("[boards] parent change failed", error.message);
      },
    });
  };

  const count = (list: readonly unknown[] | undefined): string | undefined =>
    list ? String(list.length) : undefined;

  return (
    <>
      <Stack.Screen
        options={{
          headerRight: () => (
            <Button
              variant="ghost"
              size="sm"
              disabled={!dirty}
              loading={update.isPending && dirty}
              onPress={saveName}
            >
              {t("boardSettingsSave")}
            </Button>
          ),
        }}
      />
      <ScrollView
        className="flex-1 bg-screen"
        contentInsetAdjustmentBehavior="automatic"
        keyboardShouldPersistTaps="handled"
        contentContainerClassName="pb-10"
      >
        <View className="px-4 pt-6">
          <TextField
            label={t("boardSettingsName")}
            value={name}
            onChangeText={(text) => {
              setName(text);
              setNameError(null);
            }}
            error={nameError}
            maxLength={BOARD_NAME_MAX}
            returnKeyType="done"
            onSubmitEditing={dirty ? saveName : undefined}
          />
        </View>
        <ListSection header={t("boardSettingsGeneral")}>
          <ListRow title={t("boardSettingsKey")} value={board.prefix ?? ""} accessory="none" />
          <PickerRow
            title={t("boardSettingsParent")}
            value={self?.parentId ?? null}
            options={candidates.map((row) => ({ id: row.id, label: parentOptionLabel(row) }))}
            noneLabel={t("boardSettingsNoParent")}
            onChange={changeParent}
            disabled={update.isPending || !tree.isSuccess}
          />
        </ListSection>

        <ListSection header={t("boardSettingsOrganize")}>
          <ListRow
            title={t("boardSettingsLabels")}
            icon={Tag}
            value={count(labels.data)}
            onPress={() => router.push(boardOptionsHref(boardId, "labels"))}
          />
          <ListRow
            title={t("boardSettingsMilestones")}
            icon={Flag}
            iconTone="bg-warning"
            value={count(milestones.data)}
            onPress={() => router.push(boardMilestonesHref(boardId))}
          />
          <ListRow
            title={t("boardSettingsProjects")}
            icon={Folder}
            iconTone="bg-success"
            value={count(projects.data)}
            onPress={() => router.push(boardOptionsHref(boardId, "projects"))}
          />
          <ListRow
            title={t("boardSettingsChildBoards")}
            icon={GitBranch}
            value={self ? String(self.childCount) : undefined}
            onPress={() => router.push(childBoardsHref(boardId))}
          />
        </ListSection>

        <ListSection header={t("boardSettingsPeople")} footer={t("boardSettingsAlertFooter")}>
          <ListRow
            title={t("boardSettingsMembers")}
            icon={Users}
            value={count(members.data)}
            onPress={() => router.push(boardMembersHref(boardId))}
          />
          <ListRow
            title={t("boardSettingsAlertRoutes")}
            icon={Siren}
            iconTone="bg-destructive"
            accessory="external"
            onPress={openWebAlertRoutes}
          />
        </ListSection>

        {canDelete ? (
          <ListSection
            header={t("boardSettingsDangerZone")}
            footer={t("boardSettingsDeleteWarning")}
          >
            <ListRow
              title={t("boardSettingsDelete")}
              icon={Trash2}
              iconTone="bg-destructive"
              destructive
              onPress={() => router.push(deleteBoardHref(boardId))}
            />
          </ListSection>
        ) : null}
      </ScrollView>
    </>
  );
}
