"use client";

import { useMemo, useState } from "react";
import { useTranslations } from "next-intl";
import type { ColumnDef } from "@tanstack/react-table";
import { Ban, CircleCheck, KeyRound, MoreVertical, Pencil, Trash2, Workflow } from "lucide-react";
import { Badge } from "@oba/ui/sky/badge";
import { Button } from "@oba/ui/sky/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@oba/ui/sky/dropdown-menu";
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@oba/ui/sky/dialog";
import { useDateFormat } from "@oba/shared/i18n/use-date-format";
import { AppBadges } from "@/features/organizations/components/app-badges";
import type { Agent, AgentStatusUpdate } from "../schemas";
import { AgentFireStatusBadge, AgentStatusBadge } from "../components/agent-badges";

interface AgentColumnsOptions {
  onEdit: (agent: Agent) => void;
  onManageToken: (agent: Agent) => void;
  onManageRoutine: (agent: Agent) => void;
  onStatusChange: (agentId: string, status: AgentStatusUpdate) => void;
  onDelete: (agentId: string) => void;
  isUpdatingStatus: boolean;
  isDeleting: boolean;
}

export function useAgentColumns({
  onEdit,
  onManageToken,
  onManageRoutine,
  onStatusChange,
  onDelete,
  isUpdatingStatus,
  isDeleting,
}: AgentColumnsOptions) {
  const t = useTranslations("agents");
  const { formatDateTime } = useDateFormat();

  return useMemo<ColumnDef<Agent, unknown>[]>(
    () => [
      {
        id: "name",
        accessorKey: "name",
        header: t("columns.name"),
        cell: ({ row }) => (
          <span className="text-foreground block max-w-[200px] truncate text-sm font-medium">
            {row.original.name}
          </span>
        ),
      },
      {
        id: "role",
        accessorKey: "role",
        header: t("columns.role"),
        cell: ({ row }) => (
          <span className="text-sm whitespace-nowrap">{t(`roles.${row.original.role}`)}</span>
        ),
      },
      {
        id: "flow",
        accessorKey: "flow",
        header: t("columns.flow"),
        cell: ({ row }) => <Badge variant="outline">{t(`flows.${row.original.flow}`)}</Badge>,
      },
      {
        id: "provider",
        accessorKey: "provider",
        header: t("columns.provider"),
        cell: ({ row }) => (
          <span className="text-sm whitespace-nowrap">
            {t(`providers.${row.original.provider}`)}
          </span>
        ),
      },
      {
        id: "apps",
        accessorKey: "apps",
        header: t("columns.apps"),
        cell: ({ row }) => <AppBadges apps={row.original.apps} emptyLabel={t("noApps")} />,
      },
      {
        id: "status",
        accessorKey: "status",
        header: t("columns.status"),
        cell: ({ row }) => <AgentStatusBadge status={row.original.status} />,
      },
      {
        id: "token",
        header: t("columns.token"),
        meta: { flex: true },
        cell: ({ row }) => {
          const agent = row.original;
          // Oba runs an API agent itself, so it has neither a token nor a routine.
          if (agent.flow === "API") {
            return <span className="text-muted-foreground text-sm">{t("apiFlowNote")}</span>;
          }
          if (!agent.tokenPrefix) {
            return <span className="text-muted-foreground text-sm">{t("token.none")}</span>;
          }
          return (
            <div className="flex flex-col gap-0.5">
              <span className="font-mono text-sm">{agent.tokenPrefix}…</span>
              <span className="text-muted-foreground text-xs">
                {agent.tokenLastUsedAt
                  ? t("token.lastUsed", { date: formatDateTime(agent.tokenLastUsedAt) })
                  : t("token.neverUsed")}
              </span>
            </div>
          );
        },
      },
      {
        id: "routine",
        header: t("columns.routine"),
        cell: ({ row }) => {
          const agent = row.original;
          if (agent.flow === "API") return null;
          if (!agent.routineId) {
            return <span className="text-muted-foreground text-sm">{t("routine.none")}</span>;
          }
          return (
            <div className="flex flex-col items-start gap-1">
              <span className="block max-w-[180px] truncate font-mono text-sm">
                {agent.routineId}
              </span>
              {agent.lastFireStatus ? (
                <AgentFireStatusBadge status={agent.lastFireStatus} />
              ) : (
                <span className="text-muted-foreground text-xs">{t("routine.neverFired")}</span>
              )}
            </div>
          );
        },
      },
      {
        id: "actions",
        header: "",
        size: 50,
        cell: ({ row }) => (
          <AgentActions
            agent={row.original}
            onEdit={onEdit}
            onManageToken={onManageToken}
            onManageRoutine={onManageRoutine}
            onStatusChange={onStatusChange}
            onDelete={onDelete}
            isUpdatingStatus={isUpdatingStatus}
            isDeleting={isDeleting}
          />
        ),
      },
    ],
    [
      t,
      formatDateTime,
      onEdit,
      onManageToken,
      onManageRoutine,
      onStatusChange,
      onDelete,
      isUpdatingStatus,
      isDeleting,
    ]
  );
}

