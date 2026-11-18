import { useRouter } from "expo-router";
import * as React from "react";
import { Alert } from "react-native";

import { chatShareTicketHref } from "@/components/chat/chatShare";
import type { ActionSheetAction } from "@/components/kazan/actionSheetModel";
import {
  ticketMoveToSprintHref,
  type MoveToSprintPreset,
} from "@/components/sprints/moveToSprintRoutes";
import { dueDateInstant, dueDayOf } from "@/components/ticket/dueDateModel";
import {
  needsIncidentCloseTexts,
  type IncidentCloseValues,
} from "@/components/ticket/incidentCloseModel";
import { forgetIncidentCloseDraft } from "@/components/ticket/incidentCloseStore";
import { BACKLOG_PLACEMENT } from "@/components/ticket/newTicketModel";
import {
  assigneeIdsOf,
  canChangeStatus,
  isAssignee,
  typeChange,
  unfinishedSubTicketCount,
  type TypeChange,
} from "@/components/ticket/ticketActionsModel";
import {
  currentPlacement,
  milestoneFor,
  projectFor,
  type SprintChoice,
  type TicketField,
} from "@/components/ticket/ticketFieldsModel";
import { ticketMenuActions } from "@/components/ticket/ticketMenu";
import { ticketMenuItems } from "@/components/ticket/ticketMenuModel";
import { ticketDeleteMessageKey } from "@/components/ticket/ticketModel";
import {
  milestonePicker,
  priorityPicker,
  projectPicker,
  rankPicker,
  sprintPicker,
  statusPicker,
  typePicker,
  type TicketPicker,
} from "@/components/ticket/ticketPickers";
import { ticketSheetHref, type TicketSheet } from "@/components/ticket/ticketRoutes";
import { useDueDatePicker } from "@/components/ticket/useDueDatePicker";
import { useIncidentCloseGuard } from "@/components/ticket/useIncidentCloseGuard";
import { useTicketLinks } from "@/components/ticket/useTicketLinks";
import { useTicketPicker } from "@/components/ticket/useTicketPicker";
import { useWriteFailure } from "@/components/ticket/useWriteFailure";
import { toast } from "@/components/ui/toast";
import { useBoardMilestones, useBoardProjects, useBoardSprints } from "@/hooks/queries/boards";
import {
  useDeleteTicket,
  useMoveTicketToBacklog,
  useMoveTicketToSprint,
  useUpdateTicket,
  type SubTicketMoveMode,
  type TicketUpdate,
} from "@/hooks/queries/ticketActions";
import { useComments, type TicketRef } from "@/hooks/queries/ticketDetail";
import { useMe } from "@/hooks/queries/users";
import { useOnlineWriteGuard } from "@/hooks/useOnlineWriteGuard";
import { useTranslation } from "@/i18n";
import { TICKET_COMMENTS_ENABLED } from "@/lib/features";
import type { BoardOption, Milestone, Sprint } from "@/lib/schemas/boards";
import type { TicketDetail } from "@/lib/schemas/tickets";

type TicketActionsInput = {
  ticketKey: string;
  ticketRef: TicketRef | undefined;
  ticket: TicketDetail | undefined;
  /** Whether the user may change the ticket: a board member with write rights. */
  canWrite: boolean;
  /** Leaves the screen after the ticket is deleted. */
  onDeleted: () => void;
};

/**
 * The actions of the ticket screen: the Ticket actions menu, the type,
 * status, priority, sprint, milestone and project pickers, the due-date picker, the
 * mission rank, assignee, label, description, board and Move to sprint
 * sheets, and delete
 * with its confirmation. A sub-ticket stays on its parent's board, so it is
 * never offered Move to board. Without write rights only Copy link, Share to chat
 * and Open in Kazan web are offered and no control opens a picker. A write
 * made offline is refused with a toast before it is sent; a failed write is
 * toasted with Retry.
 *
 * The board's sprints, milestones and projects are read only for a user who
 * may write. A picker whose list has not loaded reads it when tapped and
 * opens once it arrives.
 */
