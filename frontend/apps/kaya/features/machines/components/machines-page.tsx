"use client";

import { parseAsStringEnum, useQueryState } from "nuqs";

import { describeError } from "@/lib/api/error-text";
import { FilterPopover, type FilterCategory } from "@oba/ui/sky/filter-popover";
import { ListToolbar } from "@oba/ui/sky/list-toolbar";
import { PageHeading } from "@oba/ui/sky/page-heading";
import { ListEmptyState, LoadError, TableLoading } from "@oba/ui/sky/page-states";
import { CLEAR_FILTERS_LABEL, FILTER_LABELS } from "@/components/common/list-parts";
import { useNow } from "@/lib/use-now";

import { useMachines } from "../hooks/use-machines";
import { MachineStatusSchema, type MachineQueryRequest, type MachineStatus } from "../schemas";
import { MachinesTable } from "./machines-table";
import { NewMachineDialog } from "./new-machine-dialog";
import { USAGE_TICK_MS } from "./usage-data";

/** The status the list shows when nothing narrows it. */
const DEFAULT_STATUS: MachineStatus = "ACTIVE";

/** Every machine of the organization with its Claude usage, reloaded every minute. */
export function MachinesPage() {
  const [status, setStatus] = useQueryState(
    "status",
    parseAsStringEnum(MachineStatusSchema.options).withDefault(DEFAULT_STATUS)
  );
  const params: MachineQueryRequest = { status };
  const machines = useMachines(params);
  const now = useNow(USAGE_TICK_MS);
  const rows = machines.data?.data ?? [];

  const statusCategory: FilterCategory = {
    key: "status",
    label: "Status",
    mode: "single",
    options: [
      { value: "ACTIVE", label: "Active" },
      { value: "RETIRED", label: "Retired" },
    ],
    value: status,
    defaultValue: DEFAULT_STATUS,
    // Null drops the param, which the URL reads back as the default.
    onChange: (value: string | null) =>
      void setStatus(
        value === null || value === DEFAULT_STATUS ? null : MachineStatusSchema.parse(value)
      ),
  };

  return (
    <div className="flex flex-col gap-6">
      <PageHeading
        title="Machines"
        description="Each Claude Code installation that records into your organization, with how much of its subscription's limits is used."
      />
      <ListToolbar
        filters={
          <FilterPopover
            categories={[statusCategory]}
            onClear={() => void setStatus(null)}
            labels={FILTER_LABELS}
          />
        }
        create={<NewMachineDialog />}
      />
      {machines.error !== null && machines.data === undefined ? (
        <LoadError
          title="Could not load the machines"
          message={describeError(machines.error)}
          onRetry={() => void machines.refetch()}
        />
      ) : machines.data === undefined ? (
        <TableLoading label="Loading machines" />
      ) : rows.length === 0 ? (
        <ListEmptyState
          filtered={status !== DEFAULT_STATUS}
          onClearFilters={() => void setStatus(null)}
          create={<NewMachineDialog />}
          labels={{
            emptyTitle: "No machines yet",
            emptyDescription: "Register a machine, then store its token where the recorder runs.",
            filteredTitle: "No retired machines",
            filteredDescription: "Machines show here once they are retired.",
            clearFilters: CLEAR_FILTERS_LABEL,
          }}
        />
      ) : (
        <>
          {machines.error !== null && (
            <p className="text-warning-text text-sm" role="status">
              The latest reload failed, showing the previous list: {describeError(machines.error)}
            </p>
          )}
          <MachinesTable machines={rows} now={now} />
        </>
      )}
    </div>
  );
}
