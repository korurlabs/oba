"use client";

import { useQuery } from "@tanstack/react-query";
import { listOrganizationUsers } from "../api";

const KEYS = {
  all: ["users"] as const,
};

export function useOrganizationUsers(options?: { enabled?: boolean }) {
  const enabled = options?.enabled ?? true;
  return useQuery({
    queryKey: KEYS.all,
    queryFn: () => listOrganizationUsers(),
    enabled,
  });
}
