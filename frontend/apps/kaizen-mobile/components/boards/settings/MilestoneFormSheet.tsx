import { useLocalSearchParams } from "expo-router";
import * as React from "react";
import { View } from "react-native";

import {
  isMilestoneFormValid,
  milestoneCreateBody,
  milestoneFormDefaults,
  milestoneFormProblems,
  milestoneUpdateBody,
  type MilestoneFormProblems,
  type MilestoneFormValues,
} from "@/components/boards/settings/boardSettingsModel";
import {
  useMilestoneActions,
  type MilestoneActionTarget,
} from "@/components/boards/settings/useMilestoneActions";
import { BottomSheet } from "@/components/kaizen/BottomSheet";
import { ColorSwatches } from "@/components/kaizen/ColorSwatches";
import { EmptyState } from "@/components/kaizen/EmptyState";
import { ListRow, ListSection } from "@/components/kaizen/List";
import { DismissWhen } from "@/components/ticket/DismissWhen";
import { useDueDatePicker } from "@/components/ticket/useDueDatePicker";
import { SkeletonGroup, SkeletonRow } from "@/components/ui/skeleton";
import { Text } from "@/components/ui/text";
import { TextField } from "@/components/ui/text-field";
import { toast } from "@/components/ui/toast";
import {
  useCreateMilestone,
  useMilestone,
  useUpdateMilestone,
} from "@/hooks/queries/boardSettings";
import { useBoard } from "@/hooks/queries/boards";
import { useDates } from "@/hooks/useDates";
import { useOnlineWriteGuard } from "@/hooks/useOnlineWriteGuard";
import { useTranslation, type TranslationKey } from "@/i18n";
import { isApiError, isValidationError } from "@/lib/api";
import type { DateFormatter } from "@/lib/dates";
import { MILESTONE_COLORS, type MilestoneDetail, type PaletteColor } from "@/lib/schemas/boards";

/** The web form's messages, by the problem names of `milestoneFormProblems`. */
const PROBLEM_TEXTS: Record<string, TranslationKey> = {
  nameRequired: "boardMilestonesNameRequired",
  nameTooLong: "boardMilestonesNameTooLong",
  descriptionTooLong: "boardMilestonesDescriptionTooLong",
  targetDateRequired: "boardMilestonesTargetDateRequired",
  startAfterTarget: "boardMilestonesStartAfterTarget",
};

/** The spoken names of the milestone palette, by the palette's colour name. */
const MILESTONE_COLOR_NAMES: Record<string, TranslationKey> = {
  Korur: "boardMilestoneColorKorur",
  Sakura: "boardMilestoneColorSakura",
  Purple: "boardMilestoneColorPurple",
  Blue: "boardMilestoneColorBlue",
  Green: "boardMilestoneColorGreen",
  Yellow: "boardMilestoneColorYellow",
  Gray: "boardMilestoneColorGray",
};

/** The form fields a server validation message can name; any other goes under the form. */
const FORM_FIELDS: readonly string[] = ["name", "description", "startDate", "targetDate"];

function firstParam(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value;
}

/**
 * Creates a milestone on the board, or edits one: the web form's name,
 * description, start and target days and one of the seven board colours,
 * with its rules and messages. An edited milestone's sheet ends with Close
 * milestone or Reopen, which run at once and keep the sheet open, and
 * Delete, which asks first. Every reader may send them, as on the web; the
 * server refuses below board admin and the app toasts its message.
 */
