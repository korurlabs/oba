"use client";

import { useQuery } from "@tanstack/react-query";
import { useOrganizationId } from "@/hooks/use-organization-id";
import { listOrganizationMembers } from "../api";
import { MEMBER_KEYS } from "./query-keys";

export { MEMBER_KEYS };

/** The members of the current organization, for the owner pickers and filters. */
export function useOrganizationMembers() {
  const organizationId = useOrganizationId();
  return useQuery({
    queryKey: MEMBER_KEYS.list(organizationId ?? ""),
    queryFn: () => listOrganizationMembers(organizationId ?? ""),
    enabled: !!organizationId,
  });
}
