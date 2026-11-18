import { useLocalSearchParams } from "expo-router";
import * as React from "react";
import { Alert, View } from "react-native";

import { canEditBoard } from "@/components/boards/boardModel";
import type { BoardOptionListKind } from "@/components/boards/boardRoutes";
import {
  boardOptionBody,
  boardOptionDefaults,
  boardOptionNameProblem,
} from "@/components/boards/settings/boardSettingsModel";
import { BottomSheet, useSheet } from "@/components/kazan/BottomSheet";
import { ColorSwatches } from "@/components/kazan/ColorSwatches";
import { EmptyState } from "@/components/kazan/EmptyState";
import { ListRow, ListSection } from "@/components/kazan/List";
import { SkeletonGroup, SkeletonRow } from "@/components/ui/skeleton";
import { Text } from "@/components/ui/text";
import { TextField } from "@/components/ui/text-field";
import { toast } from "@/components/ui/toast";
import {
  useCreateBoardOption,
  useDeleteBoardOption,
  useUpdateBoardOption,
  type BoardOptionTarget,
} from "@/hooks/queries/boardSettings";
import { useBoardLabels, useBoardProjects, useMyBoardRole } from "@/hooks/queries/boards";
import { useOnlineWriteGuard } from "@/hooks/useOnlineWriteGuard";
import { useTranslation, type TranslationKey } from "@/i18n";
import { OPTION_COLORS, type BoardOwnedOption, type PaletteColor } from "@/lib/schemas/boards";

/*
 * The Label and Project sheets, and the texts and delete step that the two
 * lists share with them. Each request, check, toast and confirmation is the
 * Kazan web board settings' for the same action.
 */

/** The texts of one option list; the success toasts are the web's fixed messages. */
export interface BoardOptionTexts {
  add: TranslationKey;
  edit: TranslationKey;
  name: TranslationKey;
  emptyTitle: TranslationKey;
  emptyBody: TranslationKey;
  loadError: TranslationKey;
  gone: TranslationKey;
  delete: TranslationKey;
  deleteConfirm: TranslationKey;
  created: TranslationKey;
  updated: TranslationKey;
  deleted: TranslationKey;
}

export const BOARD_OPTION_TEXTS: Record<BoardOptionListKind, BoardOptionTexts> = {
  labels: {
    add: "boardLabelsAdd",
    edit: "boardLabelsEdit",
    name: "boardLabelsName",
    emptyTitle: "boardLabelsEmptyTitle",
    emptyBody: "boardLabelsEmptyBody",
    loadError: "boardLabelsError",
    gone: "boardLabelsGone",
    delete: "boardLabelsDelete",
    deleteConfirm: "boardLabelsDeleteConfirm",
    created: "boardLabelsCreated",
    updated: "boardLabelsUpdated",
    deleted: "boardLabelsDeleted",
  },
  projects: {
    add: "boardProjectsAdd",
    edit: "boardProjectsEdit",
    name: "boardProjectsName",
    emptyTitle: "boardProjectsEmptyTitle",
    emptyBody: "boardProjectsEmptyBody",
    loadError: "boardProjectsError",
    gone: "boardProjectsGone",
    delete: "boardProjectsDelete",
    deleteConfirm: "boardProjectsDeleteConfirm",
    created: "boardProjectsCreated",
    updated: "boardProjectsUpdated",
    deleted: "boardProjectsDeleted",
  },
};

/** The spoken names of the label and project palette, by the palette's colour name. */
const OPTION_COLOR_NAMES: Record<string, TranslationKey> = {
  Red: "boardOptionColorRed",
  Orange: "boardOptionColorOrange",
  Yellow: "boardOptionColorYellow",
  Green: "boardOptionColorGreen",
  Cyan: "boardOptionColorCyan",
  Blue: "boardOptionColorBlue",
  Violet: "boardOptionColorViolet",
  Pink: "boardOptionColorPink",
  Gray: "boardOptionColorGray",
  Stone: "boardOptionColorStone",
};

/** The label or project list of a board: `GET /labels` or `GET /projects`. */
export function useBoardOptionList(kind: BoardOptionListKind, boardId: string | undefined) {
  const labels = useBoardLabels(kind === "labels" ? boardId : undefined);
  const projects = useBoardProjects(kind === "projects" ? boardId : undefined);
  return kind === "labels" ? labels : projects;
}

