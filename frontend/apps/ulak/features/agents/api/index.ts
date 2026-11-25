import { api } from "@/lib/api/client";
import { API_ROUTES } from "@/lib/api-routes";
import type { ApiResponse } from "@oba/shared/schema";
import type {
  Agent,
  AgentCreateRequest,
  AgentOptions,
  AgentRoutineRequest,
  AgentStatusUpdate,
  AgentTokenCreated,
  AgentUpdateRequest,
} from "../schemas";

export async function listAgents(organizationId: string) {
  const res = await api.get<ApiResponse<Agent[]>>(API_ROUTES.agents.list(organizationId));
  return res.data;
}

export async function getAgentOptions(organizationId: string) {
  const res = await api.get<ApiResponse<AgentOptions>>(API_ROUTES.agents.options(organizationId));
  return res.data;
}

export async function createAgent(organizationId: string, data: AgentCreateRequest) {
  const res = await api.post<ApiResponse<Agent>>(API_ROUTES.agents.list(organizationId), data);
  return res.data;
}

export async function updateAgent(
  organizationId: string,
  agentId: string,
  data: AgentUpdateRequest
) {
  const res = await api.put<ApiResponse<Agent>>(
    API_ROUTES.agents.detail(organizationId, agentId),
    data
  );
  return res.data;
}

export async function updateAgentStatus(
  organizationId: string,
  agentId: string,
  status: AgentStatusUpdate
) {
  const res = await api.patch<ApiResponse<Agent>>(
    API_ROUTES.agents.status(organizationId, agentId),
    { status }
  );
  return res.data;
}

export async function deleteAgent(organizationId: string, agentId: string): Promise<void> {
  await api.delete(API_ROUTES.agents.detail(organizationId, agentId));
}

/** Issues a new token, revoking the live one. The plaintext comes back this once. */
export async function issueAgentToken(organizationId: string, agentId: string) {
  const res = await api.post<ApiResponse<AgentTokenCreated>>(
    API_ROUTES.agents.token(organizationId, agentId)
  );
  return res.data;
}

export async function revokeAgentToken(organizationId: string, agentId: string): Promise<void> {
  await api.delete(API_ROUTES.agents.token(organizationId, agentId));
}

export async function setAgentRoutine(
  organizationId: string,
  agentId: string,
  data: AgentRoutineRequest
) {
  const res = await api.put<ApiResponse<Agent>>(
    API_ROUTES.agents.routine(organizationId, agentId),
    data
  );
  return res.data;
}

export async function removeAgentRoutine(organizationId: string, agentId: string): Promise<void> {
  await api.delete(API_ROUTES.agents.routine(organizationId, agentId));
}