export function MilestoneFormSheet() {
  const { t } = useTranslation();
  const params = useLocalSearchParams<{
    boardId?: string | string[];
    milestoneId?: string | string[];
  }>();
  const boardId: string = firstParam(params.boardId) ?? "";
  const milestoneId: string | undefined = firstParam(params.milestoneId);
  const editing: boolean = !!milestoneId;

  // The detail stops loading once a delete is sent, so the deleted milestone
  // is not read again into a "not found"; the sheet keeps the copy it has.
  const [deleting, setDeleting] = React.useState<boolean>(false);
  const detail = useMilestone(boardId || undefined, deleting ? undefined : milestoneId);
  const [kept, setKept] = React.useState<MilestoneDetail | null>(null);
  if (detail.data && detail.data !== kept) setKept(detail.data);
  const milestone: MilestoneDetail | null = detail.data ?? kept;
  const boardColor: string | null | undefined = useBoard(boardId || undefined).data?.color;

  const create = useCreateMilestone(boardId);
  const update = useUpdateMilestone();
  const actions = useMilestoneActions();
  const online = useOnlineWriteGuard();

  // The fields start from the milestone once it is read; only edits are kept as state.
  const defaults: MilestoneFormValues = milestoneFormDefaults(milestone, boardColor);
  const [edits, setEdits] = React.useState<Partial<MilestoneFormValues>>({});
  const values: MilestoneFormValues = { ...defaults, ...edits };
  const changed: boolean = JSON.stringify(values) !== JSON.stringify(defaults);
  const [attempted, setAttempted] = React.useState<boolean>(false);
  const [serverErrors, setServerErrors] = React.useState<Record<string, string>>({});
  const [done, setDone] = React.useState<boolean>(false);
  const ready: boolean = !editing || !!milestone;
  const saving: boolean = create.isPending || update.isPending;

  const change = (patch: Partial<MilestoneFormValues>) => {
    setEdits((current) => ({ ...current, ...patch }));
    setServerErrors({});
  };

  const onFailure = (error: Error) => {
    // A refused field shows under it; the app's error layer toasts any other failure.
    if (isValidationError(error)) setServerErrors(error.fieldErrors);
    console.warn(`[milestones] ${milestone ? "update" : "create"} failed`, error.message);
  };

  const submit = () => {
    if (!ready || saving || done || deleting) return;
    setAttempted(true);
    if (!isMilestoneFormValid(values)) return;
    if (!online()) return;
    if (milestone) {
      update.mutate(
        {
          boardId: milestone.boardId ?? boardId,
          milestoneId: milestone.id,
          body: milestoneUpdateBody(values, milestone),
        },
        {
          onSuccess: () => {
            toast.success(t("boardMilestonesUpdated"));
            setDone(true);
          },
          onError: onFailure,
        }
      );
      return;
    }
    create.mutate(milestoneCreateBody(values), {
      onSuccess: () => {
        toast.success(t("boardMilestonesCreated"));
        setDone(true);
      },
      onError: onFailure,
    });
  };

  const target: MilestoneActionTarget | null = milestone
    ? { boardId: milestone.boardId ?? boardId, milestoneId: milestone.id, name: milestone.name }
    : null;
  const busy: boolean =
    saving || deleting || actions.isClosing || actions.isReopening || actions.isDeleting;

  const state: MilestoneFormState = ready
    ? "ready"
    : detail.isPending
      ? "loading"
      : isApiError(detail.error) && detail.error.kind === "not-found"
        ? "gone"
        : "error";

  return (
    <BottomSheet
      title={t(editing ? "boardMilestonesEdit" : "boardMilestonesAdd")}
      confirmLabel={t(editing ? "boardSettingsSave" : "boardMilestonesCreate")}
      onConfirm={submit}
      confirmDisabled={!ready || deleting}
      confirmLoading={saving}
      dirty={changed && !done}
      detent="large"
      scroll
    >
      <DismissWhen when={done} />
      {state === "ready" ? (
        <MilestoneFormFields
          values={values}
          onChange={change}
          problems={attempted ? milestoneFormProblems(values) : {}}
          serverErrors={serverErrors}
          disabled={busy}
          onSubmit={submit}
        />
      ) : (
        <MilestoneFormStatus state={state} onRetry={() => void detail.refetch()} />
      )}
      {state === "ready" && milestone && target ? (
        <ListSection>
          {milestone.status === "CLOSED" ? (
            <ListRow
              title={t("boardMilestonesReopen")}
              center
              disabled={busy}
              onPress={() => actions.reopen(target)}
            />
          ) : (
            <ListRow
              title={t("boardMilestonesClose")}
              center
              disabled={busy}
              onPress={() => actions.close(target)}
            />
          )}
          <ListRow
            title={t("boardOptionsDelete")}
            destructive
            center
            disabled={busy}
            onPress={() =>
              actions.confirmDelete(target, {
                onStart: () => setDeleting(true),
                onDeleted: () => setDone(true),
                onFailed: () => setDeleting(false),
              })
            }
          />
        </ListSection>
      ) : null}
      <View className="h-10" />
    </BottomSheet>
  );
}

/** `gone`: the edited milestone was not found. */
type MilestoneFormState = "ready" | "loading" | "error" | "gone";

function MilestoneFormStatus({
  state,
  onRetry,
}: {
  state: Exclude<MilestoneFormState, "ready">;
  onRetry: () => void;
}) {
  const { t } = useTranslation();
  if (state === "loading") {
    return (
      <View className="pt-6">
        <SkeletonGroup>
          <SkeletonRow />
          <SkeletonRow />
          <SkeletonRow />
        </SkeletonGroup>
      </View>
    );
  }
  if (state === "gone") {
    return <EmptyState variant="error" title={t("boardMilestonesGone")} className="mt-8" />;
  }
  return (
    <EmptyState
      variant="error"
      title={t("boardMilestonesLoadError")}
      onAction={onRetry}
      className="mt-8"
    />
  );
}

