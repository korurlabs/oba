"use client";

import { useQuery } from "@tanstack/react-query";

import { useIdentity } from "@/lib/identity/identity-context";

import { listMembers } from "../api";

const KEYS = {
  members: (organizationId: string) => ["members", organizationId] as const,
};

/** The organization's members, for the names Kaya shows next to user ids. */
export function useMembers() {
  const { organization } = useIdentity();
  return useQuery({
    queryKey: [...KEYS.members(organization.organizationId), "list"],
    queryFn: () => listMembers(organization.organizationId),
  });
}
