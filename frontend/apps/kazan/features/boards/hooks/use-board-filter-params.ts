"use client";

import { useQueryStates } from "nuqs";
import { filtersSearchParams } from "@oba/shared/constants/search-params";
import type { Values } from "nuqs";

export const boardFilterParams = {
  ...filtersSearchParams,
};

export type BoardFilterParams = Values<typeof boardFilterParams>;

export function useBoardFilterParams() {
  return useQueryStates(boardFilterParams);
}
