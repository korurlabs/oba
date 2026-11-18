"use client";

import { useQuery } from "@tanstack/react-query";

import { useIdentity } from "@/lib/identity/identity-context";
import { VERSIONED_KEYS } from "@/lib/versioned-keys";

import { listSettings } from "../api";

const KEYS = {
  list: (organizationId: string, versionIds: readonly string[]) =>
    [...VERSIONED_KEYS.kinds(organizationId), "settings", [...versionIds].sort()] as const,
};

/** The permission lists and hooks of the named versions' settings files; disabled while none is named. */
export function useSettings(versionIds: readonly string[]) {
  const { organization } = useIdentity();
  return useQuery({
    queryKey: KEYS.list(organization.organizationId, versionIds),
    queryFn: () => listSettings({ versionId: [...versionIds] }),
    enabled: versionIds.length > 0,
  });
}
