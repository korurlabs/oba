import * as Haptics from "expo-haptics";
import { useRouter } from "expo-router";
import {
  ArrowRightLeft,
  Check,
  CircleDot,
  Eye,
  Hourglass,
  Play,
  Trash2,
  type LucideIcon,
} from "lucide-react-native";
import * as React from "react";
import { Alert, type AccessibilityActionEvent, type AccessibilityActionInfo } from "react-native";

import { canEditBoardTickets, type TicketScope } from "@/components/boards/boardModel";
import { useBoardLinks } from "@/components/boards/boardNavigation";
import {
  closesIncident,
  columnOf,
  inBacklog,
  isStatusNoop,
  landingOrder,
  listHoldsScope,
  moveChoiceName,
  nextIdOf,
  nextSwipeStatus,
  placeBefore,
  placementChoices,
  statusChoices,
  statusLanding,
  swipeActionLabelKey,
  ticketBoardId,
  type MoveChoice,
  type StatusTarget,
} from "@/components/boards/ticketMoveModel";
import { chatShareTicketHref } from "@/components/chat/chatShare";
import { ActionSheet } from "@/components/kazan/ActionSheet";
import type { ActionSheetAction } from "@/components/kazan/actionSheetModel";
import { resolveStatus, type TicketStatus } from "@/components/kazan/ticketMetaModel";
import { useRoleGatedMenu } from "@/components/kazan/useRoleGatedMenu";
import { boardMoveToSprintHref } from "@/components/sprints/moveToSprintRoutes";
import { BACKLOG_PLACEMENT } from "@/components/ticket/newTicketModel";
import { forgetIncidentCloseDraft } from "@/components/ticket/incidentCloseStore";
import { assigneeIdsOf, isAssignee } from "@/components/ticket/ticketActionsModel";
import { ticketMenuActions } from "@/components/ticket/ticketMenu";
import { ticketMenuItems, type TicketMenuItem } from "@/components/ticket/ticketMenuModel";
import { ticketDeleteMessageKey } from "@/components/ticket/ticketModel";
import { priorityPicker, statusPicker } from "@/components/ticket/ticketPickers";
import { ticketSheetHref } from "@/components/ticket/ticketRoutes";
import { useIncidentCloseGuard } from "@/components/ticket/useIncidentCloseGuard";
import { useTicketLinks } from "@/components/ticket/useTicketLinks";
import { useTicketPicker } from "@/components/ticket/useTicketPicker";
import { useWriteFailure } from "@/components/ticket/useWriteFailure";
import { dismissToast, toast } from "@/components/ui/toast";
import { useMyBoardRoleState } from "@/hooks/queries/boards";
import type { TicketUpdateBody } from "@/hooks/queries/ticketActions";
import {
  useDeleteBoardTicket,
  useFetchCommentCount,
  useTicketMove,
  useUpdateBoardTicket,
  type TicketMove,
} from "@/hooks/queries/ticketMoves";
import { useMe } from "@/hooks/queries/users";
import { useOnlineWriteGuard } from "@/hooks/useOnlineWriteGuard";
import { useTranslation } from "@/i18n";
import { TICKET_COMMENTS_ENABLED } from "@/lib/features";
import type { BoardTicket, Sprint } from "@/lib/schemas/boards";

/** The mark of a swipe that moves a ticket on to this status. */
const SWIPE_STATUS_ICON: Record<TicketStatus, LucideIcon> = {
  TODO: CircleDot,
  IN_PROGRESS: Play,
  WAITING_FOR_THIRD_PARTY: Hourglass,
  IN_REVIEW: Eye,
  DONE: Check,
};
/** Where a ticket sits on the view: its sprint or the backlog, and the list that holds it. */
export interface TicketPlace {
  scope: TicketScope;
  rows: readonly BoardTicket[];
}

/** What a card or row needs to offer its moves. */
export interface CardMoveProps {
  accessibilityActions: AccessibilityActionInfo[];
  onAccessibilityAction: (event: AccessibilityActionEvent) => void;
}

/** A swipe action of a ticket row. */
export interface SwipeAction {
  label: string;
  icon: LucideIcon;
  tone: "success" | "info" | "destructive";
  onPress: () => void;
}

