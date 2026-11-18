import { onlineManager } from "@tanstack/react-query";
import { useLocalSearchParams } from "expo-router";
import { CalendarRange } from "lucide-react-native";
import * as React from "react";
import { ActivityIndicator, View } from "react-native";

import { resolveStatus } from "@/components/kazan/ticketMetaModel";
import { BottomSheet } from "@/components/kazan/BottomSheet";
import { EmptyState } from "@/components/kazan/EmptyState";
import { ListRow, ListSection } from "@/components/kazan/List";
import { parseMoveToSprintParams } from "@/components/sprints/moveToSprintRoutes";
import {
  defaultMoveTarget,
  defaultPickedSubTickets,
  sprintMoveTargets,
  subTicketMove,
  type SprintMoveTarget,
} from "@/components/sprints/sprintModel";
import { useSprintStateLabel } from "@/components/sprints/useSprintStateLabel";
import { ChoiceRow, type Choice } from "@/components/ticket/ChoiceRow";
import { DismissWhen } from "@/components/ticket/DismissWhen";
import { useWriteFailure } from "@/components/ticket/useWriteFailure";
import { Text } from "@/components/ui/text";
import { toast } from "@/components/ui/toast";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import { useBoardSprints } from "@/hooks/queries/boards";
import { useTicketMoveSource } from "@/hooks/queries/sprints";
import { useMoveTicketPlacement, type SubTicketMoveMode } from "@/hooks/queries/ticketActions";
import { useTicketRef, type TicketRef } from "@/hooks/queries/ticketDetail";
import { useTranslation, type TranslationKey } from "@/i18n";
import type { Sprint } from "@/lib/schemas/boards";
import type { MoveSubTicket, TicketMoveSource } from "@/lib/schemas/sprints";

const SUB_TICKET_CHOICES: readonly { mode: SubTicketMoveMode; label: TranslationKey }[] = [
  { mode: "NONE", label: "ticketMoveOnlyThis" },
  { mode: "UNFINISHED", label: "ticketMoveWithUnfinished" },
  { mode: "SELECTED", label: "moveSprintPicked" },
];

/**
 * Move to sprint: plans a ticket into another open sprint of its board, or
 * takes it to the backlog, and chooses which sub-tickets come along: none,
 * the unfinished ones, or those the user picks, wherever they sit now.
 * Sub-tickets left behind keep their own sprint. A failed move keeps the
 * sheet open and offers Retry.
 */
export function MoveToSprintSheet() {
  const { t } = useTranslation();
  const colors = useThemeColors();
  const params = useLocalSearchParams<Record<string, string | string[]>>();
  const { ticketKey, sprintId, pickSubTickets } = parseMoveToSprintParams(params);
  const ref = useTicketRef(ticketKey);
  const source = useTicketMoveSource(ref.data);
  const sprints = useBoardSprints(ref.data?.boardId);

  const failed: boolean = ref.isError || source.isError || sprints.isError;
  const retry = () => {
    if (ref.isError) void ref.refetch();
    if (source.isError) void source.refetch();
    if (sprints.isError) void sprints.refetch();
  };

  if (!ticketKey || failed) {
    return (
      <BottomSheet title={t("moveSprintTitle")}>
        <EmptyState
          variant="error"
          title={t("moveSprintLoadError")}
          onAction={failed ? retry : undefined}
          className="mt-8"
        />
      </BottomSheet>
    );
  }
  if (!ref.data || !source.data || !sprints.data) {
    return (
      <BottomSheet title={t("moveSprintTitle")}>
        <View className="items-center py-10">
          <ActivityIndicator color={colors.mutedForeground} />
        </View>
      </BottomSheet>
    );
  }
  return (
    <MoveToSprintForm
      ticketRef={ref.data}
      source={source.data}
      sprints={sprints.data}
      requestedId={sprintId}
      pickSubTickets={pickSubTickets}
    />
  );
}

type MoveToSprintFormProps = {
  ticketRef: TicketRef;
  source: TicketMoveSource;
  sprints: Sprint[];
  requestedId: string | null;
  pickSubTickets: boolean;
};

