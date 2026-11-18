import { useRouter } from "expo-router";
import { ArrowRightLeft } from "lucide-react-native";
import * as React from "react";
import { ActivityIndicator, Platform, View } from "react-native";

import { BottomSheet, type SheetHeaderAction } from "@/components/kaizen/BottomSheet";
import { EmptyState, InlineError } from "@/components/kaizen/EmptyState";
import { ListRow, ListSection } from "@/components/kaizen/List";
import { useSubviewHardwareBack } from "@/components/kaizen/useSubviewHardwareBack";
import { BACKLOG_PLACEMENT, openSprints } from "@/components/ticket/newTicketModel";
import {
  moveBoardChanges,
  moveTargets,
  resolvePlacementOnBoard,
  type MoveChange,
  type MoveTarget,
} from "@/components/ticket/ticketActionsModel";
import { ticketHref } from "@/components/ticket/ticketRoutes";
import { useSheetTicket } from "@/components/ticket/useSheetTicket";
import { useWriteFailure } from "@/components/ticket/useWriteFailure";
import { Button } from "@/components/ui/button";
import { Text } from "@/components/ui/text";
import { toast } from "@/components/ui/toast";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import { useBoardSprints, useBoardTree } from "@/hooks/queries/boards";
import { useMoveTicketToBoard, type MovedToBoard } from "@/hooks/queries/ticketActions";
import { useTranslation } from "@/i18n";
import type { Sprint } from "@/lib/schemas/boards";
import type { TicketDetail } from "@/lib/schemas/tickets";

const IS_ANDROID: boolean = Platform.OS === "android";

/** The two steps of the sheet, each opening at its top. */
const BOARD_STEP = "board";
const PLACEMENT_STEP = "placement";

/** What happens to the sub-tickets: they move along, or stay on the old board without a parent. */
type SubTicketChoice = "move" | "detach";

/**
 * Moves a top-level ticket to another board of the organization, in two
 * steps. First the board; then where the ticket lands on it, the backlog or
 * a sprint that is not completed, with the ticket's own sprint proposed when
 * the board shares it, else a sprint of the same name, else the backlog. A
 * ticket with sub-tickets takes them along unless the user leaves them on
 * the old board without a parent. The sheet lists what the move changes
 * before it is sent: the ticket gets a key of the new board and loses a
 * project or milestone of the old one. Once the move is done the sheet
 * returns to the ticket screen under its new key; a failed move keeps the
 * sheet open and offers Retry.
 */