/**
 * The web's delete step: asks the web's question with Cancel and Delete, then
 * sends `DELETE` under the board that owns the option and toasts the web's
 * message either way. `onDeleted` runs after a successful delete.
 */
export function useConfirmDeleteBoardOption(kind: BoardOptionListKind) {
  const { t } = useTranslation();
  const remove = useDeleteBoardOption(kind);
  const online = useOnlineWriteGuard();
  const texts: BoardOptionTexts = BOARD_OPTION_TEXTS[kind];
  const { mutate } = remove;

  const confirmDelete = React.useCallback(
    (target: BoardOptionTarget, onDeleted?: () => void) => {
      if (!online()) return;
      Alert.alert(t(texts.delete), t(texts.deleteConfirm), [
        { text: t("kitCancel"), style: "cancel" },
        {
          text: t("boardOptionsDelete"),
          style: "destructive",
          onPress: () =>
            mutate(target, {
              onSuccess: () => {
                toast.success(t(texts.deleted));
                onDeleted?.();
              },
            }),
        },
      ]);
    },
    [online, t, texts, mutate]
  );

  return { confirmDelete, isDeleting: remove.isPending };
}

function firstParam(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value;
}

type BoardOptionFormSheetProps = { kind: BoardOptionListKind };

/**
 * Adds a label or project to this board, or edits one of this board or of a
 * related board: a name and one of the web's ten colours. Board owners and
 * admins write; the sheet is read-only for anyone else. Saving sends the
 * trimmed name and the colour; an edit and a delete go to the owning board.
 */
export function BoardOptionFormSheet({ kind }: BoardOptionFormSheetProps) {
  const { t } = useTranslation();
  const texts: BoardOptionTexts = BOARD_OPTION_TEXTS[kind];
  const params = useLocalSearchParams<{
    boardId?: string | string[];
    labelId?: string | string[];
    projectId?: string | string[];
  }>();
  const boardId: string = firstParam(params.boardId) ?? "";
  const optionId: string | undefined = firstParam(
    kind === "labels" ? params.labelId : params.projectId
  );
  const editing: boolean = !!optionId;

  const list = useBoardOptionList(kind, boardId || undefined);
  // The option stays in the sheet once found, so it does not turn into "no
  // longer exists" while the list refreshes after its own delete.
  const found: BoardOwnedOption | undefined = optionId
    ? list.data?.find((item) => item.id === optionId)
    : undefined;
  const [kept, setKept] = React.useState<BoardOwnedOption | null>(null);
  if (found && found !== kept) setKept(found);
  const option: BoardOwnedOption | null = found ?? kept;

  const role: string | null = useMyBoardRole(boardId || undefined);
  const canManage: boolean = canEditBoard(role);
  const create = useCreateBoardOption(kind, boardId);
  const update = useUpdateBoardOption(kind);
  const { confirmDelete, isDeleting } = useConfirmDeleteBoardOption(kind);
  const online = useOnlineWriteGuard();

  // The fields start from the option once the list has it.
  const defaults = boardOptionDefaults(option);
  const [name, setName] = React.useState<string | null>(null);
  const [color, setColor] = React.useState<string | null>(null);
  const [done, setDone] = React.useState<boolean>(false);
  const nameValue: string = name ?? defaults.name;
  const colorValue: string = color ?? defaults.color;
  const changed: boolean = nameValue !== defaults.name || colorValue !== defaults.color;
  const ready: boolean = !editing || !!option;
  const saving: boolean = create.isPending || update.isPending;

  const submit = () => {
    if (!canManage || !ready || saving || done) return;
    if (boardOptionNameProblem(nameValue)) return;
    if (!online()) return;
    const body = boardOptionBody(nameValue, colorValue);
    if (option) {
      update.mutate(
        { ownerBoardId: option.boardId, optionId: option.id, body },
        {
          onSuccess: () => {
            toast.success(t(texts.updated));
            setDone(true);
          },
        }
      );
      return;
    }
    create.mutate(body, {
      onSuccess: () => {
        toast.success(t(texts.created));
        setDone(true);
      },
    });
  };

  return (
    <BottomSheet
      title={t(editing ? texts.edit : texts.add)}
      confirmLabel={t("boardSettingsSave")}
      onConfirm={submit}
      confirmDisabled={!canManage || !ready || !!boardOptionNameProblem(nameValue) || isDeleting}
      confirmLoading={saving}
      dirty={changed && !done}
      detent="medium"
      scroll
    >
      <BoardOptionFormContent
        kind={kind}
        state={
          !editing || option
            ? "ready"
            : list.isPending
              ? "loading"
              : list.isError
                ? "error"
                : "gone"
        }
        onRetry={() => void list.refetch()}
        readOnly={!canManage}
        name={nameValue}
        color={colorValue}
        disabled={saving || isDeleting}
        done={done}
        onChangeName={setName}
        onChangeColor={setColor}
        onSubmit={submit}
        onDelete={
          option && canManage
            ? () =>
                confirmDelete({ ownerBoardId: option.boardId, optionId: option.id }, () =>
                  setDone(true)
                )
            : undefined
        }
        deleting={isDeleting}
      />
    </BottomSheet>
  );
}

