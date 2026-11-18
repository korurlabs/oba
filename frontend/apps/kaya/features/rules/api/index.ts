import { api } from "@/lib/api/client";
import { API_ROUTES } from "@/lib/api-routes";
import type { ApiResponse } from "@/types/api";

import type {
  AgentsFileListResponse,
  HookListResponse,
  RoleListResponse,
  RuleKindQueryRequest,
  RuleListResponse,
  SubagentListResponse,
  TemplateListResponse,
  WorkflowListResponse,
} from "../schemas";

/** One kind's rows of the named versions, by version and then by path; not paged. */
async function listKind<T>(route: string, params: RuleKindQueryRequest) {
  const res = await api.get<ApiResponse<T[]>>(route, {
    params,
    // The server reads a repeated `versionId=` and not the `versionId[]=` form.
    paramsSerializer: { indexes: null },
  });
  return res.data;
}

export function listRules(params: RuleKindQueryRequest) {
  return listKind<RuleListResponse>(API_ROUTES.rules.list, params);
}

/** By version and then by placement: the root's, `.agents/`'s, then a mounted pack's. */
export function listAgentsFiles(params: RuleKindQueryRequest) {
  return listKind<AgentsFileListResponse>(API_ROUTES.agentsFiles.list, params);
}

export function listRoles(params: RuleKindQueryRequest) {
  return listKind<RoleListResponse>(API_ROUTES.roles.list, params);
}

export function listSubagents(params: RuleKindQueryRequest) {
  return listKind<SubagentListResponse>(API_ROUTES.subagents.list, params);
}

export function listWorkflows(params: RuleKindQueryRequest) {
  return listKind<WorkflowListResponse>(API_ROUTES.workflows.list, params);
}

export function listTemplates(params: RuleKindQueryRequest) {
  return listKind<TemplateListResponse>(API_ROUTES.templates.list, params);
}

export function listHooks(params: RuleKindQueryRequest) {
  return listKind<HookListResponse>(API_ROUTES.hooks.list, params);
}
