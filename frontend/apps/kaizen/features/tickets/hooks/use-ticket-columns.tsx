import { type ColumnDef, type Row } from "@tanstack/react-table";
import { useMemo } from "react";
import { type TicketListResponse } from "../schemas";
import { PRIORITY_ORDER } from "../lib/ticket-priority-order";
import { useTranslations } from "next-intl";
import { useDateFormat } from "@oba/shared/i18n/use-date-format";
import { TicketTypeBadge } from "../components/ticket-type-badge";
import { getPriorityLabel, TicketPriorityIcon } from "../components/ticket-priority-icon";
import { TicketStatusIcon } from "../components/ticket-status-icon";
import { UserAvatar, UserMultipleAvatar } from "@/components/ui/user-avatar";
import { LabelBadgeList } from "@/features/labels/components/label-badge";
import { DueDateBadge } from "../components/due-date-badge";
import { ProjectBadge } from "@/features/projects/components/project-badge";
import { TicketActionsMenu } from "../components/ticket-actions-menu";
import { SystemActorBadge } from "../components/system-actor-badge";
import { SubTicketProgressBar } from "../components/sub-ticket-progress-bar";
import type { SubTicketMoveChoice } from "../components/sub-ticket-choice";
import { subTicketProgress } from "@/lib/utils/ticket-groups";
import { Settings } from "lucide-react";

// ── Options ────────────────────────────────────────────────────────

interface UseTicketColumnsOptions {
  boardId?: string;
  /** `subTickets` is set for a ticket with sub-tickets: which of them the user chose to move along. */
  onMoveToSprint?: (ticketId: string, sprintId: string, subTickets?: SubTicketMoveChoice) => void;
  onMoveToBacklog?: (ticketId: string, subTickets?: SubTicketMoveChoice) => void;
  onDelete?: (ticketId: string) => void;
  onEdit?: (ticket: TicketListResponse) => void;
  /**
   * Copy a shareable deep-link to the ticket. When provided, the actions
   * column is rendered even if no other row actions are available — the
   * copy-link option is universally useful.
   */
  onCopyLink?: (ticket: TicketListResponse) => void;
  sprints?: Array<{ id: string; name: string; status: string }>;
  currentSprintId?: string;
  onBulkAction?: () => void;
}

// ── Hook ───────────────────────────────────────────────────────────

