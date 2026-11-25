"use client";

import Link from "next/link";

import { Badge } from "@oba/ui/sky/badge";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@oba/ui/sky/table";
import { ageText } from "@/lib/format-time";

import type { MachineResponse } from "../schemas";
import { accountPlanText } from "./machine-account";
import { MachineActions } from "./machine-actions";
import { NOT_REPORTED } from "./machine-text";
import { MachineUsageCompact } from "./machine-usage";

function Missing() {
  return <span className="text-muted-foreground">{NOT_REPORTED}</span>;
}

export type MachinesTableProps = {
  machines: readonly MachineResponse[];
  now: number;
};

/**
 * One row per machine: name with its Claude Code version, user, account with
 * its subscription, usage, last seen, sessions and status, with the actions
 * the viewer may take in a row menu. Every cell holds at most two short lines,
 * so rows keep one height; a long name, email or subscription is cut and shown
 * whole on hover.
 */
export function MachinesTable({ machines, now }: MachinesTableProps) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Name</TableHead>
          <TableHead>User</TableHead>
          <TableHead>Account</TableHead>
          <TableHead>Usage</TableHead>
          <TableHead>Last seen</TableHead>
          <TableHead className="text-right">Sessions</TableHead>
          <TableHead>Status</TableHead>
          <TableHead className="w-10">
            <span className="sr-only">Actions</span>
          </TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {machines.map((machine: MachineResponse) => {
          const email = machine.account?.accountEmail;
          const plan = accountPlanText(machine.account);
          return (
            <TableRow key={machine.id} className="h-13">
              <TableCell>
                <div className="flex max-w-56 flex-col">
                  <Link
                    href={`/machines/${machine.id}`}
                    className="truncate font-medium hover:underline"
                    title={machine.machineName}
                  >
                    {machine.machineName}
                  </Link>
                  {machine.claudeCodeVersion && (
                    <span className="text-muted-foreground truncate text-xs">
                      Claude Code {machine.claudeCodeVersion}
                    </span>
                  )}
                </div>
              </TableCell>
              <TableCell className="whitespace-nowrap">
                {machine.userDisplayName ?? machine.userId}
              </TableCell>
              <TableCell>
                {machine.account ? (
                  <div className="flex max-w-64 flex-col">
                    {email ? (
                      <span className="truncate" title={email}>
                        {email}
                      </span>
                    ) : (
                      <Missing />
                    )}
                    {plan !== null && (
                      <span className="text-muted-foreground truncate text-xs" title={plan}>
                        {plan}
                      </span>
                    )}
                  </div>
                ) : (
                  <Missing />
                )}
              </TableCell>
              <TableCell>
                <MachineUsageCompact usage={machine.usage} now={now} />
              </TableCell>
              <TableCell className="whitespace-nowrap">
                {ageText(machine.lastSeenAt, now)}
              </TableCell>
              <TableCell className="text-right whitespace-nowrap tabular-nums">
                <Link href={`/?machineId=${machine.id}`} className="hover:underline">
                  {machine.sessionCount}
                </Link>
              </TableCell>
              <TableCell>
                <Badge variant={machine.status === "ACTIVE" ? "success" : "muted"}>
                  {machine.status === "ACTIVE" ? "Active" : "Retired"}
                </Badge>
              </TableCell>
              <TableCell className="text-right">
                <MachineActions machine={machine} layout="menu" />
              </TableCell>
            </TableRow>
          );
        })}
      </TableBody>
    </Table>
  );
}
