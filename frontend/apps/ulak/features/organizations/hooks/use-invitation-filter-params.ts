"use client";

import { useQueryStates } from "nuqs";
import { invitationFilterParams } from "../lib/invitation-filters";

/** The Invitations list's search and status, synced with the URL. */
export function useInvitationFilterParams() {
  return useQueryStates(invitationFilterParams);
}