type MilestoneFormFieldsProps = {
  values: MilestoneFormValues;
  onChange: (patch: Partial<MilestoneFormValues>) => void;
  /** Problems found before sending, shown once the user tried to send. */
  problems: MilestoneFormProblems;
  /** Messages the server returned, by field; they win over `problems`. */
  serverErrors: Record<string, string>;
  disabled: boolean;
  onSubmit: () => void;
};

function MilestoneFormFields({
  values,
  onChange,
  problems,
  serverErrors,
  disabled,
  onSubmit,
}: MilestoneFormFieldsProps) {
  const { t } = useTranslation();
  const dates: DateFormatter = useDates();

  const startPicker = useDueDatePicker({
    value: values.startDate,
    title: t("boardMilestonesStartDate"),
    onPick: (startDate: string) => onChange({ startDate }),
    onClear: () => onChange({ startDate: null }),
  });
  const targetPicker = useDueDatePicker({
    value: values.targetDate,
    title: t("boardMilestonesTargetDate"),
    onPick: (targetDate: string) => onChange({ targetDate }),
    // Days before the start cannot be picked; the form check still refuses a
    // target that a later start date overtakes.
    minimumDate: values.startDate,
  });

  const message = (field: keyof MilestoneFormProblems): string | null => {
    const server: string | undefined = serverErrors[field];
    if (server) return server;
    const problem: string | undefined = problems[field];
    const key: TranslationKey | undefined = problem ? PROBLEM_TEXTS[problem] : undefined;
    return key ? t(key) : null;
  };
  const startError: string | null = message("startDate");
  const targetError: string | null = message("targetDate");
  const otherErrors: string[] = Object.entries(serverErrors)
    .filter(([field]) => !FORM_FIELDS.includes(field))
    .map(([, text]) => text);

  const labelOf = (color: PaletteColor): string => {
    const key: TranslationKey | undefined = MILESTONE_COLOR_NAMES[color.name];
    return key ? t(key) : color.name;
  };

  return (
    <View>
      <View className="gap-4 px-4 pt-4">
        <Text variant="footnote" className="text-muted-foreground">
          {t("boardMilestonesFormHint")}
        </Text>
        <TextField
          label={t("boardMilestonesName")}
          placeholder={t("boardMilestonesNamePlaceholder")}
          required
          value={values.name}
          onChangeText={(name: string) => onChange({ name })}
          error={message("name")}
          editable={!disabled}
          autoFocus={!values.name}
          returnKeyType="done"
          onSubmitEditing={onSubmit}
        />
        <TextField
          label={t("boardMilestonesDescription")}
          placeholder={t("boardMilestonesDescriptionPlaceholder")}
          value={values.description}
          onChangeText={(description: string) => onChange({ description })}
          multiline
          rows={3}
          error={message("description")}
          editable={!disabled}
        />
      </View>
      <ListSection>
        <ListRow
          title={t("boardMilestonesStartDate")}
          value={(values.startDate && dates.date(values.startDate)) || t("boardMilestonesPickDate")}
          subtitle={startError ?? undefined}
          subtitleTone={startError ? "destructive" : undefined}
          invalid={!!startError}
          onPress={disabled ? undefined : startPicker.open}
          disabled={disabled}
        />
        <ListRow
          title={t("boardMilestonesTargetDate")}
          value={
            (values.targetDate && dates.date(values.targetDate)) || t("boardMilestonesPickDate")
          }
          subtitle={targetError ?? undefined}
          subtitleTone={targetError ? "destructive" : undefined}
          invalid={!!targetError}
          required
          onPress={disabled ? undefined : targetPicker.open}
          disabled={disabled}
        />
      </ListSection>
      {startPicker.calendar ? <View className="pt-2">{startPicker.calendar}</View> : null}
      {targetPicker.calendar ? <View className="pt-2">{targetPicker.calendar}</View> : null}
      <View className="gap-2 px-4 pt-6">
        <Text variant="footnote" className="text-muted-foreground">
          {t("boardMilestonesColor")}
        </Text>
        <ColorSwatches
          colors={MILESTONE_COLORS}
          value={values.color}
          onChange={(color: string) => onChange({ color })}
          labelOf={labelOf}
          accessibilityLabel={t("boardMilestonesColor")}
          disabled={disabled}
        />
      </View>
      {otherErrors.map((text: string) => (
        <Text key={text} variant="footnote" className="px-6 pt-3 text-destructive">
          {text}
        </Text>
      ))}
    </View>
  );
}
