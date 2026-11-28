"use client";

import { useQueryStates } from "nuqs";
import { agentFilterParams } from "../lib/agent-filters";

/** The Agents list's search, status, flow and provider, synced with the URL. */
export function useAgentFilterParams() {
  return useQueryStates(agentFilterParams);
}