/**
 * Row menu of an agent. Token and routine are offered only for a ROUTINE
 * agent: Oba runs an API agent itself and the API refuses both for it.
 */
function AgentActions({
  agent,
  onEdit,
  onManageToken,
  onManageRoutine,
  onStatusChange,
  onDelete,
  isUpdatingStatus,
  isDeleting,
}: { agent: Agent } & AgentColumnsOptions) {
  const t = useTranslations("agents");
  const [confirmOpen, setConfirmOpen] = useState(false);
  const isRoutine = agent.flow === "ROUTINE";
  const isDisabled = agent.status === "DISABLED";

  // The confirmation sits beside the menu, not inside it: the menu unmounts
  // its items when it closes, which would take an open dialog with it.
  return (
    <>
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button variant="ghost" size="icon-sm" onClick={(e) => e.stopPropagation()}>
            <MoreVertical aria-hidden="true" />
            <span className="sr-only">{t("actions.menu")}</span>
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          <DropdownMenuItem onSelect={() => onEdit(agent)}>
            <Pencil aria-hidden="true" />
            {t("actions.edit")}
          </DropdownMenuItem>
          {isRoutine && (
            <>
              <DropdownMenuItem onSelect={() => onManageToken(agent)}>
                <KeyRound aria-hidden="true" />
                {t("actions.token")}
              </DropdownMenuItem>
              <DropdownMenuItem onSelect={() => onManageRoutine(agent)}>
                <Workflow aria-hidden="true" />
                {t("actions.routine")}
              </DropdownMenuItem>
            </>
          )}
          <DropdownMenuItem
            disabled={isUpdatingStatus}
            onSelect={() => onStatusChange(agent.id, isDisabled ? "ACTIVE" : "DISABLED")}
          >
            {isDisabled ? <CircleCheck aria-hidden="true" /> : <Ban aria-hidden="true" />}
            {isDisabled ? t("actions.enable") : t("actions.disable")}
          </DropdownMenuItem>
          <DropdownMenuSeparator />
          <DropdownMenuItem
            variant="destructive"
            disabled={isDeleting}
            onSelect={() => setConfirmOpen(true)}
          >
            <Trash2 aria-hidden="true" />
            {t("actions.delete")}
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
      <Dialog open={confirmOpen} onOpenChange={setConfirmOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("deleteConfirmTitle")}</DialogTitle>
            <DialogDescription>
              {t("deleteConfirmDescription", { name: agent.name })}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <DialogClose asChild>
              <Button variant="outline">{t("cancel")}</Button>
            </DialogClose>
            <DialogClose asChild>
              <Button variant="destructive" onClick={() => onDelete(agent.id)}>
                {t("actions.delete")}
              </Button>
            </DialogClose>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
