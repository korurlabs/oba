"use client";

import Link from "next/link";
import type { ReactNode } from "react";
import { MessagesSquare } from "lucide-react";

import { Badge } from "@oba/ui/sky/badge";
import { Button } from "@oba/ui/sky/button";
import { Card, CardContent, CardHeader, CardTitle } from "@oba/ui/sky/card";
import { PageHeading } from "@oba/ui/sky/page-heading";
import { ERROR_CODES, KayaApiError } from "@/lib/api/errors";
import { describeError } from "@/lib/api/error-text";
import { ageText, dateTimeText } from "@/lib/format-time";
import { EmptyState, LoadError, TableLoading } from "@oba/ui/sky/page-states";
import { useNow } from "@/lib/use-now";

import { useMachine } from "../hooks/use-machines";
import { MachineActions } from "./machine-actions";
import { accountSubscriptionText, NOT_REPORTED } from "./machine-text";
import { MachineUsage } from "./machine-usage";
import { USAGE_TICK_MS } from "./usage-data";

function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="grid grid-cols-1 gap-1 py-2 sm:grid-cols-[12rem_1fr] sm:gap-4">
      <dt className="text-muted-foreground text-sm">{label}</dt>
      <dd className="min-w-0 text-sm break-words">{children}</dd>
    </div>
  );
}

function orMissing(value: string | null | undefined): ReactNode {
  return value ?? <span className="text-muted-foreground">{NOT_REPORTED}</span>;
}

/** One machine: what it reported, its token, its usage and its actions. */
export function MachineDetail({ machineId }: { machineId: string }) {
  const machine = useMachine(machineId);
  const now = useNow(USAGE_TICK_MS);
  const data = machine.data?.data ?? null;

  if (machine.error !== null && data === null) {
    if (
      machine.error instanceof KayaApiError &&
      machine.error.status === 404 &&
      machine.error.code === ERROR_CODES.NOT_FOUND
    ) {
      return (
        <EmptyState
          title="Machine not found"
          description="Your organization has no machine with this id."
        />
      );
    }
    return (
      <LoadError
        title="Could not load the machine"
        message={describeError(machine.error)}
        onRetry={() => void machine.refetch()}
      />
    );
  }
  if (data === null) return <TableLoading label="Loading the machine" />;

  const subscription = accountSubscriptionText(data.account);
  return (
    <div className="flex flex-col gap-6">
      <PageHeading
        title={data.machineName}
        back={{ href: "/machines", label: "Machines" }}
        description={
          <div className="flex flex-wrap items-center gap-2">
            <Badge variant={data.status === "ACTIVE" ? "success" : "muted"}>
              {data.status === "ACTIVE" ? "Active" : "Retired"}
            </Badge>
            <p className="text-muted-foreground">
              Machine of{" "}
              <span className="text-foreground">{data.userDisplayName ?? data.userId}</span>
            </p>
          </div>
        }
        actions={
          <>
            <Button asChild variant="outline" size="sm">
              <Link href={`/?machineId=${data.id}`}>
                <MessagesSquare aria-hidden />
                {data.sessionCount} sessions
              </Link>
            </Button>
            <MachineActions machine={data} />
          </>
        }
      />
      <div className="grid gap-6 lg:grid-cols-[1fr_20rem]">
        <Card>
          <CardHeader>
            <CardTitle>Details</CardTitle>
          </CardHeader>
          <CardContent>
            <dl className="divide-y">
              <Row label="Claude Code version">{orMissing(data.claudeCodeVersion)}</Row>
              <Row label="Account">{orMissing(data.account?.accountEmail)}</Row>
              <Row label="Subscription">{orMissing(subscription)}</Row>
              <Row label="Last seen">
                {data.lastSeenAt
                  ? `${ageText(data.lastSeenAt, now)} (${dateTimeText(data.lastSeenAt)})`
                  : "Never"}
              </Row>
              <Row label="Token">
                {data.token ? (
                  <span>
                    <code className="font-mono">{data.token.tokenPrefix}…</code>, last used{" "}
                    {ageText(data.token.lastUsedAt, now).toLowerCase()}, expires{" "}
                    {dateTimeText(data.token.expiresAt)}
                  </span>
                ) : (
                  <span className="text-muted-foreground">No live token</span>
                )}
              </Row>
              <Row label="Registered">{dateTimeText(data.createdAt)}</Row>
            </dl>
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>Usage</CardTitle>
          </CardHeader>
          <CardContent>
            <MachineUsage usage={data.usage} now={now} />
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
