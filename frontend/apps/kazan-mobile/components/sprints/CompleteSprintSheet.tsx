import { onlineManager } from "@tanstack/react-query";
import { CircleCheck, TriangleAlert } from "lucide-react-native";
import * as React from "react";
import { ActivityIndicator, Alert, View } from "react-native";

import { EMPTY_FILTERS } from "@/components/boards/boardModel";
import { BottomSheet } from "@/components/kazan/BottomSheet";
import { EmptyState } from "@/components/kazan/EmptyState";
import { ListRow, ListSection } from "@/components/kazan/List";
import {
  completeBody,
  completeChoices,
  completeTargets,
  completionCounts,
  defaultCompleteChoice,
  type CompleteChoice,
} from "@/components/sprints/sprintModel";
import { useSprintSheetParams } from "@/components/sprints/useSprintSheetParams";
import { useSprintStateLabel } from "@/components/sprints/useSprintStateLabel";
import { DismissWhen } from "@/components/ticket/DismissWhen";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";
import { toast } from "@/components/ui/toast";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import { useBoardSprints, useBoardTickets } from "@/hooks/queries/boards";
import { useCompleteSprint, useSprintDetail } from "@/hooks/queries/sprints";
import { useTranslation, type TranslationKey } from "@/i18n";
import type { BoardTicket, Sprint } from "@/lib/schemas/boards";
import type { SprintDetail } from "@/lib/schemas/sprints";

const CHOICE_COPY: Record<CompleteChoice, { title: TranslationKey; body: TranslationKey }> = {
  "next-sprint": { title: "sprintCompleteToNext", body: "sprintCompleteToNextBody" },
  backlog: { title: "sprintCompleteToBacklog", body: "sprintCompleteToBacklogBody" },
  sprint: { title: "sprintCompleteToSprint", body: "sprintCompleteToSprintBody" },
  delete: { title: "sprintCompleteDelete", body: "sprintCompleteDeleteBody" },
};

/**
 * Complete sprint: how much of the sprint is done, and what happens to the
 * unfinished tickets (the next sprint of the series, the backlog, another
 * open sprint of the board, or deleted). Only an active sprint completes.
 */
export function CompleteSprintSheet() {
  const { t } = useTranslation();
  const colors = useThemeColors();
  const { boardId, sprintId } = useSprintSheetParams();
  const sprint = useSprintDetail(boardId, sprintId);
  const sprints = useBoardSprints(boardId);
  const tickets = useBoardTickets(boardId, sprintId ? { sprintId } : null, EMPTY_FILTERS, "");

  const failed: boolean = sprint.isError || sprints.isError || tickets.isError;
  const retry = () => {
    if (sprint.isError) void sprint.refetch();
    if (sprints.isError) void sprints.refetch();
    if (tickets.isError) void tickets.refetch();
  };

  if (!boardId || !sprintId || failed) {
    return (
      <BottomSheet title={t("sprintCompleteTitle")}>
        <EmptyState
          variant="error"
          title={t("sprintSheetError")}
          onAction={failed ? retry : undefined}
          className="mt-8"
        />
      </BottomSheet>
    );
  }
  if (!sprint.data || !sprints.data || !tickets.data) {
    return (
      <BottomSheet title={t("sprintCompleteTitle")}>
        <View className="items-center py-10">
          <ActivityIndicator color={colors.mutedForeground} />
        </View>
      </BottomSheet>
    );
  }
  if (sprint.data.status !== "ACTIVE") {
    return (
      <BottomSheet title={t("sprintCompleteTitle")}>
        <EmptyState
          icon={CircleCheck}
          title={t("sprintCompleteNotActive", { name: sprint.data.name })}
          className="mt-8"
        />
      </BottomSheet>
    );
  }
  return (
    <CompleteSprintForm
      boardId={boardId}
      sprint={sprint.data}
      sprints={sprints.data}
      tickets={tickets.data}
    />
  );
}

type CompleteSprintFormProps = {
  boardId: string;
  sprint: SprintDetail;
  sprints: Sprint[];
  tickets: BoardTicket[];
};

