"use client";

import { useState } from "react";
import Link from "next/link";
import { parseAsString, useQueryState } from "nuqs";

import { Badge } from "@oba/ui/sky/badge";
import { FilterPopover, type FilterCategory } from "@oba/ui/sky/filter-popover";
import { ListToolbar } from "@oba/ui/sky/list-toolbar";
import { PageHeading } from "@oba/ui/sky/page-heading";
import { ListEmptyState, LoadError, TableLoading } from "@oba/ui/sky/page-states";
import { SearchInput } from "@oba/ui/sky/search-input";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@oba/ui/sky/table";
import {
  CLEAR_FILTERS_LABEL,
  CLEAR_SEARCH_LABEL,
  FILTER_LABELS,
  withChosen,
} from "@/components/common/list-parts";
import { USAGE_TICK_MS } from "@/features/machines/components/usage-data";
import { useMembers } from "@/features/members/hooks/use-members";
import type { MemberResponse } from "@/features/members/schemas";
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
 * The accounts the search finds by account e-mail, organization or user
 * name, ignoring case; an empty search keeps every account. The list holds
 * every account of the organization, so it is searched in the browser.
 */
export function searchAccounts(
  accounts: readonly AccountListResponse[],
  search: string
): AccountListResponse[] {
  const needle = search.trim().toLowerCase();
  if (needle === "") return [...accounts];
  return accounts.filter((account: AccountListResponse) => {
    const texts = [
      account.accountEmail,
      organizationText(account),
      ...account.users.map((user: AccountUser) => user.displayName),
    ];
    return texts.some((text: string) => text.toLowerCase().includes(needle));
  });
}

/**
 * Every account of the organization with its provider, subscription, users
 * and current usage, reloaded every minute; `?userId=` keeps one user's,
 * which the User filter shows and clears.
 */
export function AccountsPage() {
  const [userId, setUserId] = useQueryState("userId", parseAsString);
  const [search, setSearch] = useState("");
  const params: AccountQueryRequest = userId ? { userId } : {};
  const accounts = useAccounts(params);
  const members = useMembers();
  const now = useNow(USAGE_TICK_MS);
  const rows = searchAccounts(accounts.data?.data ?? [], search);
  const userCategory: FilterCategory = {
    key: "userId",
    label: "User",
    mode: "single",
    searchable: true,
    options: withChosen(
      (members.data?.data ?? []).map((member: MemberResponse) => ({
        value: member.userId,
        label: member.displayName,
      })),
      userId
    ),
    value: userId,
    onChange: (value: string | null) => void setUserId(value),
  };

  return (
    <div className="flex flex-col gap-6">
      <PageHeading
        title="Accounts"
        description="The AI provider accounts your users work on, with how much of each limit is used."
      />
      <ListToolbar
        search={
          <SearchInput
            value={search}
            onSearchChange={(next: string | undefined) => setSearch(next ?? "")}
            label="Search accounts"
            placeholder="E-mail, organization or user"
            clearLabel={CLEAR_SEARCH_LABEL}
          />
        }
        filters={
          <FilterPopover
            categories={[userCategory]}
            onClear={() => void setUserId(null)}
            labels={FILTER_LABELS}
          />
        }
      />
      {members.error !== null && (
        <p className="text-warning-text text-sm" role="status">
          The user list could not be loaded: {describeError(members.error)}
        </p>
      )}
      {accounts.error !== null && accounts.data === undefined ? (
        <LoadError
          title="Could not load the accounts"
          message={describeError(accounts.error)}
          onRetry={() => void accounts.refetch()}
        />
      ) : accounts.data === undefined ? (
        <TableLoading label="Loading accounts" />
      ) : rows.length === 0 ? (
        <ListEmptyState
          filtered={userId !== null || search !== ""}
          onClearFilters={() => {
            setSearch("");
            void setUserId(null);
          }}
          labels={{
            emptyTitle: "No account reported yet",
            emptyDescription: "An account shows here once a user's recorder reports it.",
            filteredTitle: "No account matches",
            filteredDescription: "No account matches the search and filters.",
            clearFilters: CLEAR_FILTERS_LABEL,
          }}
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