export function MoveToBoardSheet() {
  const { t } = useTranslation();
  const router = useRouter();
  const colors = useThemeColors();
  const fail = useWriteFailure();
  const { ticketKey, ticketRef, ticket, error, retry } = useSheetTicket();
  const tree = useBoardTree();
  const move = useMoveTicketToBoard(ticketRef, ticketKey ?? "");
  const [picked, setPicked] = React.useState<string | null>(null);
  // The user's own placement; until one is chosen the proposed one applies.
  const [placement, setPlacement] = React.useState<string | null>(null);
  const [subTickets, setSubTickets] = React.useState<SubTicketChoice>("move");

  const targets: MoveTarget[] = ticket ? moveTargets(tree.data ?? [], ticket.board.id) : [];
  const target: MoveTarget | undefined = targets.find((item) => item.id === picked);
  const sprints = useBoardSprints(target?.id);
  const proposed: string | null =
    ticket && sprints.data ? resolvePlacementOnBoard(ticket.sprint, sprints.data) : null;
  const chosen: string | null = placement ?? proposed;
  const detach: boolean = subTickets === "detach";

  const pick = (id: string) => {
    setPicked(id);
    setPlacement(null);
    setSubTickets("move");
  };
  const back = React.useCallback(() => setPicked(null), []);

  // Android's Back on the placement step returns to the board list.
  const onPlacementStep: boolean = Boolean(ticket && target);
  const moving: boolean = move.isPending;
  useSubviewHardwareBack(onPlacementStep ? back : null, moving);

  const submit = () => {
    if (!target || !chosen || move.isPending) return;
    move.mutate(
      { targetBoardId: target.id, placement: chosen, detach },
      {
        onSuccess: ({ ticket: moved, feedback }: MovedToBoard) => {
          const message: string = t("ticketMovedToBoard", { key: moved.key });
          if (feedback.tone === "warning") {
            toast.warning(message, {
              description: t("bulkMoveMilestoneCleared", {
                count: feedback.count,
                keys: feedback.keys,
              }),
            });
          } else {
            toast.success(message);
          }
          router.dismissTo(ticketHref(moved.key));
        },
        onError: (failure) => fail(failure, "post", submit),
      }
    );
  };

  if (ticket && target) {
    const backAction: SheetHeaderAction = {
      label: t("boardBack"),
      onPress: back,
      disabled: moving,
    };
    const confirm = (
      <MoveConfirm
        label={t("ticketMoveBoardConfirm", { board: target.name })}
        onConfirm={submit}
        disabled={!chosen || sprints.isError}
        loading={moving}
      />
    );
    // Both steps keep the `scroll` layout on iOS: a sheet that switches to the
    // pinned-footer layout and back mounts a new scroll view, which the native
    // form sheet never sizes under the header again.
    return (
      <BottomSheet
        title={t("ticketMoveBoardTitle", { key: ticket.key, board: target.name })}
        headerAction={backAction}
        scroll
        scrollKey={PLACEMENT_STEP}
        footer={
          IS_ANDROID ? (
            <View className="flex-row gap-3">
              <Button variant="ghost" disabled={moving} onPress={back}>
                {backAction.label}
              </Button>
              {confirm}
            </View>
          ) : undefined
        }
      >
        <PlacementStep
          ticket={ticket}
          target={target}
          sprints={sprints.data}
          sprintsFailed={sprints.isError}
          onRetrySprints={() => void sprints.refetch()}
          placement={chosen}
          onPlacement={setPlacement}
          subTickets={subTickets}
          onSubTickets={setSubTickets}
          changes={moveBoardChanges(ticket, target, chosen, detach)}
          disabled={moving}
        />
        {IS_ANDROID ? null : <View className="px-4 pb-10 pt-6">{confirm}</View>}
      </BottomSheet>
    );
  }

  const loadError: unknown = error ?? tree.error;

  return (
    <BottomSheet title={t("ticketMoveToBoard")} scroll scrollKey={BOARD_STEP}>
      {loadError ? (
        <InlineError
          message={t("ticketBoardsError")}
          onRetry={() => {
            if (error) void retry();
            else void tree.refetch();
          }}
          className="m-4"
        />
      ) : !ticket || tree.isPending ? (
        <View className="items-center py-10">
          <ActivityIndicator color={colors.mutedForeground} />
        </View>
      ) : targets.length === 0 ? (
        <EmptyState title={t("ticketMoveNoBoards")} className="mt-8" />
      ) : (
        <>
          <Text className="px-6 pt-2 font-sans text-subhead text-muted-foreground">
            {t("ticketMoveToBoardIntro", { key: ticket.key, board: ticket.board.name })}
          </Text>
          <ListSection footer={t("ticketMoveToBoardFooter")}>
            {targets.map((item) => (
              <ListRow
                key={item.id}
                title={item.name}
                subtitle={item.prefix ?? undefined}
                indent={item.depth}
                accessory="chevron"
                onPress={() => pick(item.id)}
              />
            ))}
          </ListSection>
        </>
      )}
    </BottomSheet>
  );
}

type PlacementStepProps = {
  ticket: TicketDetail;
  target: MoveTarget;
  /** The target board's sprints; undefined while they load. */
  sprints: Sprint[] | undefined;
  sprintsFailed: boolean;
  onRetrySprints: () => void;
  /** `BACKLOG_PLACEMENT` or a sprint id; null until the sprints are known. */
  placement: string | null;
  onPlacement: (placement: string) => void;
  subTickets: SubTicketChoice;
  onSubTickets: (choice: SubTicketChoice) => void;
  changes: MoveChange[];
  disabled: boolean;
};

/**
 * Where the ticket lands on the picked board, what happens to its
 * sub-tickets, and what the move changes. The hint says whether the
 * ticket's sprint is shared with that board, because only a sprint linked
 * to the board can hold the ticket there.
 */
