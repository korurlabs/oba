"use client";

import { parseAsBoolean, parseAsInteger, parseAsString, useQueryStates } from "nuqs";
import type { Values } from "nuqs";

/** The wall's filters, kept in the URL so a filtered wall can be reloaded and shared. */
export const sessionFilterParams = {
  search: parseAsString,
  repositoryId: parseAsString,
  userId: parseAsString,
  machineId: parseAsString,
  model: parseAsString,
  accountOrganizationUuid: parseAsString,
  ended: parseAsBoolean,
  page: parseAsInteger.withDefault(1),
};

export type SessionFilterParams = Values<typeof sessionFilterParams>;

export function useSessionFilterParams() {
  return useQueryStates(sessionFilterParams);
}