export function useTicketActions({
  ticketKey,
  ticketRef,
  ticket,
  canWrite,
  onDeleted,
}: TicketActionsInput) {
  const { t } = useTranslation();
  const router = useRouter();
  const links = useTicketLinks();
  const fail = useWriteFailure();
  const askIncidentClose = useIncidentCloseGuard();
  const me = useMe(true).data;
  const comments = useComments(ticketRef);
  const update = useUpdateTicket(ticketRef);
  const moveToBacklog = useMoveTicketToBacklog(ticketRef);
  const moveToSprint = useMoveTicketToSprint(ticketRef);
  const remove = useDeleteTicket(ticketRef, ticketKey);
  const optionsBoardId: string | undefined = canWrite ? ticketRef?.boardId : undefined;
  const sprints = useBoardSprints(optionsBoardId);
  const milestones = useBoardMilestones(optionsBoardId);
  const projects = useBoardProjects(optionsBoardId);
  const { picker, setPicker, closePicker, withOptions, openChoice } = useTicketPicker();
  // A question a picker's choice leads to; it needs its own sheet, because
  // the picker's sheet is still closing when the choice runs.
  const [followUp, setFollowUp] = React.useState<TicketPicker | null>(null);

  const online = useOnlineWriteGuard();

  const write = React.useCallback(
    (change: TicketUpdate) => {
      if (!online()) return;
      update.mutate(change, { onError: (error) => fail(error, "put", () => write(change)) });
    },
    [fail, online, update]
  );

  const openSheet = React.useCallback(
    (sheet: TicketSheet) => {
      if (online()) router.push(ticketSheetHref(ticketKey, sheet));
    },
    [online, router, ticketKey]
  );

  const openMoveToSprint = React.useCallback(
    (preset: MoveToSprintPreset = {}) => {
      if (online()) router.push(ticketMoveToSprintHref(ticketKey, preset));
    },
    [online, router, ticketKey]
  );

  /** Sets an incident to Done with the texts the API requires for it. */
  const closeIncident = React.useCallback(
    (status: string, texts: IncidentCloseValues) => {
      if (!online() || !ticketRef) return;
      update.mutate(
        { body: { status, ...texts }, optimistic: (current) => ({ ...current, status, ...texts }) },
        {
          onSuccess: () => forgetIncidentCloseDraft(ticketRef.ticketId),
          onError: (error) => fail(error, "put", () => closeIncident(status, texts)),
        }
      );
    },
    [fail, online, ticketRef, update]
  );

  // A backlog ticket's status picker lists the other statuses disabled, with the reason.
  // Setting an incident without its root cause analysis or lessons learned to
  // Done asks for both first; a cancel keeps the status it had.
  const changeStatus = React.useCallback(() => {
    if (!ticket) return;
    openChoice(
      statusPicker(
        t,
        ticket.status,
        (status) => {
          if (ticketRef && needsIncidentCloseTexts(ticket.type, ticket.status, status, ticket)) {
            void askIncidentClose(ticketRef).then((texts) => {
              if (texts) closeIncident(status, texts);
            });
            return;
          }
          write({ body: { status }, optimistic: (current) => ({ ...current, status }) });
        },
        !canChangeStatus(ticket)
      )
    );
  }, [askIncidentClose, closeIncident, openChoice, t, ticket, ticketRef, write]);

  const changePriority = React.useCallback(() => {
    if (!ticket) return;
    openChoice(
      priorityPicker(t, ticket.type, ticket.priority, (priority) =>
        write({ body: { priority }, optimistic: (current) => ({ ...current, priority }) })
      )
    );
  }, [openChoice, t, ticket, write]);

  // A priority the new type does not allow is replaced in the same write.
  const changeType = React.useCallback(() => {
    if (!ticket) return;
    openChoice(
      typePicker(t, ticket.type, (type) => {
        const change: TypeChange = typeChange(type, ticket.priority);
        write({ body: change, optimistic: (current) => ({ ...current, ...change }) });
      })
    );
  }, [openChoice, t, ticket, write]);

  const changeRank = React.useCallback(() => {
    if (!ticket) return;
    openChoice(
      rankPicker(t, ticket.missionRank ?? null, (missionRank) =>
        write({ body: { missionRank }, optimistic: (current) => ({ ...current, missionRank }) })
      )
    );
  }, [openChoice, t, ticket, write]);

  const assignToMe = React.useCallback(() => {
    if (!ticket || !me) return;
    const assigneeIds: string[] = [...assigneeIdsOf(ticket), me.id];
    write({
      body: { assigneeIds },
      optimistic: (current) => ({
        ...current,
        assignees: [
          ...(current.assignees ?? []),
          {
            id: me.id,
            userId: me.id,
            firstName: me.profile?.firstName ?? null,
            lastName: me.profile?.lastName ?? null,
            email: me.email ?? null,
          },
        ],
      }),
    });
  }, [me, ticket, write]);

  const runMoveToBacklog = React.useCallback(
    (mode: SubTicketMoveMode) => {
      if (!online()) return;
      moveToBacklog.mutate(mode, {
        onSuccess: () => toast.success(t("ticketMovedToBacklog", { key: ticketKey })),
        onError: (error) => fail(error, "post", () => runMoveToBacklog(mode)),
      });
    },
    [fail, moveToBacklog, online, t, ticketKey]
  );

  /**
   * A parent asks which of its unfinished sub-tickets come along; the rest
   * move at once. Picking sub-tickets one by one opens Move to sprint on
   * `placementId`, the sprint or backlog already chosen.
   */
  const askSubTicketMode = React.useCallback(
    (run: (mode: SubTicketMoveMode) => void, placementId: string) => {
      if (!ticket) return;
      if (unfinishedSubTicketCount(ticket) === 0) {
        run("UNFINISHED");
        return;
      }
      setFollowUp({
        title: t("ticketMoveSubTicketsTitle"),
        actions: [
          { label: t("ticketMoveOnlyThis"), onPress: () => run("NONE") },
          { label: t("ticketMoveWithUnfinished"), onPress: () => run("UNFINISHED") },
          {
            label: t("moveSprintPicked"),
            onPress: () => openMoveToSprint({ sprintId: placementId, pickSubTickets: true }),
          },
        ],
      });
    },
    [openMoveToSprint, t, ticket]
  );

  const askMoveToBacklog = React.useCallback(
    () => askSubTicketMode(runMoveToBacklog, BACKLOG_PLACEMENT),
    [askSubTicketMode, runMoveToBacklog]
  );

  const runMoveToSprint = React.useCallback(
    (sprint: Sprint, mode: SubTicketMoveMode) => {
      if (!online()) return;
      moveToSprint.mutate(
        { sprintId: sprint.id, mode },
        {
          onSuccess: () =>
            toast.success(t("ticketMovedToSprint", { key: ticketKey, sprint: sprint.name })),
          onError: (error) => fail(error, "post", () => runMoveToSprint(sprint, mode)),
        }
      );
    },
    [fail, moveToSprint, online, t, ticketKey]
  );

  const changeSprint = React.useCallback(() => {
    if (!ticket) return;
    withOptions(sprints, (list: Sprint[]) => {
      setPicker(
        sprintPicker(t, list, currentPlacement(ticket), (choice: SprintChoice) => {
          const sprint: Sprint | null = choice.sprint;
          if (choice.id === BACKLOG_PLACEMENT || !sprint) askMoveToBacklog();
          else askSubTicketMode((mode) => runMoveToSprint(sprint, mode), sprint.id);
        })
      );
    });
  }, [
    askMoveToBacklog,
    askSubTicketMode,
    runMoveToSprint,
    setPicker,
    sprints,
    t,
    ticket,
    withOptions,
  ]);

  const changeMilestone = React.useCallback(() => {
    if (!ticket) return;
    withOptions(milestones, (list: Milestone[]) => {
      const next: TicketPicker | null = milestonePicker(
        t,
        list,
        ticket.milestone?.id ?? null,
        (milestone) =>
          write({
            body: { milestoneId: milestone.id },
            optimistic: (detail) => ({ ...detail, milestone: milestoneFor(milestone.id, list) }),
          }),
        () =>
          write({
            body: { clearMilestone: true },
            optimistic: (detail) => ({ ...detail, milestone: null }),
          })
      );
      if (next) setPicker(next);
      else toast.info(t("ticketMilestonesEmpty"));
    });
  }, [milestones, setPicker, t, ticket, withOptions, write]);

  // The API cannot take a project off a ticket, so the picker only switches it.
  const changeProject = React.useCallback(() => {
    if (!ticket) return;
    withOptions(projects, (list: BoardOption[]) => {
      const next: TicketPicker | null = projectPicker(
        t,
        list,
        ticket.project?.id ?? null,
        (project) =>
          write({
            body: { projectId: project.id },
            optimistic: (detail) => ({ ...detail, project: projectFor(project.id, list) }),
          })
      );
      if (next) setPicker(next);
      else toast.info(t("ticketProjectsEmpty"));
    });
  }, [projects, setPicker, t, ticket, withOptions, write]);

  const pickDueDay = React.useCallback(
    (day: string) => {
      const dueDate: string | null = dueDateInstant(day);
      if (!dueDate || !ticket || dueDayOf(ticket) === day) return;
      write({ body: { dueDate }, optimistic: (detail) => ({ ...detail, dueDate }) });
    },
    [ticket, write]
  );

  // The API cannot take a due date off a ticket, so the picker offers no way to clear it.
  const duePicker = useDueDatePicker({
    value: ticket ? dueDayOf(ticket) : null,
    title: t("ticketFieldDueDate"),
    onPick: pickDueDay,
  });
  const openDuePicker: () => void = duePicker.open;

  const runDelete = React.useCallback(() => {
    if (!online()) return;
    remove.mutate(undefined, {
      onSuccess: () => {
        onDeleted();
        toast.success(t("ticketDeleted", { key: ticketKey }));
      },
      onError: (error) => fail(error, "delete", runDelete),
    });
  }, [fail, onDeleted, online, remove, t, ticketKey]);

  const confirmDelete = React.useCallback(() => {
    if (!ticket) return;
    const subTickets: number = ticket.subTickets?.length ?? 0;
    const commentCount: number = comments.data?.length ?? 0;
    Alert.alert(
      t("ticketDeleteTitle", { key: ticketKey }),
      t(ticketDeleteMessageKey(TICKET_COMMENTS_ENABLED), {
        subTickets: t("ticketDeleteSubTickets", { count: subTickets }),
        comments: t("ticketDeleteComments", { count: commentCount }),
        board: ticket.board.name,
      }),
      [
        { text: t("kitCancel"), style: "cancel" },
        { text: t("ticketDeleteConfirm"), style: "destructive", onPress: runDelete },
      ]
    );
  }, [comments.data, runDelete, t, ticket, ticketKey]);

  const writable: boolean = canWrite && !!ticket;
  // A sub-ticket stays on its parent's board, so it cannot be moved to another one.
  const onMoveToBoard: (() => void) | undefined =
    writable && !ticket?.parentTicketId ? () => openSheet("move-board") : undefined;

  const menuActions: ActionSheetAction[] = ticketMenuActions(
    t,
    ticketMenuItems(
      {
        canWrite: writable,
        canChangeStatus: !!ticket && canChangeStatus(ticket),
        isAssignee: ticket && me ? isAssignee(ticket, me.id) : null,
      },
      {
        changeStatus,
        assignToMe,
        setPriority: changePriority,
        moveToBoard: onMoveToBoard,
        moveToSprint: () => openMoveToSprint(),
        moveToBacklog: ticket?.sprint ? askMoveToBacklog : undefined,
        copyLink: () => links.copyTicketLink(ticketKey),
        shareToChat: ticket
          ? () => {
              if (online()) {
                router.push(
                  chatShareTicketHref({ id: ticket.id, key: ticket.key, title: ticket.title })
                );
              }
            }
          : undefined,
        openWeb: () => links.openTicketOnWeb(ticketKey),
        delete: confirmDelete,
      }
    )
  );

  const onEditField = React.useCallback(
    (field: TicketField) => {
      if (field === "assignees") openSheet("assignees");
      else if (field === "labels") openSheet("labels");
      else if (field === "sprint") changeSprint();
      else if (field === "milestone") changeMilestone();
      else if (field === "project") changeProject();
      else if (field === "dueDate") openDuePicker();
    },
    [changeMilestone, changeProject, changeSprint, openDuePicker, openSheet]
  );

  return {
    menuActions,
    picker,
    closePicker,
    followUp,
    closeFollowUp: () => setFollowUp(null),
    dueCalendar: writable ? duePicker.calendar : null,
    onEditDescription: writable ? () => openSheet("description") : undefined,
    onChangeStatus: writable && ticket ? changeStatus : undefined,
    onChangePriority: writable ? changePriority : undefined,
    onChangeType: writable ? changeType : undefined,
    onOpenSizing: writable ? changeRank : undefined,
    onMoveToBoard,
    onEditField: writable ? onEditField : undefined,
    editableFields: writable ? EDITABLE_FIELDS : NO_FIELDS,
  };
}

/** The fields whose picker the app has: every field but the reporter. */
const EDITABLE_FIELDS: readonly TicketField[] = [
  "assignees",
  "labels",
  "sprint",
  "milestone",
  "project",
  "dueDate",
];
const NO_FIELDS: readonly TicketField[] = [];