function PlacementStep({
  ticket,
  target,
  sprints,
  sprintsFailed,
  onRetrySprints,
  placement,
  onPlacement,
  subTickets,
  onSubTickets,
  changes,
  disabled,
}: PlacementStepProps) {
  const { t } = useTranslation();
  const colors = useThemeColors();
  const board: string = target.name;
  const open: Sprint[] = openSprints(sprints ?? []);
  const current = ticket.sprint ?? null;
  const hint: string = current
    ? open.some((sprint) => sprint.id === current.id)
      ? t("ticketMoveBoardSprintShared", { sprint: current.name, board })
      : t("ticketMoveBoardSprintNotShared", { sprint: current.name, board })
    : t("ticketMoveBoardFromBacklog", { board });
  const subTicketCount: number = ticket.subTickets?.length ?? 0;

  let placementRows: React.ReactNode;
  if (sprintsFailed) {
    placementRows = (
      <InlineError
        message={t("ticketMoveBoardSprintsLoadFailed")}
        onRetry={onRetrySprints}
        className="mx-4"
      />
    );
  } else if (!sprints) {
    placementRows = (
      <View className="items-center py-4">
        <ActivityIndicator color={colors.mutedForeground} />
      </View>
    );
  } else {
    placementRows = [
      <ListRow
        key={BACKLOG_PLACEMENT}
        title={t("ticketBacklog")}
        accessory="radio"
        checked={placement === BACKLOG_PLACEMENT}
        onPress={() => onPlacement(BACKLOG_PLACEMENT)}
        disabled={disabled}
      />,
      ...open.map((sprint) => (
        <ListRow
          key={sprint.id}
          title={sprint.name}
          accessory="radio"
          checked={placement === sprint.id}
          onPress={() => onPlacement(sprint.id)}
          disabled={disabled}
        />
      )),
    ];
  }

  return (
    <>
      <Text className="px-6 pt-2 font-sans text-subhead text-muted-foreground">
        {t("ticketMoveBoardDescription")}
      </Text>

      <ListSection header={t("ticketMoveBoardPlacementLabel", { board })} footer={hint}>
        {placementRows}
      </ListSection>

      {subTicketCount > 0 ? (
        <ListSection header={t("ticketMoveBoardSubTicketsLabel", { count: subTicketCount })}>
          <ListRow
            title={t("ticketMoveBoardSubTicketsMove", { board })}
            subtitle={t("ticketMoveBoardSubTicketsMoveDesc", { prefix: target.prefix ?? "" })}
            wrap
            accessory="radio"
            checked={subTickets === "move"}
            onPress={() => onSubTickets("move")}
            disabled={disabled}
          />
          <ListRow
            title={t("ticketMoveBoardSubTicketsDetach", { board: ticket.board.name })}
            subtitle={t("ticketMoveBoardSubTicketsDetachDesc")}
            wrap
            accessory="radio"
            checked={subTickets === "detach"}
            onPress={() => onSubTickets("detach")}
            disabled={disabled}
          />
        </ListSection>
      ) : null}

      {changes.length > 0 ? (
        <ListSection header={t("ticketMoveBoardChangesLabel")}>
          {changes.map((change) => (
            <ListRow key={change.key} title={t(change.key, change.values)} wrap accessory="none" />
          ))}
        </ListSection>
      ) : null}
    </>
  );
}

type MoveConfirmProps = {
  label: string;
  onConfirm: () => void;
  disabled: boolean;
  loading: boolean;
};

/**
 * The move button of the placement step. iOS shows it full width under the
 * placement, with Back in the sheet header; Android has no header actions,
 * so it sits in the footer beside a ghost Back.
 */
function MoveConfirm({ label, onConfirm, disabled, loading }: MoveConfirmProps) {
  return (
    <Button
      block={!IS_ANDROID}
      className={IS_ANDROID ? "flex-1" : undefined}
      icon={ArrowRightLeft}
      disabled={disabled}
      loading={loading}
      onPress={onConfirm}
    >
      {label}
    </Button>
  );
}