type BoardOptionFormContentProps = {
  kind: BoardOptionListKind;
  /** `gone`: the list loaded without the edited option. */
  state: "ready" | "loading" | "error" | "gone";
  onRetry: () => void;
  readOnly: boolean;
  name: string;
  color: string;
  disabled: boolean;
  /** The option is saved or deleted: the sheet closes. */
  done: boolean;
  onChangeName: (text: string) => void;
  onChangeColor: (color: string) => void;
  onSubmit: () => void;
  /** Present for an existing option that the reader manages. */
  onDelete?: () => void;
  deleting: boolean;
};

function BoardOptionFormContent({
  kind,
  state,
  onRetry,
  readOnly,
  name,
  color,
  disabled,
  done,
  onChangeName,
  onChangeColor,
  onSubmit,
  onDelete,
  deleting,
}: BoardOptionFormContentProps) {
  const { t } = useTranslation();
  const { dismiss } = useSheet();
  const texts: BoardOptionTexts = BOARD_OPTION_TEXTS[kind];

  React.useEffect(() => {
    if (done) dismiss();
  }, [done, dismiss]);

  const labelOf = React.useCallback(
    (paletteColor: PaletteColor): string => {
      const key: TranslationKey | undefined = OPTION_COLOR_NAMES[paletteColor.name];
      return key ? t(key) : paletteColor.name;
    },
    [t]
  );

  if (state === "loading") {
    return (
      <View className="pt-6">
        <SkeletonGroup>
          <SkeletonRow />
          <SkeletonRow />
        </SkeletonGroup>
      </View>
    );
  }
  if (state === "error") {
    return (
      <EmptyState variant="error" title={t(texts.loadError)} onAction={onRetry} className="mt-8" />
    );
  }
  if (state === "gone") {
    return <EmptyState variant="error" title={t(texts.gone)} className="mt-8" />;
  }
  return (
    <View className="pb-6">
      <View className="gap-4 px-4 pt-4">
        <TextField
          label={t(texts.name)}
          placeholder={t(texts.name)}
          value={name}
          onChangeText={onChangeName}
          editable={!readOnly && !disabled}
          autoFocus={!readOnly && !name}
          returnKeyType="done"
          onSubmitEditing={onSubmit}
        />
        <View className="gap-2">
          <Text variant="footnote" className="text-muted-foreground">
            {t("boardOptionsColor")}
          </Text>
          <ColorSwatches
            colors={OPTION_COLORS}
            value={color}
            onChange={onChangeColor}
            labelOf={labelOf}
            accessibilityLabel={t("boardOptionsColor")}
            disabled={readOnly || disabled}
          />
        </View>
        {readOnly ? (
          <Text variant="footnote" className="text-muted-foreground">
            {t("boardSettingsReadOnly")}
          </Text>
        ) : null}
      </View>
      {onDelete ? (
        <ListSection>
          <ListRow
            title={t(texts.delete)}
            destructive
            center
            disabled={deleting || disabled}
            onPress={onDelete}
          />
        </ListSection>
      ) : null}
    </View>
  );
}
