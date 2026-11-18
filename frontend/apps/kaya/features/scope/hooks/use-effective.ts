"use client";

import { useQuery } from "@tanstack/react-query";
import { parseAsBoolean, useQueryState } from "nuqs";

import { useIdentity } from "@/lib/identity/identity-context";
import { VERSIONED_KEYS } from "@/lib/versioned-keys";

import { getPackEffective } from "../api";

/**
 * A pack's items at one of its versions or drafts, null for its current
 * version. The pages always read the inherited items too: the lock and the
 * status of an override need them, and the toggle only decides what is listed.
 */
export function usePackEffective(packId: string, versionId: string | null) {
  const { organization } = useIdentity();
  return useQuery({
    queryKey: VERSIONED_KEYS.packEffective(organization.organizationId, packId, versionId, true),
    queryFn: () =>
      getPackEffective(packId, { versionId: versionId ?? undefined, inherited: true }),
    enabled: packId !== "",
  });
}

/** "Show inherited", kept in the URL as `inherited` so a link opens the same view. */
export function useShowInherited() {
  return useQueryState("inherited", parseAsBoolean.withDefault(false));
}
