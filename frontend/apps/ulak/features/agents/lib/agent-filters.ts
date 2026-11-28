import { parseAsString, parseAsStringEnum } from "nuqs/server";
import type { Values } from "nuqs";
import { agentFlowSchema, agentProviderSchema } from "@oba/shared/features/users/schema";
import { isSearching, matchesSearch } from "@/lib/list-search";
import { AgentStatusSchema, type Agent } from "../schemas";

/** The Agents list's search, status, flow and provider in the URL, so a narrowed list can be reloaded and shared. */
export const agentFilterParams = {
  search: parseAsString,
  status: parseAsStringEnum(AgentStatusSchema.options),
  flow: parseAsStringEnum(agentFlowSchema.options),
  provider: parseAsStringEnum(agentProviderSchema.options),
};

export type AgentFilterParams = Values<typeof agentFilterParams>;

/** The search and every filter cleared. */
export const CLEARED_AGENT_FILTERS = { search: null, status: null, flow: null, provider: null };

/** The filters of the popover cleared; the search stays. */
export const CLEARED_AGENT_POPOVER_FILTERS = { status: null, flow: null, provider: null };

/** The agents that match the search on name, the status, the flow and the provider. */
export function filterAgents(agents: readonly Agent[], filters: AgentFilterParams): Agent[] {
  return agents.filter(
    (agent) =>
      (!filters.status || agent.status === filters.status) &&
      (!filters.flow || agent.flow === filters.flow) &&
      (!filters.provider || agent.provider === filters.provider) &&
      matchesSearch(filters.search, [agent.name])
  );
}

/** True when the search or a filter narrows the list. */
export function hasActiveAgentFilter(filters: AgentFilterParams): boolean {
  return isSearching(filters.search) || !!filters.status || !!filters.flow || !!filters.provider;
}
