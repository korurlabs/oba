import { useLocalSearchParams, useRouter } from "expo-router";
import * as React from "react";
import { View } from "react-native";

import {
  boardNameProblem,
  boardPrefixProblem,
  newBoardRecheck,
  normalizePrefix,
  parentOptionLabel,
  type BoardFieldProblem,
  type BoardNameProblem,
  type NewBoardRecheck,
  type NewBoardValues,
} from "@/components/boards/boardForms";
import { flattenBoardTree, suggestPrefix, type BoardRow } from "@/components/boards/boardModel";
import { boardHref } from "@/components/boards/boardRoutes";
import { PickerRow } from "@/components/boards/PickerRow";
import { BottomSheet } from "@/components/kaizen/BottomSheet";
import { ListSection } from "@/components/kaizen/List";
import { DismissWhen } from "@/components/ticket/DismissWhen";
import { Text } from "@/components/ui/text";
import { TextField } from "@/components/ui/text-field";
import { toast } from "@/components/ui/toast";
import { useBoardTree, useCreateBoard } from "@/hooks/queries/boards";
import { useOnlineWriteGuard } from "@/hooks/useOnlineWriteGuard";
import { useTranslation, type TranslationKey } from "@/i18n";
import { isApiError, isValidationError } from "@/lib/api";
import {
  BOARD_NAME_MAX,
  BOARD_PREFIX_MAX,
  BOARD_PREFIX_TAKEN_CODE,
  DEFAULT_BOARD_COLOR,
  type BoardDetail,
} from "@/lib/schemas/boards";

const NAME_PROBLEM: Record<BoardNameProblem, TranslationKey> = {
  required: "newBoardNameRequired",
  "too-long": "newBoardNameTooLong",
};

const PREFIX_PROBLEM: Record<BoardFieldProblem, TranslationKey> = {
  required: "newBoardKeyRequired",
  "too-long": "newBoardKeyTooLong",
  pattern: "newBoardKeyPattern",
};

type FieldErrors = { name: string | null; prefix: string | null };

const NO_ERRORS: FieldErrors = { name: null, prefix: null };

/**
 * The New board sheet: a name, a key that prefixes every ticket key, and an
 * optional parent board, checked as the web app's create-board form checks
 * them. The key follows the name until the user types one. A created board
 * opens in place of the sheet.
 *
 * Opened from a board's Child boards with `parentBoardId`, it creates a child
 * of that board, as the web's Create child board: the parent is fixed, and the
 * sheet closes back to the Child boards list, where the new board shows.
 */
