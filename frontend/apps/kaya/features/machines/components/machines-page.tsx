"use client";

import { parseAsStringEnum, useQueryState } from "nuqs";

import { describeError } from "@/lib/api/error-text";
import { NativeSelect } from "@oba/ui/sky/field";
import { EmptyState, LoadError, TableLoading } from "@oba/ui/sky/page-states";
import { useNow } from "@/lib/use-now";

import { useMachines } from "../hooks/use-machines";
import { MachineStatusSchema, type MachineQueryRequest } from "../schemas";
import { MachinesTable } from "./machines-table";
import { NewMachineDialog } from "./new-machine-dialog";
import { USAGE_TICK_MS } from "./usage-data";

/** Every machine of the organization with its Claude usage, reloaded every minute. */
export function MachinesPage() {
  const [status, setStatus] = useQueryState(
    "status",
    parseAsStringEnum(MachineStatusSchema.options).withDefault("ACTIVE")
  );
  const params: MachineQueryRequest = { status };
  const machines = useMachines(params);
  const now = useNow(USAGE_TICK_MS);
  const rows = machines.data?.data ?? [];

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div className="flex flex-col gap-1">
          <h1>Machines</h1>
          <p className="text-muted-foreground">
            Each Claude Code installation that records into your organization, with how much of its
            subscription&apos;s limits is used.
          </p>
        </div>
        <div className="flex items-end gap-3">
          <label className="flex flex-col gap-1 text-xs font-medium">
            <span className="text-muted-foreground">Status</span>
            <NativeSelect
              value={status}
              onChange={(event) => void setStatus(MachineStatusSchema.parse(event.target.value))}
            >
              <option value="ACTIVE">Active</option>
              <option value="RETIRED">Retired</option>
            </NativeSelect>
          </label>
          <NewMachineDialog />
        </div>
      </div>
      {machines.error !== null && machines.data === undefined ? (
        <LoadError
          title="Could not load the machines"
          message={describeError(machines.error)}
          onRetry={() => void machines.refetch()}
        />
      ) : machines.data === undefined ? (
        <TableLoading label="Loading machines" />
      ) : rows.length === 0 ? (
        <EmptyState
          title={status === "ACTIVE" ? "No machines yet" : "No retired machines"}
          description={
            status === "ACTIVE"
              ? "Register a machine with New machine, then store its token where the recorder runs."
              : "Retired machines show here."
          }
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
