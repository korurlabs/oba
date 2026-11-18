"use client";

import Link from "next/link";

import { Badge } from "@oba/ui/sky/badge";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@oba/ui/sky/table";
import { ageText } from "@/lib/format-time";

import type { MachineResponse } from "../schemas";
import { MachineActions } from "./machine-actions";
import { accountSubscriptionText, NOT_REPORTED } from "./machine-text";
import { MachineUsage } from "./machine-usage";

function Missing() {
  return <span className="text-muted-foreground">{NOT_REPORTED}</span>;
}

export type MachinesTableProps = {
  machines: readonly MachineResponse[];
  now: number;
};

/**
 * One row per machine: name, user, Claude Code version, account,
 * subscription, usage, last seen, sessions and status, with the actions the
 * viewer may take. Values the machine never sent read "Not reported".
 */
export function MachinesTable({ machines, now }: MachinesTableProps) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Name</TableHead>
          <TableHead>User</TableHead>
          <TableHead>Claude Code</TableHead>
          <TableHead>Account</TableHead>
          <TableHead>Subscription</TableHead>
          <TableHead>Usage</TableHead>
          <TableHead>Last seen</TableHead>
          <TableHead className="text-right">Sessions</TableHead>
          <TableHead>Status</TableHead>
          <TableHead>
            <span className="sr-only">Actions</span>
          </TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {machines.map((machine: MachineResponse) => {
          const subscription = accountSubscriptionText(machine.account);
          return (
            <TableRow key={machine.id} className="align-top">
              <TableCell className="font-medium">
                <Link href={`/machines/${machine.id}`} className="hover:underline">
                  {machine.machineName}
                </Link>
              </TableCell>
              <TableCell>{machine.userDisplayName ?? machine.userId}</TableCell>
              <TableCell>{machine.claudeCodeVersion ?? <Missing />}</TableCell>
              <TableCell>{machine.account?.accountEmail ?? <Missing />}</TableCell>
              <TableCell className="whitespace-normal">{subscription ?? <Missing />}</TableCell>
              <TableCell>
                <MachineUsage usage={machine.usage} now={now} className="w-56" />
              </TableCell>
              <TableCell className="whitespace-nowrap">
                {ageText(machine.lastSeenAt, now)}
              </TableCell>
              <TableCell className="text-right tabular-nums">
                <Link href={`/?machineId=${machine.id}`} className="hover:underline">
                  {machine.sessionCount}
                </Link>
              </TableCell>
              <TableCell>
                <Badge variant={machine.status === "ACTIVE" ? "success" : "muted"}>
                  {machine.status === "ACTIVE" ? "Active" : "Retired"}
                </Badge>
              </TableCell>
              <TableCell>
                <MachineActions machine={machine} size="xs" />
              </TableCell>
            </TableRow>
          );
        })}
      </TableBody>
    </Table>
  );
}