export interface BoardMoves {
  /** The user may move tickets on this board. */
  canMove: boolean;
  moveStatus: (ticket: BoardTicket, place: TicketPlace, target: StatusTarget) => void;
  place: (
    ticket: BoardTicket,
    from: TicketPlace,
    target: TicketScope,
    targetRows: readonly BoardTicket[] | null,
    beforeId: string | null
  ) => void;
  /**
   * Moves a ticket to another position in `rows`, the group of its sprint or
   * backlog as the list shows it, before `beforeId` (the end when null).
   */
  reorder: (
    ticket: BoardTicket,
    place: TicketPlace,
    rows: readonly BoardTicket[],
    beforeId: string | null
  ) => void;
  /** The screen-reader moves of a card, instead of a drag. */
  cardMoves: (ticket: BoardTicket, place: TicketPlace) => CardMoveProps | undefined;
  /** The swipe-right action of a row, or null when the ticket has no next status. */
  swipeNext: (ticket: BoardTicket, place: TicketPlace) => SwipeAction | null;
  /** The swipe-left actions of a row: Move (when it can move) and Delete. */
  swipeMore: (ticket: BoardTicket, place: TicketPlace) => SwipeAction[];
  /** The menu a held card opens. */
  openMenu: (ticket: BoardTicket, place: TicketPlace) => void;
  /** The action sheets of the menu; render once in the view. */
  sheets: React.ReactElement;
}

type BoardMovesInput = {
  boardId: string;
  sprints: readonly Sprint[];
  backlogId: string | null;
  canWrite: boolean;
  /**
   * Starts selection mode with a held card's ticket picked; left out where
   * the view offers no selection, such as the Kanban columns.
   */
  onSelect?: (ticket: BoardTicket, place: TicketPlace) => void;
};

type Sheet = { ticket: BoardTicket; place: TicketPlace } | null;

function haptic(kind: "success" | "warning"): void {
  Haptics.notificationAsync(
    kind === "success"
      ? Haptics.NotificationFeedbackType.Success
      : Haptics.NotificationFeedbackType.Warning
  ).catch((error: unknown) => {
    console.warn("[boards] haptic failed", error instanceof Error ? error.message : error);
  });
}

/**
 * The ticket moves of a board view: status moves (drag between columns, the
 * drop strip, a swipe, the menu, a screen-reader action), placement moves
 * between a sprint and the backlog, and delete. A move that sets an incident
 * to Done first asks for its root cause analysis and lessons learned. A move
 * lands at once with a toast offering Undo; a failure puts the board back and
 * shows the refusal, with Retry unless a rule refused it. Undo sends the
 * previous status, placement and order.
 */
