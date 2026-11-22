"use client";

import { useQuery } from "@tanstack/react-query";
import { useOrganizationId } from "@/hooks/use-organization-id";
import { listLegalEntities } from "../api";
import { LEGAL_ENTITY_KEYS } from "./query-keys";

export { LEGAL_ENTITY_KEYS };

/**
 * The legal entities of the organization, for the pickers and filters of
 * documents and templates; waits for the organization. They change rarely
 * and only in the admin app, so the list is kept for five minutes.
 */
export function useLegalEntities() {
  const organizationId = useOrganizationId();
  return useQuery({
    queryKey: LEGAL_ENTITY_KEYS.list(organizationId ?? ""),
    queryFn: () => listLegalEntities(),
    enabled: !!organizationId,
    staleTime: 5 * 60 * 1000,
  });
}
