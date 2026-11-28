"use client";

import { useCallback, useMemo, useState } from "react";
import { useTranslations } from "next-intl";
import { Plus } from "lucide-react";
import { Button } from "@oba/ui/sky/button";
import { FilterPopover, type FilterCategory } from "@oba/ui/sky/filter-popover";
import { ListToolbar } from "@oba/ui/sky/list-toolbar";
import { ListEmptyState } from "@oba/ui/sky/page-states";
import { SearchInput } from "@oba/ui/sky/search-input";
import { agentFlowSchema, agentProviderSchema } from "@oba/shared/features/users/schema";
import { DataTable } from "@/components/data-table";
import {
  useClearSearchLabel,
  useFilterPopoverLabels,
  useListEmptyLabels,
} from "@/components/list-labels";
import { OrganizationLoadError } from "@/features/organizations/components/organization-load-error";
import { AgentStatusSchema, type Agent, type AgentStatusUpdate } from "../schemas";
import { useAgents, useDeleteAgent, useUpdateAgentStatus } from "../hooks/use-agents";
import { useAgentFilterParams } from "../hooks/use-agent-filter-params";
import {
  CLEARED_AGENT_FILTERS,
  CLEARED_AGENT_POPOVER_FILTERS,
  filterAgents,
  hasActiveAgentFilter,
} from "../lib/agent-filters";
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
 * The agents of the organization, with a search on name and status, flow
 * and provider filters in the URL; the API answers with every agent, so the
 * list is narrowed here. Shown only to an OWNER or ADMIN, who are the only
 * roles the API lets read agents, so every control is editable here.
 */
export function AgentsTab({ organizationId }: AgentsTabProps) {
  const t = useTranslations("agents");
  const { data: agentsResponse, isLoading, isError, refetch } = useAgents(organizationId);
  const { mutate: updateStatus, isPending: isUpdatingStatus } =
    useUpdateAgentStatus(organizationId);
  const { mutate: deleteAgent, isPending: isDeleting } = useDeleteAgent(organizationId);
  const [filters, setFilters] = useAgentFilterParams();
  const filterLabels = useFilterPopoverLabels();
  const clearSearchLabel = useClearSearchLabel();
  const emptyLabels = useListEmptyLabels({
    emptyTitle: t("emptyTitle"),
    emptyDescription: t("emptyDescription"),
    filteredTitle: t("filteredTitle"),
  });
  const [dialog, setDialog] = useState<OpenDialog>(null);
  // The last agent a dialog was opened for, kept while the dialog closes so
  // its content does not vanish under the closing animation.
  const [dialogAgentId, setDialogAgentId] = useState<string | null>(null);

  const agents = useMemo(() => agentsResponse?.data ?? [], [agentsResponse?.data]);
  const shownAgents = useMemo(() => filterAgents(agents, filters), [agents, filters]);
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

  const filterCategories: FilterCategory[] = [
    {
      key: "status",
      label: t("columns.status"),
      mode: "single",
      value: filters.status,
      onChange: (value) => {
        const status = AgentStatusSchema.safeParse(value);
        void setFilters({ status: status.success ? status.data : null });
      },
      options: AgentStatusSchema.options.map((value) => ({
        value,
        label: t(`statuses.${value}`),
      })),
    },
    {
      key: "flow",
      label: t("columns.flow"),
      mode: "single",
      value: filters.flow,
      onChange: (value) => {
        const flow = agentFlowSchema.safeParse(value);
        void setFilters({ flow: flow.success ? flow.data : null });
      },
      options: agentFlowSchema.options.map((value) => ({ value, label: t(`flows.${value}`) })),
    },
    {
      key: "provider",
      label: t("columns.provider"),
      mode: "single",
      value: filters.provider,
      onChange: (value) => {
        const provider = agentProviderSchema.safeParse(value);
        void setFilters({ provider: provider.success ? provider.data : null });
      },
      options: agentProviderSchema.options.map((value) => ({
        value,
        label: t(`providers.${value}`),
      })),
    },
  ];

  const newButton = (
    <Button onClick={() => openDialog({ kind: "form", agentId: null })}>
      <Plus aria-hidden="true" />
      {t("newAgent")}
    </Button>
  );

  return (
    <div className="flex flex-col gap-4">
      <ListToolbar
        search={
          <SearchInput
            value={filters.search}
            onSearchChange={(search) => void setFilters({ search: search ?? null })}
            label={t("searchLabel")}
            clearLabel={clearSearchLabel}
          />
        }
        filters={
          <FilterPopover
            categories={filterCategories}
            onClear={() => void setFilters(CLEARED_AGENT_POPOVER_FILTERS)}
            labels={filterLabels}
          />
        }
        create={newButton}
      />

      {isError && !agentsResponse ? (
        <OrganizationLoadError onRetry={() => void refetch()} />
      ) : (
        <DataTable<Agent>
          columns={columns}
          data={shownAgents}
          isLoading={isLoading}
          loadingLabel={t("loading")}
          getRowId={(agent: Agent) => agent.id}
          emptyMessage={
            <ListEmptyState
              filtered={hasActiveAgentFilter(filters)}
              onClearFilters={() => void setFilters(CLEARED_AGENT_FILTERS)}
              create={newButton}
              labels={emptyLabels}
            />
          }
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
    </div>
  );
}
