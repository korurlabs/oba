"use client";

import { useCallback, useState } from "react";
import { useTranslations } from "next-intl";
import { Plus } from "lucide-react";
import { Button } from "@oba/ui/sky/button";
import { EmptyState } from "@oba/ui/sky/page-states";
import { DataTable } from "@/components/data-table";
import { OrganizationLoadError } from "@/features/organizations/components/organization-load-error";
import type { Agent, AgentStatusUpdate } from "../schemas";
import { useAgents, useDeleteAgent, useUpdateAgentStatus } from "../hooks/use-agents";
import { useAgentColumns } from "../hooks/use-agent-columns";
import { AgentFormDialog } from "./agent-form-dialog";
import { AgentTokenDialog } from "./agent-token-dialog";
import { AgentRoutineDialog } from "./agent-routine-dialog";

/** Which dialog is open, and for which agent (null: a new agent). */
type OpenDialog =
  | { kind: "form"; agentId: string | null }
  | { kind: "token"; agentId: string }
  | { kind: "routine"; agentId: string }
  | null;

interface AgentsTabProps {
  organizationId: string;
}

/**
 * The agents of the organization. Shown only to an OWNER or ADMIN, who are
 * the only roles the API lets read agents, so every control is editable here.
 */
export function AgentsTab({ organizationId }: AgentsTabProps) {
  const t = useTranslations("agents");
  const { data: agentsResponse, isLoading, isError, refetch } = useAgents(organizationId);
  const { mutate: updateStatus, isPending: isUpdatingStatus } =
    useUpdateAgentStatus(organizationId);
  const { mutate: deleteAgent, isPending: isDeleting } = useDeleteAgent(organizationId);
  const [dialog, setDialog] = useState<OpenDialog>(null);
  // The last agent a dialog was opened for, kept while the dialog closes so
  // its content does not vanish under the closing animation.
  const [dialogAgentId, setDialogAgentId] = useState<string | null>(null);

  const agents = agentsResponse?.data ?? [];
  // Read from the list on every render, so a dialog shows the agent as the
  // last refetch returned it (a new token prefix, a new routine).
  const dialogAgent = agents.find((agent) => agent.id === dialogAgentId) ?? null;

  const openDialog = useCallback((next: NonNullable<OpenDialog>) => {
    setDialogAgentId(next.agentId);
    setDialog(next);
  }, []);

  // The handlers are dependencies of the column memo. A new identity rebuilds
  // every column and remounts every cell, which closes an open row menu or
  // dialog, so they are kept stable.
  const handleEdit = useCallback(
    (agent: Agent) => openDialog({ kind: "form", agentId: agent.id }),
    [openDialog]
  );
  const handleManageToken = useCallback(
    (agent: Agent) => openDialog({ kind: "token", agentId: agent.id }),
    [openDialog]
  );
  const handleManageRoutine = useCallback(
    (agent: Agent) => openDialog({ kind: "routine", agentId: agent.id }),
    [openDialog]
  );
  const handleStatusChange = useCallback(
    (agentId: string, status: AgentStatusUpdate) => updateStatus({ agentId, status }),
    [updateStatus]
  );
  const handleDelete = useCallback((agentId: string) => deleteAgent(agentId), [deleteAgent]);

  const columns = useAgentColumns({
    onEdit: handleEdit,
    onManageToken: handleManageToken,
    onManageRoutine: handleManageRoutine,
    onStatusChange: handleStatusChange,
    onDelete: handleDelete,
    isUpdatingStatus,
    isDeleting,
  });

  const closeDialog = (isOpen: boolean) => {
    if (!isOpen) setDialog(null);
  };

  return (
    <section className="flex flex-col gap-4" aria-labelledby="organization-agents-title">
      <div className="flex min-h-9 flex-wrap items-center justify-between gap-3">
        <div className="flex flex-col gap-1">
          <h2 id="organization-agents-title" className="text-xl">
            {t("title")}
          </h2>
          <p className="text-muted-foreground text-sm">{t("description")}</p>
        </div>
        <Button size="sm" onClick={() => openDialog({ kind: "form", agentId: null })}>
          <Plus aria-hidden="true" />
          {t("newAgent")}
        </Button>
      </div>

      {isError && !agentsResponse ? (
        <OrganizationLoadError onRetry={() => void refetch()} />
      ) : (
        <DataTable<Agent>
          columns={columns}
          data={agents}
          isLoading={isLoading}
          loadingLabel={t("loading")}
          getRowId={(agent: Agent) => agent.id}
          emptyMessage={<EmptyState title={t("emptyTitle")} description={t("emptyDescription")} />}
        />
      )}

      <AgentFormDialog
        organizationId={organizationId}
        agent={dialog?.kind === "form" ? dialogAgent : null}
        open={dialog?.kind === "form"}
        onOpenChange={closeDialog}
      />
      <AgentTokenDialog
        organizationId={organizationId}
        agent={dialogAgent}
        open={dialog?.kind === "token"}
        onOpenChange={closeDialog}
      />
      <AgentRoutineDialog
        organizationId={organizationId}
        agent={dialogAgent}
        open={dialog?.kind === "routine"}
        onOpenChange={closeDialog}
      />
    </section>
  );
}
