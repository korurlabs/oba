"use client";

import { parseAsInteger, parseAsString, parseAsStringEnum, useQueryStates } from "nuqs";
import type { Values } from "nuqs";

import { VersionStatusSchema } from "@/features/versions/schemas";

/**
 * The change list's filters, kept in the URL; no status shows every change,
 * no `packId` every pack.
 */
export const changeFilterParams = {
  status: parseAsStringEnum(VersionStatusSchema.options),
  packId: parseAsString,
  page: parseAsInteger.withDefault(1),
};

export type ChangeFilterParams = Values<typeof changeFilterParams>;

export function useChangeFilterParams() {
  return useQueryStates(changeFilterParams);
}
