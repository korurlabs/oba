"use client";

import Link from "next/link";
import { parseAsString, useQueryState } from "nuqs";

import { Badge } from "@oba/ui/sky/badge";
import { EmptyState, LoadError, TableLoading } from "@oba/ui/sky/page-states";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@oba/ui/sky/table";
import { USAGE_TICK_MS } from "@/features/machines/components/usage-data";
import { describeError } from "@/lib/api/error-text";
import { ageText } from "@/lib/format-time";
import { useNow } from "@/lib/use-now";

import { useAccounts } from "../hooks/use-accounts";
import {
  PROVIDER_LABELS,
  type AccountUser,
  type AccountListResponse,
  type AccountQueryRequest,
} from "../schemas";
import { organizationText, tierText } from "./account-text";
import { AccountUsage } from "./account-usage";

/** The users of one account as links, or "No user". */
export function AccountUsers({ users }: { users: readonly AccountUser[] }) {
  if (users.length === 0) return <span className="text-muted-foreground">No user</span>;
  return (
    <ul className="flex flex-col gap-0.5">
      {users.map((user: AccountUser) => (
        <li key={user.userId}>{user.displayName}</li>
      ))}
    </ul>
  );
}

/** "2" active machines, with " / 3" when retired ones also used the account. */
export function MachineCount({ account }: { account: AccountListResponse }) {
  return (
    <>
      {account.activeMachines}
      {account.machines !== account.activeMachines && (
        <span className="text-muted-foreground"> / {account.machines}</span>
      )}
    </>
  );
}

/**
 * Every account of the organization with its provider, subscription, users
 * and current usage, reloaded every minute; `?userId=` keeps one user's.
 */
export function AccountsPage() {
  const [userId] = useQueryState("userId", parseAsString);
  const params: AccountQueryRequest = userId ? { userId } : {};
  const accounts = useAccounts(params);
  const now = useNow(USAGE_TICK_MS);
  const rows = accounts.data?.data ?? [];

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col gap-1">
        <h1>Accounts</h1>
        <p className="text-muted-foreground">
          The AI provider accounts your users work on, with how much of each limit is used.
        </p>
      </div>
      {accounts.error !== null && accounts.data === undefined ? (
        <LoadError
          title="Could not load the accounts"
          message={describeError(accounts.error)}
          onRetry={() => void accounts.refetch()}
        />
      ) : accounts.data === undefined ? (
        <TableLoading label="Loading accounts" />
      ) : rows.length === 0 ? (
        <EmptyState
          title="No account reported yet"
          description="An account shows here once a user's recorder reports it."
        />
      ) : (
        <>
          {accounts.error !== null && (
            <p className="text-warning-text text-sm" role="status">
              The latest reload failed, showing the previous list: {describeError(accounts.error)}
            </p>
          )}
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Account</TableHead>
                <TableHead>Provider</TableHead>
                <TableHead>Organization</TableHead>
                <TableHead>Tier</TableHead>
                <TableHead>Users</TableHead>
                <TableHead className="text-right">Machines</TableHead>
                <TableHead>Usage</TableHead>
                <TableHead>Last seen</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.map((account: AccountListResponse) => (
                <TableRow key={account.id} className="align-top">
                  <TableCell className="font-medium">
                    <Link href={`/accounts/${account.id}`} className="hover:underline">
                      {account.accountEmail}
                    </Link>
                  </TableCell>
                  <TableCell>
                    <Badge variant="outline">{PROVIDER_LABELS[account.provider]}</Badge>
                  </TableCell>
                  <TableCell>{organizationText(account)}</TableCell>
                  <TableCell>{tierText(account)}</TableCell>
                  <TableCell className="whitespace-normal">
                    <AccountUsers users={account.users} />
                  </TableCell>
                  <TableCell className="text-right tabular-nums">
                    <MachineCount account={account} />
                  </TableCell>
                  <TableCell>
                    <AccountUsage account={account} now={now} className="w-56" />
                  </TableCell>
                  <TableCell className="whitespace-nowrap">
                    {ageText(account.lastSeenAt, now)}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </>
      )}
    </div>
  );
}
