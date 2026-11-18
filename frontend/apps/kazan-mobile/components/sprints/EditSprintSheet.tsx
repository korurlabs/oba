import { onlineManager } from "@tanstack/react-query";
import * as React from "react";
import { ActivityIndicator, View } from "react-native";

import type { EditSprintTab } from "@/components/boards/boardRoutes";
import { BottomSheet } from "@/components/kazan/BottomSheet";
import { EmptyState } from "@/components/kazan/EmptyState";
import { useSubviewHardwareBack } from "@/components/kazan/useSubviewHardwareBack";
import { RecurrenceFields } from "@/components/sprints/RecurrenceFields";
import { SeriesStatus } from "@/components/sprints/SeriesStatus";
import {
  LinkSprintBoards,
  SprintBoardsTab,
  sprintSubviewSheetProps,
  type SprintBoardRef,
} from "@/components/sprints/SprintBoardsTab";
import { bulkLinkBody, togglePickedBoard } from "@/components/sprints/sprintBoardsModel";
import { SprintFormFields } from "@/components/sprints/SprintFormFields";
import {
  DEFAULT_SPRINT_LENGTH_DAYS,
  editNaming,
  editSaveSteps,
  editSprintValues,
  recurrenceProblems,
  seriesBody,
  sprintFieldProblems,
  sprintLengthDays,
  type EditSprintEdits,
  type EditSprintValues,
  type RecurrenceForm,
  type RecurrenceProblems,
  type SprintFieldProblems,
  type SprintFields,
} from "@/components/sprints/sprintModel";
import { UnlinkSprintView } from "@/components/sprints/UnlinkSprintView";
import { useSprintSheetParams } from "@/components/sprints/useSprintSheetParams";
import { DismissWhen } from "@/components/ticket/DismissWhen";
import { SegmentedControl } from "@/components/ui/segmented-control";
import { Text } from "@/components/ui/text";
import { toast } from "@/components/ui/toast";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import {
  useBulkLinkSprint,
  useSprintDetail,
  useUnlinkSprint,
  useUpdateSprint,
  useUpdateSprintSeries,
} from "@/hooks/queries/sprints";
import { useOnlineWriteGuard } from "@/hooks/useOnlineWriteGuard";
import { useTranslation, type TranslationKey } from "@/i18n";
import { isValidationError } from "@/lib/api";
import type { SprintDetail } from "@/lib/schemas/sprints";

const TABS: readonly EditSprintTab[] = ["general", "boards", "automation"];

const TAB_LABEL: Record<EditSprintTab, TranslationKey> = {
  general: "sprintTabGeneral",
  boards: "sprintTabBoards",
  automation: "sprintTabAutomation",
};

/** What the sheet shows: its tabs, or the link or unlink view of the Boards tab in their place. */
type EditSprintView =
  | { kind: "tabs" }
  | { kind: "link" }
  | { kind: "unlink"; board: SprintBoardRef };

const TABS_VIEW: EditSprintView = { kind: "tabs" };

/**
 * Edit sprint: the name, goal and dates on General, the boards sharing the
 * sprint on Boards, the series automation on Automation. General and
 * Automation share one Save, which writes what changed on either; a completed
 * sprint keeps them read-only. Boards links and unlinks at once, in every
 * sprint status, as on the web.
 */
export function EditSprintSheet() {
  const { t } = useTranslation();
  const colors = useThemeColors();
  const { boardId, sprintId, tab } = useSprintSheetParams();
  const sprint = useSprintDetail(boardId, sprintId);

  if (!boardId || !sprintId || sprint.isError) {
    return (
      <BottomSheet title={t("sprintEditTitle")}>
        <EmptyState
          variant="error"
          title={t("sprintSheetError")}
          onAction={sprint.isError ? () => void sprint.refetch() : undefined}
          className="mt-8"
        />
      </BottomSheet>
    );
  }
  if (sprint.isPending) {
    return (
      <BottomSheet title={t("sprintEditTitle")}>
        <View className="items-center py-10">
          <ActivityIndicator color={colors.mutedForeground} />
        </View>
      </BottomSheet>
    );
  }
  return <EditSprintForm boardId={boardId} sprint={sprint.data} initialTab={tab} />;
}

type EditSprintFormProps = {
  boardId: string;
  /**
   * The sprint as last read. The sheet can open on a cached copy that a write
   * elsewhere has made stale, so the form follows each read; only the edits
   * are kept as state, and a read does not reset what was typed.
   */
  sprint: SprintDetail;
  initialTab: EditSprintTab;
};

