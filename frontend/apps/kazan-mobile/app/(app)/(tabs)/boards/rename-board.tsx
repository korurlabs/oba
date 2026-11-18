import { useLocalSearchParams } from "expo-router";
import * as React from "react";
import { View } from "react-native";

import { boardNameProblem, type BoardNameProblem } from "@/components/boards/boardForms";
import { canEditBoard, flattenBoardTree, type BoardRow } from "@/components/boards/boardModel";
import { BottomSheet, useSheet } from "@/components/kazan/BottomSheet";
import { EmptyState } from "@/components/kazan/EmptyState";
import { Text } from "@/components/ui/text";
import { TextField } from "@/components/ui/text-field";
import { toast } from "@/components/ui/toast";
import {
  useBoardMembers,
  useBoardTree,
  useMyBoardRole,
  useUpdateBoard,
} from "@/hooks/queries/boards";
import { useOnlineWriteGuard } from "@/hooks/useOnlineWriteGuard";
import { useTranslation, type TranslationKey } from "@/i18n";
import { isValidationError } from "@/lib/api";
import { BOARD_NAME_MAX } from "@/lib/schemas/boards";

const NAME_PROBLEM: Record<BoardNameProblem, TranslationKey> = {
  required: "newBoardNameRequired",
  "too-long": "newBoardNameTooLong",
};

/**
 * Renames a board from its menu on the boards list, with the same check and
 * the same `PUT /kazan/boards/{boardId}` as Board settings. Board admins
 * and owners rename; anyone else sees why the field is read-only.
 */
export default function RenameBoardSheet() {
  const { t } = useTranslation();
  const params = useLocalSearchParams<{ boardId?: string | string[] }>();
  const boardId: string =
    (Array.isArray(params.boardId) ? params.boardId[0] : params.boardId) ?? "";
  const tree = useBoardTree();
  const board: BoardRow | undefined = React.useMemo(
    () => flattenBoardTree(tree.data ?? []).find((row) => row.id === boardId),
    [tree.data, boardId]
  );
  const members = useBoardMembers(boardId || undefined);
  const role: string | null = useMyBoardRole(boardId || undefined);
  const canEdit: boolean = canEditBoard(role);
  const update = useUpdateBoard(boardId);
  const online = useOnlineWriteGuard();

  const [name, setName] = React.useState<string | null>(null);
  const [nameError, setNameError] = React.useState<string | null>(null);
  const [saved, setSaved] = React.useState<boolean>(false);
  // The field starts from the board's name once the list has it.
  const value: string = name ?? board?.name ?? "";
  const changed: boolean = !!board && value.trim() !== board.name;

  const submit = () => {
    if (!board || !canEdit || update.isPending || saved) return;
    if (!changed) {
      setSaved(true);
      return;
    }
    const problem: BoardNameProblem | null = boardNameProblem(value);
    if (problem) {
      setNameError(t(NAME_PROBLEM[problem], { max: BOARD_NAME_MAX }));
      return;
    }
    setNameError(null);
    if (!online()) return;
    update.mutate(
      { name: value.trim() },
      {
        onSuccess: () => {
          toast.success(t("boardSettingsSaved"));
          setSaved(true);
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

  return (
    <BottomSheet
      title={t("boardsRenameTitle")}
      confirmLabel={t("boardSettingsSave")}
      onConfirm={submit}
      confirmDisabled={!board || !canEdit}
      confirmLoading={update.isPending}
      dirty={changed && !saved}
      detent="medium"
    >
      <RenameBoardContent
        found={!!board || tree.isPending}
        readOnly={members.isSuccess && !canEdit}
        name={value}
        error={nameError}
        saved={saved}
        onChangeName={(text: string) => {
          setName(text);
          setNameError(null);
        }}
        onSubmit={submit}
      />
    </BottomSheet>
  );
}

type RenameBoardContentProps = {
  /** False once the board list is loaded without this board. */
  found: boolean;
  readOnly: boolean;
  name: string;
  error: string | null;
  /** The name is saved: the sheet closes. */
  saved: boolean;
  onChangeName: (text: string) => void;
  onSubmit: () => void;
};

function RenameBoardContent({
  found,
  readOnly,
  name,
  error,
  saved,
  onChangeName,
  onSubmit,
}: RenameBoardContentProps) {
  const { t } = useTranslation();
  const { dismiss } = useSheet();

  React.useEffect(() => {
    if (saved) dismiss();
  }, [saved, dismiss]);

  if (!found) {
    return <EmptyState variant="error" title={t("boardNoAccess")} className="mt-8" />;
  }
  return (
    <View className="gap-2 px-4 pb-6 pt-4">
      <TextField
        label={t("boardSettingsName")}
        value={name}
        onChangeText={onChangeName}
        error={error}
        maxLength={BOARD_NAME_MAX}
        editable={!readOnly}
        autoFocus={!readOnly}
        selectTextOnFocus
        returnKeyType="done"
        onSubmitEditing={onSubmit}
      />
      {readOnly ? (
        <Text variant="footnote" className="text-muted-foreground">
          {t("boardSettingsReadOnly")}
        </Text>
      ) : null}
    </View>
  );
}
