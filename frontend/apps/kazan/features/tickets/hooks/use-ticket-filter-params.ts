"use client";

import {
  parseAsArrayOf,
  parseAsBoolean,
  parseAsString,
  parseAsStringEnum,
  useQueryStates,
} from "nuqs";
import type { Values } from "nuqs";
import { TicketStatus, TicketType, TicketPriority } from "@/types/enums";

export const ticketFilterParams = {
  search: parseAsString,
  statuses: parseAsArrayOf(parseAsStringEnum(Object.values(TicketStatus))),
  types: parseAsArrayOf(parseAsStringEnum(Object.values(TicketType))),
  priorities: parseAsArrayOf(parseAsStringEnum(Object.values(TicketPriority))),
  assigneeIds: parseAsArrayOf(parseAsString),
  reviewerIds: parseAsArrayOf(parseAsString),
  createdByIds: parseAsArrayOf(parseAsString),
  labelIds: parseAsArrayOf(parseAsString),
  boardIds: parseAsArrayOf(parseAsString),
  projectId: parseAsString,
  dueDateStart: parseAsString,
  dueDateEnd: parseAsString,
  includeAllBoards: parseAsBoolean.withDefault(true),
  sortBy: parseAsString.withDefault("displayOrder"),
  sortDirection: parseAsStringEnum(["asc", "desc"]).withDefault("asc"),
};

export type TicketFilterParams = Values<typeof ticketFilterParams>;

export function useTicketFilterParams() {
  return useQueryStates(ticketFilterParams);
}