function CompleteSprintForm({ boardId, sprint, sprints, tickets }: CompleteSprintFormProps) {
  const { t } = useTranslation();
  const sprintState = useSprintStateLabel();
  const complete = useCompleteSprint(boardId, sprint.id);
  const { completed, incomplete, rate } = completionCounts(tickets);
  const targets: Sprint[] = React.useMemo(
    () => completeTargets(sprints, sprint.id),
    [sprints, sprint.id]
  );
  const choices: CompleteChoice[] = completeChoices(sprint.series, targets.length);
  const [choice, setChoice] = React.useState<CompleteChoice>(() =>
    defaultCompleteChoice(sprint.series)
  );
  const [targetId, setTargetId] = React.useState<string | null>(null);
  const [done, setDone] = React.useState<boolean>(false);
  const seriesName: string | null = sprint.series?.enabled ? sprint.series.name : null;

  const send = () => {
    // With nothing unfinished the choice moves nothing; the backlog is the plain request.
    const body = incomplete > 0 ? completeBody(choice, targetId) : completeBody("backlog", null);
    if (!body) return;
    complete.mutate(body, {
      onSuccess: () => {
        toast.success(t("sprintCompleted", { name: sprint.name }));
        setDone(true);
      },
      // The app's error layer toasts the server's message.
      onError: (error: Error) => console.warn("[sprints] complete failed", error.message),
    });
  };

  const submit = () => {
    if (!onlineManager.isOnline()) {
      toast.error(t("offlineWriteBlocked"));
      return;
    }
    if (incomplete > 0 && choice === "delete") {
      Alert.alert(
        t("sprintCompleteDeleteConfirmTitle", { count: incomplete }),
        t("sprintCompleteDeleteConfirmMessage"),
        [
          { text: t("kitCancel"), style: "cancel" },
          { text: t("sprintCompleteDeleteConfirm"), style: "destructive", onPress: send },
        ]
      );
      return;
    }
    send();
  };

  const missingTarget: boolean = incomplete > 0 && choice === "sprint" && !targetId;

  return (
    <BottomSheet title={t("sprintCompleteTitle")} scroll>
      <DismissWhen when={done} />
      <Text variant="footnote" className="px-6 pt-2 text-muted-foreground">
        {sprint.name}
      </Text>
      <View className="flex-row gap-2 px-4 pt-4">
        <StatTile label={t("sprintCompleteCompleted")} value={String(completed)} />
        <StatTile label={t("sprintCompleteIncomplete")} value={String(incomplete)} />
        <StatTile label={t("sprintCompleteRate")} value={`${rate}%`} />
      </View>

      {incomplete > 0 ? (
        <ListSection header={t("sprintCompleteIncompleteHeader")}>
          {choices.map((option) => (
            <ListRow
              key={option}
              title={t(CHOICE_COPY[option].title)}
              subtitle={
                option === "next-sprint" && seriesName
                  ? t("sprintCompleteToNextBodyNamed", { name: seriesName })
                  : t(CHOICE_COPY[option].body)
              }
              subtitleTone={option === "delete" ? "destructive" : "muted"}
              wrap
              accessory="radio"
              checked={choice === option}
              onPress={() => setChoice(option)}
              disabled={complete.isPending}
            />
          ))}
        </ListSection>
      ) : null}

      {incomplete > 0 && choice === "sprint" ? (
        <ListSection header={t("sprintCompleteTargetHeader")}>
          {targets.map((target) => (
            <ListRow
              key={target.id}
              title={target.name}
              subtitle={sprintState(target)}
              accessory="radio"
              checked={targetId === target.id}
              onPress={() => setTargetId(target.id)}
              disabled={complete.isPending}
            />
          ))}
        </ListSection>
      ) : null}

      <Card className="mx-4 mt-6 gap-2">
        <View className="flex-row items-start gap-2">
          <Icon as={TriangleAlert} size={16} className="mt-0.5 text-warning" />
          <Text variant="subhead" className="flex-1">
            {seriesName
              ? t("sprintCompleteWarningSeries", { name: seriesName })
              : t("sprintCompleteWarning")}
          </Text>
        </View>
      </Card>

      <View className="px-4 pb-10 pt-4">
        <Button
          block
          icon={CircleCheck}
          onPress={submit}
          loading={complete.isPending}
          disabled={missingTarget || complete.isPending}
        >
          {t("sprintCompleteConfirm")}
        </Button>
      </View>
    </BottomSheet>
  );
}

/** One count of the summary. */
function StatTile({ label, value }: { label: string; value: string }) {
  return (
    <Card className="flex-1 items-center gap-1 py-3">
      <Text variant="title-2">{value}</Text>
      <Text variant="caption" className="text-muted-foreground">
        {label}
      </Text>
    </Card>
  );
}