export function useBoardMoves({
  boardId,
  sprints,
  backlogId,
  canWrite,
  onSelect,
}: BoardMovesInput): BoardMoves {
  const { t } = useTranslation();
  const router = useRouter();
  const { openTicket, openMoveToSprint } = useBoardLinks();
  const links = useTicketLinks();
  const { openChoice } = useTicketPicker();
  const me = useMe(canWrite).data;
  const mutation = useTicketMove(boardId);
  const update = useUpdateBoardTicket(boardId);
  const remove = useDeleteBoardTicket();
  const fetchCommentCount = useFetchCommentCount();
  const fail = useWriteFailure();
  const askIncidentClose = useIncidentCloseGuard();
  const [menu, setMenu] = React.useState<Sheet>(null);
  const closeMenu = React.useCallback(() => setMenu(null), []);
  const roleState = useMyBoardRoleState(boardId);
  // A held card's write actions follow `canWrite` and Select follows the
  // board role, so the menu opens once the role is read.
  const menuVisible: boolean = useRoleGatedMenu({
    requested: menu !== null,
    role: roleState,
    onCancel: closeMenu,
    logTag: "boardMoves",
  });
  const mutate = mutation.mutate;

  const online = useOnlineWriteGuard();

  const statusLabel = React.useCallback(
    (status: string): string => t(resolveStatus(status).labelKey),
    [t]
  );

  const placeLabel = React.useCallback(
    (scope: TicketScope): string =>
      "sprintId" in scope
        ? (sprints.find((sprint) => sprint.id === scope.sprintId)?.name ?? t("boardSprintPicker"))
        : t("boardBacklog"),
    [sprints, t]
  );

  /**
   * Sends a move with its feedback. `message` is the success toast; `undo` is
   * the move that restores the board, and without it the move is itself an
   * Undo and shows no toast. A move that sets an incident to Done, an Undo
   * included, first asks for its root cause analysis and lessons learned and
   * is sent with them; a cancel leaves the board as it was. A Retry sends
   * the texts already given.
   */
  const run = React.useCallback(
    (move: TicketMove, message: string, undo: TicketMove | null) => {
      if (!online()) return;
      if (!move.incidentClose && closesIncident(move)) {
        void askIncidentClose({ boardId: move.boardId, ticketId: move.ticket.id }).then(
          (incidentClose) => {
            if (incidentClose) run({ ...move, incidentClose }, message, undo);
          }
        );
        return;
      }
      const toastId: string | null = undo
        ? toast.success(message, {
            action: { label: t("boardUndo"), onPress: () => run(undo, message, null) },
          })
        : null;
      // The success toast brings its own haptic; an Undo shows no toast.
      if (!toastId) haptic("success");
      mutate(move, {
        onSuccess: () => {
          if (move.incidentClose) forgetIncidentCloseDraft(move.ticket.id);
        },
        onError: (error) => {
          if (toastId) dismissToast(toastId);
          haptic("warning");
          fail(error, move.kind === "place" ? "post" : "put", () => run(move, message, undo));
        },
      });
    },
    [askIncidentClose, fail, mutate, online, t]
  );

  const movedTo = React.useCallback(
    (ticket: BoardTicket, target: string): string => t("boardMovedTo", { key: ticket.key, target }),
    [t]
  );

  const moveStatus = React.useCallback(
    (ticket: BoardTicket, place: TicketPlace, target: StatusTarget) => {
      if (!canWrite || isStatusNoop(place.rows, ticket, target)) return;
      const ownBoard: string = ticketBoardId(ticket, boardId);
      const order: number | undefined = landingOrder(
        statusLanding(place.rows, ticket, target),
        ticket.id,
        boardId
      );
      const fromStatus: TicketStatus = resolveStatus(ticket.status).status;
      const move: TicketMove = { kind: "status", ticket, boardId: ownBoard, target, order };
      const moved: BoardTicket = {
        ...ticket,
        status: target.status,
        displayOrder: order ?? ticket.displayOrder,
      };
      const undo: TicketMove = {
        kind: "status",
        ticket: moved,
        boardId: ownBoard,
        target: {
          status: fromStatus,
          beforeId: nextIdOf(columnOf(place.rows, fromStatus), ticket.id),
        },
        order: order !== undefined && ticket.displayOrder != null ? ticket.displayOrder : undefined,
      };
      run(move, movedTo(ticket, statusLabel(target.status)), undo);
    },
    [boardId, canWrite, movedTo, run, statusLabel]
  );

  const reorder = React.useCallback(
    (
      ticket: BoardTicket,
      where: TicketPlace,
      rows: readonly BoardTicket[],
      beforeId: string | null
    ) => {
      if (!canWrite || beforeId === ticket.id || nextIdOf(rows, ticket.id) === beforeId) return;
      const order: number | undefined = landingOrder(
        placeBefore(rows, ticket, beforeId),
        ticket.id,
        boardId
      );
      // Without a new order the API keeps the ticket where it was, so the
      // list would jump back on the next read.
      if (order === undefined || ticket.displayOrder == null) return;
      const ownBoard: string = ticketBoardId(ticket, boardId);
      const status: TicketStatus = resolveStatus(ticket.status).status;
      const move: TicketMove = {
        kind: "status",
        ticket,
        boardId: ownBoard,
        target: { status, beforeId },
        order,
      };
      const undo: TicketMove = {
        kind: "status",
        ticket: { ...ticket, displayOrder: order },
        boardId: ownBoard,
        target: { status, beforeId: nextIdOf(rows, ticket.id) },
        order: ticket.displayOrder,
      };
      run(move, movedTo(ticket, placeLabel(where.scope)), undo);
    },
    [boardId, canWrite, movedTo, placeLabel, run]
  );

  const place = React.useCallback(
    (
      ticket: BoardTicket,
      from: TicketPlace,
      target: TicketScope,
      targetRows: readonly BoardTicket[] | null,
      beforeId: string | null
    ) => {
      if (!canWrite || listHoldsScope(scopeParams(from.scope), target)) return;
      const ownBoard: string = ticketBoardId(ticket, boardId);
      const order: number | undefined = targetRows
        ? landingOrder(placeBefore(targetRows, ticket, beforeId), ticket.id, boardId)
        : undefined;
      const move: TicketMove = {
        kind: "place",
        ticket,
        boardId: ownBoard,
        target,
        beforeId,
        order,
      };
      const placed: BoardTicket = { ...ticket, displayOrder: order ?? ticket.displayOrder };
      const moved: BoardTicket = "backlogId" in target ? inBacklog(placed) : placed;
      const undo: TicketMove = {
        kind: "place",
        ticket: moved,
        boardId: ownBoard,
        target: from.scope,
        beforeId: nextIdOf(from.rows, ticket.id),
        order: order !== undefined && ticket.displayOrder != null ? ticket.displayOrder : undefined,
        status: ticket.status,
      };
      run(move, movedTo(ticket, placeLabel(target)), undo);
    },
    [boardId, canWrite, movedTo, placeLabel, run]
  );

  /** Statuses are fixed for a top-level backlog ticket: the API keeps it Todo. */
  const statusMovable = React.useCallback(
    (ticket: BoardTicket, where: TicketPlace): boolean =>
      "sprintId" in where.scope || !!ticket.parentTicketId,
    []
  );

  const choicesFor = React.useCallback(
    (ticket: BoardTicket, where: TicketPlace): MoveChoice[] => [
      ...(statusMovable(ticket, where) ? statusChoices(ticket.status) : []),
      ...placementChoices(
        ticket,
        boardId,
        "sprintId" in where.scope ? where.scope.sprintId : null,
        backlogId,
        sprints,
        t("boardBacklog")
      ),
    ],
    [backlogId, boardId, sprints, statusMovable, t]
  );

  const choose = React.useCallback(
    (ticket: BoardTicket, where: TicketPlace, choice: MoveChoice) => {
      if (choice.kind === "status") {
        moveStatus(ticket, where, { status: choice.status, beforeId: null });
      } else {
        place(ticket, where, choice.scope, null, null);
      }
    },
    [moveStatus, place]
  );

  const cardMoves = React.useCallback(
    (ticket: BoardTicket, where: TicketPlace): CardMoveProps | undefined => {
      if (!canWrite) return undefined;
      const choices: MoveChoice[] = choicesFor(ticket, where);
      const label = (choice: MoveChoice): string =>
        t("boardMoveTo", {
          target: choice.kind === "status" ? statusLabel(choice.status) : choice.label,
        });
      return {
        accessibilityActions: choices.map((choice) => ({
          name: moveChoiceName(choice),
          label: label(choice),
        })),
        onAccessibilityAction: (event: AccessibilityActionEvent) => {
          const choice: MoveChoice | undefined = choices.find(
            (item) => moveChoiceName(item) === event.nativeEvent.actionName
          );
          if (choice) choose(ticket, where, choice);
        },
      };
    },
    [canWrite, choicesFor, choose, statusLabel, t]
  );

  const confirmDelete = React.useCallback(
    (ticket: BoardTicket) => {
      if (!online()) return;
      const target = { boardId: ticketBoardId(ticket, boardId), ticketId: ticket.id };
      // The comment count is read only while the app shows comments.
      const commentCount: Promise<number> = TICKET_COMMENTS_ENABLED
        ? fetchCommentCount(target)
        : Promise.resolve(0);
      commentCount
        .then((comments: number) => {
          Alert.alert(
            t("ticketDeleteTitle", { key: ticket.key }),
            t(ticketDeleteMessageKey(TICKET_COMMENTS_ENABLED), {
              subTickets: t("ticketDeleteSubTickets", { count: ticket.subTickets?.length ?? 0 }),
              comments: t("ticketDeleteComments", { count: comments }),
              board: ticket.board?.name ?? "",
            }),
            [
              { text: t("kitCancel"), style: "cancel" },
              {
                text: t("ticketDeleteConfirm"),
                style: "destructive",
                onPress: () =>
                  remove.mutate(target, {
                    onSuccess: () => toast.success(t("ticketDeleted", { key: ticket.key })),
                    onError: (error) => fail(error, "delete", () => confirmDelete(ticket)),
                  }),
              },
            ]
          );
        })
        .catch((error: unknown) => {
          console.warn(
            "[boards] comments for delete failed",
            error instanceof Error ? error.message : error
          );
          toast.error(t("boardTicketLoadError"), {
            action: { label: t("ticketRetry"), onPress: () => confirmDelete(ticket) },
          });
        });
    },
    [boardId, fail, fetchCommentCount, online, remove, t]
  );

  const swipeNext = React.useCallback(
    (ticket: BoardTicket, where: TicketPlace): SwipeAction | null => {
      if (!canWrite || !statusMovable(ticket, where)) return null;
      const next: TicketStatus | null = nextSwipeStatus(ticket.status);
      if (!next) return null;
      return {
        label: t(swipeActionLabelKey(next)),
        icon: SWIPE_STATUS_ICON[next],
        tone: "success",
        onPress: () => moveStatus(ticket, where, { status: next, beforeId: null }),
      };
    },
    [canWrite, moveStatus, statusMovable, t]
  );

  const swipeMore = React.useCallback(
    (ticket: BoardTicket, where: TicketPlace): SwipeAction[] => {
      if (!canWrite) return [];
      const actions: SwipeAction[] = [];
      const placements: MoveChoice[] = choicesFor(ticket, where).filter(
        (choice) => choice.kind === "place"
      );
      if (placements.length > 0) {
        actions.push({
          label: t("boardSwipeMove"),
          icon: ArrowRightLeft,
          tone: "info",
          onPress: () => openMoveToSprint(boardId, ticket.key),
        });
      }
      actions.push({
        label: t("boardSwipeDelete"),
        icon: Trash2,
        tone: "destructive",
        onPress: () => confirmDelete(ticket),
      });
      return actions;
    },
    [boardId, canWrite, choicesFor, confirmDelete, openMoveToSprint, t]
  );

  const openMenu = React.useCallback((ticket: BoardTicket, where: TicketPlace) => {
    setMenu({ ticket, place: where });
  }, []);

  /** Sends a field change of the menu's ticket; a failure is shown with Retry. */
  const write = React.useCallback(
    (
      ticket: BoardTicket,
      body: TicketUpdateBody,
      optimistic: (row: BoardTicket) => BoardTicket
    ) => {
      if (!online()) return;
      const target = { boardId: ticketBoardId(ticket, boardId), ticketId: ticket.id };
      update.mutate(
        { target, body, optimistic },
        { onError: (error) => fail(error, "put", () => write(ticket, body, optimistic)) }
      );
    },
    [boardId, fail, online, update]
  );

  /** The menu of a held card: the ticket menu with the board's own moves and pickers. */
  const boardMenuItems = (ticket: BoardTicket, where: TicketPlace): TicketMenuItem[] => {
    const inSprint: boolean = "sprintId" in where.scope;
    const canPlace: boolean = choicesFor(ticket, where).some((choice) => choice.kind === "place");
    return ticketMenuItems(
      {
        canWrite,
        canChangeStatus: statusMovable(ticket, where),
        isAssignee: me ? isAssignee(ticket, me.id) : null,
      },
      {
        open: () => openTicket(ticket.key),
        select:
          onSelect && canEditBoardTickets(roleState.role)
            ? () => onSelect(ticket, where)
            : undefined,
        changeStatus: () =>
          openChoice(
            statusPicker(t, ticket.status, (status: string) =>
              moveStatus(ticket, where, { status: resolveStatus(status).status, beforeId: null })
            )
          ),
        assignToMe: () => {
          if (!me) return;
          write(ticket, { assigneeIds: [...assigneeIdsOf(ticket), me.id] }, (row) => ({
            ...row,
            assignees: [
              ...(row.assignees ?? []),
              {
                id: me.id,
                userId: me.id,
                firstName: me.profile?.firstName ?? null,
                lastName: me.profile?.lastName ?? null,
                email: me.email ?? null,
              },
            ],
          }));
        },
        setPriority: () =>
          openChoice(
            priorityPicker(t, ticket.type, ticket.priority, (priority: string) =>
              write(ticket, { priority }, (row) => ({ ...row, priority }))
            )
          ),
        // A sub-ticket stays on its parent's board.
        moveToBoard: ticket.parentTicketId
          ? undefined
          : () => {
              if (online()) router.push(ticketSheetHref(ticket.key, "move-board"));
            },
        moveToSprint: canPlace ? () => openMoveToSprint(boardId, ticket.key) : undefined,
        moveToBacklog:
          inSprint && backlogId
            ? () =>
                router.push(
                  boardMoveToSprintHref(boardId, ticket.key, { sprintId: BACKLOG_PLACEMENT })
                )
            : undefined,
        copyLink: () => links.copyTicketLink(ticket.key),
        shareToChat: () => {
          if (online()) {
            router.push(
              chatShareTicketHref({ id: ticket.id, key: ticket.key, title: ticket.title })
            );
          }
        },
        delete: () => confirmDelete(ticket),
      }
    );
  };

  const menuActions: ActionSheetAction[] = menu
    ? ticketMenuActions(t, boardMenuItems(menu.ticket, menu.place))
    : [];

  const sheets = (
    <ActionSheet
      visible={menuVisible}
      onClose={closeMenu}
      title={menu ? `${menu.ticket.key} ${menu.ticket.title}` : undefined}
      actions={menuActions}
      cancelLabel={t("kitCancel")}
    />
  );

  return {
    canMove: canWrite,
    moveStatus,
    place,
    reorder,
    cardMoves,
    swipeNext,
    swipeMore,
    openMenu,
    sheets,
  };
}

/** The list params a scope reads, to compare a scope with another. */
function scopeParams(scope: TicketScope): Record<string, string> {
  return "sprintId" in scope ? { sprintId: scope.sprintId } : { backlogId: scope.backlogId };
}
