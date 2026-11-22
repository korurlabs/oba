"use client";

import { useQuery } from "@tanstack/react-query";
import { useOrganizationId } from "@/hooks/use-organization-id";
import { listOrganizationMembers } from "../api";

/** Query keys of the organization members, under the same root Otak uses for them. */
export const MEMBER_KEYS = {
  all: ["organizationMembers"] as const,
  list: (organizationId: string) => [...MEMBER_KEYS.all, "list", organizationId] as const,
};

/**
 * The members of the current organization, for the access screen's member
 * picker. Only a MANAGE holder sees that screen, so a caller passes
 * `enabled: false` for anyone else.
 */
export function useOrganizationMembers({ enabled = true }: { enabled?: boolean } = {}) {
  const organizationId = useOrganizationId();
  return useQuery({
    queryKey: MEMBER_KEYS.list(organizationId ?? ""),
    queryFn: () => listOrganizationMembers(organizationId!),
    enabled: enabled && !!organizationId,
  });
}