export default function NewBoardSheet() {
  const { t } = useTranslation();
  const router = useRouter();
  const params = useLocalSearchParams<{ parentBoardId?: string | string[] }>();
  const fixedParentId: string | null =
    (Array.isArray(params.parentBoardId) ? params.parentBoardId[0] : params.parentBoardId) || null;
  const tree = useBoardTree();
  const create = useCreateBoard();
  const online = useOnlineWriteGuard();
  const rows: BoardRow[] = React.useMemo(() => flattenBoardTree(tree.data ?? []), [tree.data]);

  const [name, setName] = React.useState<string>("");
  const [prefix, setPrefix] = React.useState<string>("");
  const [prefixTouched, setPrefixTouched] = React.useState<boolean>(false);
  const [parentId, setParentId] = React.useState<string | null>(fixedParentId);
  const [errors, setErrors] = React.useState<FieldErrors>(NO_ERRORS);
  const [submitted, setSubmitted] = React.useState<boolean>(false);
  const [created, setCreated] = React.useState<BoardDetail | null>(null);

  const dirty: boolean = created === null && (name.trim() !== "" || prefix !== "");

  // Replace the sheet only once it is no longer dirty, so leaving does not ask to discard.
  React.useEffect(() => {
    if (created && !fixedParentId) router.replace(boardHref(created.id));
  }, [created, fixedParentId, router]);

  const nameError = (value: string): string | null => {
    const problem: BoardNameProblem | null = boardNameProblem(value);
    return problem ? t(NAME_PROBLEM[problem], { max: BOARD_NAME_MAX }) : null;
  };

  const prefixError = (value: string): string | null => {
    const problem: BoardFieldProblem | null = boardPrefixProblem(value);
    return problem ? t(PREFIX_PROBLEM[problem]) : null;
  };

  // Both fields go through here, because typing a name can rewrite the key.
  const edit = (next: NewBoardValues, typedKey: boolean) => {
    const recheck: NewBoardRecheck = newBoardRecheck({ name, prefix }, next, {
      submitted,
      typedKey,
    });
    setName(next.name);
    setPrefix(next.prefix);
    setErrors((current) => ({
      name: recheck.name ? nameError(next.name) : current.name,
      prefix: recheck.prefix ? prefixError(next.prefix) : current.prefix,
    }));
  };

  const changeName = (text: string) => {
    edit({ name: text, prefix: prefixTouched ? prefix : suggestPrefix(text) }, false);
  };

  const changePrefix = (text: string) => {
    setPrefixTouched(true);
    edit({ name, prefix: normalizePrefix(text) }, true);
  };

  const submit = () => {
    // The keyboard's Done key submits too, so a second press while the first
    // create is in flight must not post another board.
    if (create.isPending || created !== null) return;
    setSubmitted(true);
    const checked: FieldErrors = { name: nameError(name), prefix: prefixError(prefix) };
    if (checked.name || checked.prefix) {
      setErrors(checked);
      return;
    }
    if (!online()) return;
    create.mutate(
      {
        name: name.trim(),
        prefix,
        color: DEFAULT_BOARD_COLOR,
        ...(parentId ? { parentBoardId: parentId } : {}),
      },
      {
        onSuccess: (board: BoardDetail) => {
          toast.success(t("newBoardCreated", { name: board.name }));
          setCreated(board);
        },
        onError: (error: Error) => {
          // A refused field shows under it; other failures are toasted by the app's error layer.
          if (isApiError(error, BOARD_PREFIX_TAKEN_CODE)) {
            setErrors({ name: null, prefix: t("newBoardKeyTaken") });
          } else if (isValidationError(error)) {
            const fields: Record<string, string> = error.fieldErrors;
            setErrors({ name: fields.name ?? null, prefix: fields.prefix ?? null });
          } else {
            console.warn("[boards] create failed", error.message);
          }
        },
      }
    );
  };

  return (
    <BottomSheet
      title={t(fixedParentId ? "newBoardChildTitle" : "newBoardTitle")}
      confirmLabel={t("newBoardCreate")}
      onConfirm={submit}
      confirmLoading={create.isPending || created !== null}
      dirty={dirty}
      scroll
    >
      <DismissWhen when={created !== null && fixedParentId !== null} />
      {fixedParentId ? (
        <Text variant="footnote" className="px-6 pt-2 text-muted-foreground">
          {t("newBoardChildBody")}
        </Text>
      ) : null}
      <View className="gap-4 px-4 pt-4">
        <TextField
          label={t("newBoardName")}
          value={name}
          onChangeText={changeName}
          placeholder={t("newBoardNamePlaceholder")}
          error={errors.name}
          maxLength={BOARD_NAME_MAX}
          autoFocus
          returnKeyType="next"
        />
        <TextField
          label={t("newBoardKey")}
          value={prefix}
          onChangeText={changePrefix}
          placeholder={t("newBoardKeyPlaceholder")}
          helper={t("newBoardKeyHelper")}
          error={errors.prefix}
          maxLength={BOARD_PREFIX_MAX}
          autoCapitalize="characters"
          autoCorrect={false}
          returnKeyType="done"
          onSubmitEditing={submit}
        />
      </View>
      <ListSection>
        <PickerRow
          title={t("newBoardParent")}
          value={parentId}
          options={rows.map((row) => ({ id: row.id, label: parentOptionLabel(row) }))}
          noneLabel={t("newBoardNoParent")}
          onChange={setParentId}
          disabled={fixedParentId !== null || !tree.isSuccess || rows.length === 0}
        />
      </ListSection>
    </BottomSheet>
  );
}
