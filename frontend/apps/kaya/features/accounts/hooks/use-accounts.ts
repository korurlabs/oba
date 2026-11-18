"use client";

import { useQuery } from "@tanstack/react-query";

import { useIdentity } from "@/lib/identity/identity-context";

import { getAccount, listAccounts } from "../api";
import type { AccountQueryRequest } from "../schemas";

/** Accounts reload every minute while the page is visible, so usage stays current. */
export const ACCOUNTS_REFETCH_MS = 60_000;

const KEYS = {
  all: (organizationId: string) => ["accounts", organizationId] as const,
  list: (organizationId: string, params: AccountQueryRequest) =>
    [...KEYS.all(organizationId), "list", params] as const,
  detail: (organizationId: string, id: string) =>
    [...KEYS.all(organizationId), "detail", id] as const,
};

export function useAccounts(params: AccountQueryRequest = {}) {
  const { organization } = useIdentity();
  return useQuery({
    queryKey: KEYS.list(organization.organizationId, params),
    queryFn: () => listAccounts(params),
    // TanStack Query pauses an interval while the tab is hidden.
    refetchInterval: ACCOUNTS_REFETCH_MS,
  });
}

export function useAccount(id: string) {
  const { organization } = useIdentity();
  return useQuery({
    queryKey: KEYS.detail(organization.organizationId, id),
    queryFn: () => getAccount(id),
    enabled: id !== "",
    refetchInterval: ACCOUNTS_REFETCH_MS,
  });
}
