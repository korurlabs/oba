"use client";

import { parseAsInteger, parseAsString, useQueryStates } from "nuqs";
import type { Values } from "nuqs";

/** The repository filter's value for lessons of no repository; the API takes it as `organizationWide`. */
export const ORGANIZATION_WIDE = "ORGANIZATION_WIDE";

/** The lesson list's filters, kept in the URL so a filtered list can be reloaded and shared. */
export const lessonFilterParams = {
  search: parseAsString,
  repositoryId: parseAsString,
  userId: parseAsString,
  page: parseAsInteger.withDefault(1),
};

export type LessonFilterParams = Values<typeof lessonFilterParams>;

export function useLessonFilterParams() {
  return useQueryStates(lessonFilterParams);
}