const NO_EDITS: EditSprintEdits = { fields: {}, recurrence: {} };

function EditSprintForm({ boardId, sprint, initialTab }: EditSprintFormProps) {
  const { t } = useTranslation();
  const update = useUpdateSprint(boardId, sprint.id);
  const updateSeries = useUpdateSprintSeries(boardId, sprint.id);
  const [tab, setTab] = React.useState<EditSprintTab>(initialTab);
  const [edits, setEdits] = React.useState<EditSprintEdits>(NO_EDITS);
  const { initialFields, fields, initialRecurrence, recurrence }: EditSprintValues =
    editSprintValues(sprint, edits);
  const [attempted, setAttempted] = React.useState<boolean>(false);
  const [saving, setSaving] = React.useState<boolean>(false);
  const [saved, setSaved] = React.useState<boolean>(false);
  const [serverErrors, setServerErrors] = React.useState<Record<string, string>>({});
  const [seriesError, setSeriesError] = React.useState<string | null>(null);
  const [view, setView] = React.useState<EditSprintView>(TABS_VIEW);
  const [picked, setPicked] = React.useState<string[]>([]);
  const bulkLink = useBulkLinkSprint(boardId, sprint.id);
  const unlink = useUnlinkSprint(sprint.id);
  const online = useOnlineWriteGuard();
  const linkedIds: string[] = React.useMemo(
    () => (sprint.boards ?? []).map((board) => board.id),
    [sprint.boards]
  );

  const readOnly: boolean = sprint.status === "COMPLETED";
  const { editsSeriesName } = editNaming(sprint);
  const datesLength: number | null = sprintLengthDays(fields.startDay, fields.endDay);
  const fieldProblems: SprintFieldProblems = sprintFieldProblems(fields, editsSeriesName);
  const repeatProblems: RecurrenceProblems = recurrenceProblems(recurrence, datesLength);
  const fieldsChanged: boolean = JSON.stringify(fields) !== JSON.stringify(initialFields);
  const recurrenceChanged: boolean =
    JSON.stringify(recurrence) !== JSON.stringify(initialRecurrence);
  const dirty: boolean = !saved && (fieldsChanged || recurrenceChanged);

  const changeFields = (patch: Partial<SprintFields>) => {
    setEdits((current) => ({ ...current, fields: { ...current.fields, ...patch } }));
    setServerErrors({});
  };
  const changeRecurrence = (patch: Partial<RecurrenceForm>) => {
    setEdits((current) => ({ ...current, recurrence: { ...current.recurrence, ...patch } }));
    setSeriesError(null);
  };

  const save = async () => {
    setAttempted(true);
    if (Object.keys(fieldProblems).length > 0) {
      setTab("general");
      return;
    }
    if (Object.keys(repeatProblems).length > 0) {
      setTab(repeatProblems.dates ? "general" : "automation");
      return;
    }
    if (!fieldsChanged && !recurrenceChanged) {
      setSaved(true);
      return;
    }
    if (!onlineManager.isOnline()) {
      toast.error(t("offlineWriteBlocked"));
      return;
    }
    const steps = editSaveSteps({
      sprint,
      fields,
      fieldsChanged,
      recurrence,
      recurrenceChanged,
    });
    if (!steps) return;
    setSaving(true);
    try {
      if (steps.update) await update.mutateAsync(steps.update);
    } catch (error: unknown) {
      setSaving(false);
      // A refused field shows under it; other failures are toasted by the app's error layer.
      if (isValidationError(error)) {
        setServerErrors(error.fieldErrors);
        setTab("general");
      } else {
        console.warn("[sprints] update failed", error instanceof Error ? error.message : error);
      }
      return;
    }
    try {
      if (steps.series) {
        await updateSeries.mutateAsync({
          series: seriesBody(recurrence, datesLength ?? DEFAULT_SPRINT_LENGTH_DAYS),
        });
      }
    } catch (error: unknown) {
      setSaving(false);
      setTab("automation");
      if (isValidationError(error)) {
        setSeriesError(Object.values(error.fieldErrors)[0] ?? error.message);
      } else {
        console.warn(
          "[sprints] automation update failed",
          error instanceof Error ? error.message : error
        );
      }
      return;
    }
    setSaving(false);
    toast.success(t("sprintSaved", { name: sprint.name }));
    setSaved(true);
  };

  const backToTabs = React.useCallback(() => {
    setView(TABS_VIEW);
    setPicked([]);
  }, []);
  const subviewBusy: boolean = bulkLink.isPending || unlink.isPending;
  useSubviewHardwareBack(view.kind === "tabs" ? null : backToTabs, subviewBusy);

  const link = () => {
    const body = bulkLinkBody(picked);
    if (!body || bulkLink.isPending || !online()) return;
    bulkLink.mutate(body, {
      onSuccess: backToTabs,
      // The refusal is toasted by the hook; the picks stay for another try.
      onError: (error: Error) => console.warn("[sprints] link boards failed", error.message),
    });
  };

  if (view.kind === "link") {
    return (
      <BottomSheet
        title={t("sprintBoardsLinkBoards")}
        dirty={dirty || picked.length > 0}
        scroll
        {...sprintSubviewSheetProps(
          {
            onBack: backToTabs,
            backDisabled: bulkLink.isPending,
            confirmLabel: bulkLink.isPending
              ? t("sprintBoardsLinking")
              : t("sprintBoardsLinkBoards"),
            onConfirm: link,
            confirmDisabled: picked.length === 0,
            confirmLoading: bulkLink.isPending,
          },
          t("kitCancel")
        )}
      >
        <LinkSprintBoards
          linkedIds={linkedIds}
          picked={picked}
          onToggle={(id: string) => setPicked((current) => togglePickedBoard(current, id))}
          disabled={bulkLink.isPending}
        />
        <View className="h-10" />
      </BottomSheet>
    );
  }

  if (view.kind === "unlink") {
    return (
      <BottomSheet
        title={t("sprintUnlinkTitle", { board: view.board.name })}
        dirty={dirty}
        scroll
        {...sprintSubviewSheetProps(
          { onBack: backToTabs, backDisabled: unlink.isPending },
          t("kitCancel")
        )}
      >
        <UnlinkSprintView
          sprintId={sprint.id}
          board={view.board}
          unlink={unlink}
          onDone={backToTabs}
        />
      </BottomSheet>
    );
  }

  const shownFieldProblems: SprintFieldProblems = attempted ? fieldProblems : {};
  const shownRepeatProblems: RecurrenceProblems = attempted ? repeatProblems : {};
  const utcDays: boolean = recurrence.enabled || (sprint.series?.enabled ?? false);

  return (
    <BottomSheet
      title={t("sprintEditTitle")}
      confirmLabel={readOnly ? undefined : t("sprintSave")}
      onConfirm={readOnly ? undefined : () => void save()}
      confirmLoading={saving}
      dirty={dirty}
      scroll
    >
      <DismissWhen when={saved} />
      <View className="px-4 pt-3">
        <SegmentedControl
          values={TABS.map((value) => t(TAB_LABEL[value]))}
          selectedIndex={TABS.indexOf(tab)}
          onChange={(index: number) => {
            const next: EditSprintTab | undefined = TABS[index];
            if (next) setTab(next);
          }}
        />
      </View>
      {readOnly && tab !== "boards" ? (
        <Text variant="footnote" className="px-6 pt-3 text-muted-foreground">
          {t("sprintCompletedReadOnly")}
        </Text>
      ) : null}
      {tab === "general" ? (
        <SprintFormFields
          fields={fields}
          onChange={changeFields}
          problems={shownFieldProblems}
          serverErrors={serverErrors}
          round={editsSeriesName ? sprint.round : null}
          roundHelper={editsSeriesName ? "edit" : "create"}
          utcDays={utcDays}
          datesError={shownRepeatProblems.dates ? t("sprintRepeatDatesRange") : null}
          disabled={readOnly || saving}
        />
      ) : tab === "boards" ? (
        <SprintBoardsTab
          boardId={boardId}
          sprintId={sprint.id}
          onLink={() => setView({ kind: "link" })}
          onUnlink={(board: SprintBoardRef) => setView({ kind: "unlink", board })}
        />
      ) : (
        <>
          <RecurrenceFields
            value={recurrence}
            onChange={changeRecurrence}
            datesLength={datesLength}
            endDay={fields.endDay || null}
            problems={shownRepeatProblems}
            disabled={readOnly || saving}
          />
          {seriesError ? (
            <Text variant="footnote" className="px-6 pt-3 text-destructive">
              {seriesError}
            </Text>
          ) : null}
          <SeriesStatus
            series={sprint.series}
            sprintName={sprint.name}
            switchOn={recurrence.enabled}
          />
        </>
      )}
      <View className="h-10" />
    </BottomSheet>
  );
}
