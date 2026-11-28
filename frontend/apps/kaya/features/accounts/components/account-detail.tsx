"use client";

import type { ReactNode } from "react";

import { Badge } from "@oba/ui/sky/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@oba/ui/sky/card";
import { PageHeading } from "@oba/ui/sky/page-heading";
import { EmptyState, LoadError, TableLoading } from "@oba/ui/sky/page-states";
import { MachinesTable } from "@/features/machines/components";
import { USAGE_TICK_MS } from "@/features/machines/components/usage-data";
import { useMachines } from "@/features/machines/hooks/use-machines";
import { ERROR_CODES, KayaApiError } from "@/lib/api/errors";
import { describeError } from "@/lib/api/error-text";
import { ageText, dateTimeText } from "@/lib/format-time";
import { useNow } from "@/lib/use-now";

import { useAccount } from "../hooks/use-accounts";
import {
  PROVIDER_LABELS,
  providerReportsUsage,
  type AccountUser,
  type AccountDetailResponse,
} from "../schemas";
import { AccountProviderUsage } from "./account-provider-usage";
import { NOT_REPORTED, organizationText, tierText } from "./account-text";
import { AccountUsage } from "./account-usage";
import { MachineCount } from "./accounts-page";

const SOURCE_LABELS = { RECORDER: "Recorder", MANUAL: "Manual" } as const;

function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="grid grid-cols-1 gap-1 py-2 sm:grid-cols-[12rem_1fr] sm:gap-4">
      <dt className="text-muted-foreground text-sm">{label}</dt>
      <dd className="min-w-0 text-sm break-words">{children}</dd>
    </div>
  );
}

function orMissing(value: string | null | undefined): ReactNode {
  return value || <span className="text-muted-foreground">{NOT_REPORTED}</span>;
}

function UsersCard({ users, now }: { users: readonly AccountUser[]; now: number }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Users</CardTitle>
      </CardHeader>
      <CardContent>
        {users.length === 0 ? (
          <p className="text-muted-foreground text-sm">No user</p>
        ) : (
          <ul className="divide-y">
            {users.map((user: AccountUser) => (
              <li
                key={user.userId}
                className="flex items-center justify-between gap-2 py-2 text-sm"
              >
                <span className="flex min-w-0 flex-col">
                  <span className="break-all">{user.displayName}</span>
                  <span className="text-muted-foreground text-xs">
                    last used {ageText(user.lastSeenAt, now).toLowerCase()}
                  </span>
                </span>
                <Badge variant="muted">{SOURCE_LABELS[user.source]}</Badge>
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  );
}

/** The snapshot the recorder last sent for the account, with when it was captured. */
function CurrentUsageCard({ account, now }: { account: AccountDetailResponse; now: number }) {
  const capturedAt = account.usage.capturedAt;
  return (
    <Card>
      <CardHeader>
        <CardTitle>Current usage</CardTitle>
        {providerReportsUsage(account.provider) && (
          <CardDescription>
            Reported by the Kaya recorder
            {capturedAt ? `, captured ${dateTimeText(capturedAt)}` : ""}
          </CardDescription>
        )}
      </CardHeader>
      <CardContent>
        <AccountUsage account={account} now={now} />
      </CardContent>
    </Card>
  );
}

/** The machines whose latest account is this one, retired ones included. */
function AccountMachines({ accountId, now }: { accountId: string; now: number }) {
  const machines = useMachines({ accountId });
  const rows = machines.data?.data ?? [];
  if (machines.error !== null && machines.data === undefined) {
    return (
      <LoadError
        title="Could not load the machines"
        message={describeError(machines.error)}
        onRetry={() => void machines.refetch()}
      />
    );
  }
  if (machines.data === undefined) return <TableLoading label="Loading machines" />;
  if (rows.length === 0) {
    return (
      <EmptyState title="No machines" description="No machine has reported this account yet." />
    );
  }
  return <MachinesTable machines={rows} now={now} />;
}

/**
 * One account: its provider and subscription, the users who use it, the
 * usage the recorder last reported beside what the provider will report,
 * and the machines on it.
 */
export function AccountDetail({ accountId }: { accountId: string }) {
  const account = useAccount(accountId);
  const now = useNow(USAGE_TICK_MS);
  const data = account.data?.data ?? null;

  if (account.error !== null && data === null) {
    if (
      account.error instanceof KayaApiError &&
      account.error.status === 404 &&
      account.error.code === ERROR_CODES.NOT_FOUND
    ) {
      return (
        <EmptyState
          title="Account not found"
          description="Your organization has no account with this id."
        />
      );
    }
    return (
      <LoadError
        title="Could not load the account"
        message={describeError(account.error)}
        onRetry={() => void account.refetch()}
      />
    );
  }
  if (data === null) return <TableLoading label="Loading the account" />;

  return (
    <div className="flex flex-col gap-6">
      <PageHeading title={data.accountEmail} back={{ href: "/accounts", label: "Accounts" }} />
      <div className="grid gap-6 lg:grid-cols-[1fr_20rem]">
        <Card>
          <CardHeader>
            <CardTitle>Account</CardTitle>
          </CardHeader>
          <CardContent>
            <dl className="divide-y">
              <Row label="Provider">{PROVIDER_LABELS[data.provider]}</Row>
              <Row label="Organization">{organizationText(data)}</Row>
              <Row label="Organization UUID">{orMissing(data.accountOrganizationUuid)}</Row>
              <Row label="Tier">{tierText(data)}</Row>
              <Row label="Billing type">{orMissing(data.billingType)}</Row>
              <Row label="Organization type">{orMissing(data.organizationType)}</Row>
              <Row label="Active machines">
                <MachineCount account={data} />
              </Row>
              <Row label="Last seen">
                {data.lastSeenAt
                  ? `${ageText(data.lastSeenAt, now)} (${dateTimeText(data.lastSeenAt)})`
                  : "Never"}
              </Row>
            </dl>
          </CardContent>
        </Card>
        <UsersCard users={data.users} now={now} />
      </div>
      <div className="grid gap-6 lg:grid-cols-2">
        <CurrentUsageCard account={data} now={now} />
        <AccountProviderUsage provider={data.provider} />
      </div>
      <AccountMachines accountId={data.id} now={now} />
    </div>
  );
}