function MoveToSprintForm({
  ticketRef,
  source,
  sprints,
  requestedId,
  pickSubTickets,
}: MoveToSprintFormProps) {
  const { t } = useTranslation();
  const fail = useWriteFailure();
  const sprintState = useSprintStateLabel();
  const move = useMoveTicketPlacement(ticketRef);
  const targets: SprintMoveTarget[] = React.useMemo(
    () => sprintMoveTargets(sprints, source.sprint?.id ?? null),
    [source.sprint?.id, sprints]
  );
  const subTickets: MoveSubTicket[] = source.subTickets ?? NO_SUB_TICKETS;
  const [targetId, setTargetId] = React.useState<string | null>(() =>
    defaultMoveTarget(targets, requestedId)
  );
  const [mode, setMode] = React.useState<SubTicketMoveMode>(
    pickSubTickets && subTickets.length > 0 ? "SELECTED" : "UNFINISHED"
  );
  const [picked, setPicked] = React.useState<string[]>(() => defaultPickedSubTickets(subTickets));
  const [done, setDone] = React.useState<boolean>(false);
  const target: SprintMoveTarget | undefined = targets.find((item) => item.id === targetId);

  const choices: Choice[] = targets.map((item) => ({
    id: item.id,
    label: item.sprint ? `${item.sprint.name} · ${sprintState(item.sprint)}` : t("ticketBacklog"),
    value: item.sprint ? item.sprint.name : t("ticketBacklog"),
  }));

  const placeOf = (sub: MoveSubTicket): string => {
    if (resolveStatus(sub.status).status === "DONE") return t(resolveStatus(sub.status).labelKey);
    return sub.sprint?.name ?? t("ticketBacklog");
  };

  const toggle = (id: string) =>
    setPicked((current) =>
      current.includes(id) ? current.filter((item) => item !== id) : [...current, id]
    );

  const submit = () => {
    if (!target) return;
    if (!onlineManager.isOnline()) {
      toast.error(t("offlineWriteBlocked"));
      return;
    }
    const sprint: Sprint | null = target.sprint;
    move.mutate(
      { sprintId: sprint ? sprint.id : null, ...subTicketMove(mode, picked) },
      {
        onSuccess: () => {
          toast.success(
            sprint
              ? t("ticketMovedToSprint", { key: source.key, sprint: sprint.name })
              : t("ticketMovedToBacklog", { key: source.key })
          );
          setDone(true);
        },
        onError: (error: Error) => fail(error, "post", submit),
      }
    );
  };

  return (
    <BottomSheet
      title={t("moveSprintTitle")}
      confirmLabel={t("ticketMoveConfirm")}
      onConfirm={submit}
      confirmDisabled={!target || move.isPending}
      confirmLoading={move.isPending}
      scroll
    >
      <DismissWhen when={done} />
      <Text variant="footnote" className="px-6 pt-2 text-muted-foreground" numberOfLines={2}>
        {`${source.key} ${source.title}`}
      </Text>
      {targets.length === 0 ? (
        <EmptyState title={t("moveSprintNoTargets")} className="mt-8" />
      ) : (
        <>
          <ListSection>
            <ChoiceRow
              title={t("moveSprintTarget")}
              icon={CalendarRange}
              value={targetId}
              choices={choices}
              onChange={setTargetId}
              disabled={move.isPending}
            />
          </ListSection>

          {subTickets.length > 0 ? (
            <ListSection header={t("moveSprintSubTicketsHeader")}>
              {SUB_TICKET_CHOICES.map((choice) => (
                <ListRow
                  key={choice.mode}
                  title={t(choice.label)}
                  wrap
                  accessory="radio"
                  checked={mode === choice.mode}
                  onPress={() => setMode(choice.mode)}
                  disabled={move.isPending}
                />
              ))}
            </ListSection>
          ) : null}

          {subTickets.length > 0 && mode === "SELECTED" ? (
            <ListSection header={t("moveSprintPickHeader")} footer={t("moveSprintPickFooter")}>
              {subTickets.map((sub) => (
                <ListRow
                  key={sub.id}
                  title={`${sub.key} ${sub.title}`}
                  subtitle={placeOf(sub)}
                  accessory="checkbox"
                  checked={picked.includes(sub.id)}
                  onPress={() => toggle(sub.id)}
                  disabled={move.isPending}
                />
              ))}
            </ListSection>
          ) : null}
        </>
      )}
    </BottomSheet>
  );
}

const NO_SUB_TICKETS: MoveSubTicket[] = [];