export const useTicketColumns = (options?: UseTicketColumnsOptions) => {
  const t = useTranslations();
  const { formatDate } = useDateFormat();

  const hasActions = !!(
    options?.onDelete ||
    options?.onMoveToSprint ||
    options?.onMoveToBacklog ||
    options?.onCopyLink
  );

  return useMemo<ColumnDef<TicketListResponse, unknown>[]>(
    () => [
      {
        accessorKey: "key",
        header: t("ticket.table.key"),
        cell: ({ row }) => (
          <span className="text-foreground text-xs font-medium whitespace-nowrap">
            {row.original.key}
          </span>
        ),
        sortingFn: "alphanumeric",
      },
      {
        accessorKey: "type",
        header: t("ticket.table.type"),
        cell: ({ row }) => <TicketTypeBadge type={row.original.type} />,
        sortingFn: "alphanumeric",
      },
      {
        accessorKey: "title",
        header: t("ticket.table.title"),
        cell: ({ row }) => (
          <span className="text-foreground block truncate text-sm">{row.original.title}</span>
        ),
        sortingFn: "text",
      },
      {
        id: "board",
        accessorFn: (row) => row.board?.prefix ?? "",
        header: t("ticket.table.board"),
        cell: ({ row }) => (
          <span className="text-muted-foreground text-xs whitespace-nowrap">
            {row.original.board?.prefix}
          </span>
        ),
        meta: { flex: true },
        sortingFn: "alphanumeric",
      },
      {
        id: "project",
        accessorFn: (row) => row.project?.name ?? "",
        header: t("ticket.table.project"),
        cell: ({ row }) => {
          const project = row.original.project;
          if (!project) return <span className="text-muted-foreground text-xs">—</span>;
          return <ProjectBadge project={project} size="md" />;
        },
        sortingFn: "alphanumeric",
      },
      {
        id: "labels",
        header: t("ticket.table.labels"),
        cell: ({ row }) => {
          const labels = row.original.labels;
          if (!labels?.length) return <span className="text-muted-foreground text-xs">—</span>;
          return <LabelBadgeList labels={labels} maxVisible={3} size="sm" />;
        },
        enableSorting: false,
      },
      {
        accessorKey: "priority",
        header: t("ticket.table.priority"),
        cell: ({ row }) => (
          <div className="flex items-center gap-1.5 whitespace-nowrap">
            <TicketPriorityIcon priority={row.original.priority} size={14} />
            <span className="text-muted-foreground text-xs">
              {getPriorityLabel(row.original.priority)}
            </span>
          </div>
        ),
        sortingFn: (rowA, rowB) => {
          const a = PRIORITY_ORDER[rowA.original.priority] ?? 2;
          const b = PRIORITY_ORDER[rowB.original.priority] ?? 2;
          return a - b;
        },
      },
      {
        id: "assignee",
        header: t("ticket.table.assignee"),
        cell: ({ row }) => {
          if (row.original.assignees?.length === 0) {
            return (
              <span className="text-muted-foreground text-xs whitespace-nowrap">
                {t("common.unassigned")}
              </span>
            );
          }
          return <UserMultipleAvatar users={row.original.assignees ?? []} size="xs" />;
        },
        sortingFn: "alphanumeric",
      },
      {
        id: "reviewer",
        header: t("ticket.table.reviewer"),
        cell: ({ row }) => {
          if (row.original.reviewers?.length === 0) {
            return (
              <span className="text-muted-foreground text-xs whitespace-nowrap">
                {t("common.unassigned")}
              </span>
            );
          }
          return <UserMultipleAvatar users={row.original.reviewers ?? []} size="xs" />;
        },
        sortingFn: "alphanumeric",
      },
      {
        accessorKey: "status",
        header: t("ticket.table.status"),
        cell: ({ row }) => (
          <div className="flex items-center gap-2 whitespace-nowrap">
            <TicketStatusIcon status={row.original.status} />
            <span className="text-muted-foreground text-xs">
              {t(`ticket.status.${row.original.status}`)}
            </span>
          </div>
        ),
        sortingFn: "alphanumeric",
      },
      {
        id: "progress",
        header: t("ticket.table.progress"),
        // A parent's sub-tickets can be planned in other sprints or the
        // backlog, so the bar splits the unfinished ones by placement. It
        // counts every sub-ticket, also those a sprint list does not nest.
        cell: ({ row }) => {
          const subTickets = row.original.subTickets ?? [];
          if (subTickets.length === 0) return null;
          const breakdown = subTicketProgress(subTickets, options?.currentSprintId);
          return (
            <div className="w-24">
              <SubTicketProgressBar
                completed={breakdown.done}
                total={breakdown.total}
                breakdown={breakdown}
              />
            </div>
          );
        },
        enableSorting: false,
      },
      {
        accessorKey: "dueDate",
        header: t("ticket.table.dueDate"),
        cell: ({ row }) => <DueDateBadge dueDate={row.original.dueDate} />,
        sortingFn: (rowA, rowB) => {
          const a = rowA.original.dueDate;
          const b = rowB.original.dueDate;
          if (!a && !b) return 0;
          if (!a) return 1;
          if (!b) return -1;
          return a.localeCompare(b);
        },
      },
      {
        accessorKey: "createdAt",
        header: t("ticket.table.createdAt"),
        cell: ({ row }) => {
          return (
            <div className="flex items-center gap-1.5">
              <span className="text-muted-foreground text-xs whitespace-nowrap">
                {formatDate(row.original.createdAt)}
              </span>
              {row.original.createdBySystemActor ? (
                <SystemActorBadge actor={row.original.createdBySystemActor} />
              ) : (
                row.original.createdBy && (
                  <UserAvatar showName={false} user={row.original.createdBy} size="xs" />
                )
              )}
            </div>
          );
        },
        sortingFn: (rowA, rowB) => {
          return rowA.original.createdAt.localeCompare(rowB.original.createdAt);
        },
      },
      // Actions column -- only when at least one action callback is provided
      ...(hasActions
        ? [
          {
            id: "actions",
            header: () => (
              <div className="flex items-center gap-1">
                <span>{t("common.actions")}</span>
                {options?.onBulkAction && (
                  <button
                    type="button"
                    onClick={(e) => {
                      e.stopPropagation();
                      options.onBulkAction!();
                    }}
                    className="hover:bg-muted ml-1 rounded p-0.5"
                    title={t("ticket.bulkMove.title")}
                  >
                    <Settings className="text-muted-foreground h-3.5 w-3.5" />
                  </button>
                )}
              </div>
            ),
            enableSorting: false,
            enableHiding: false,
            enableResizing: false,
            meta: { align: "center" },
            minSize: 100,
            cell: ({ row }: { row: Row<TicketListResponse> }) => (
              <div onClick={(e) => e.stopPropagation()}>
                <TicketActionsMenu
                  ticket={row.original}
                  boardId={options?.boardId}
                  onEdit={options?.onEdit ? () => options.onEdit!(row.original) : undefined}
                  onCopyLink={options?.onCopyLink}
                  onDelete={
                    options?.onDelete ? () => options.onDelete!(row.original.id) : undefined
                  }
                  onMoveToSprint={
                    options?.onMoveToSprint
                      ? (sprintId: string, subTickets?: SubTicketMoveChoice) =>
                        options.onMoveToSprint!(row.original.id, sprintId, subTickets)
                      : undefined
                  }
                  onMoveToBacklog={
                    options?.onMoveToBacklog
                      ? (subTickets?: SubTicketMoveChoice) =>
                        options.onMoveToBacklog!(row.original.id, subTickets)
                      : undefined
                  }
                  sprints={options?.sprints}
                  currentSprintId={options?.currentSprintId}
                />
              </div>
            ),
          } satisfies ColumnDef<TicketListResponse, unknown>,
        ]
        : []),
    ],
    [t, formatDate, hasActions, options?.boardId, options?.onMoveToSprint, options?.onMoveToBacklog, options?.onDelete, options?.onEdit, options?.onCopyLink, options?.sprints, options?.currentSprintId, options?.onBulkAction]
  );
};
