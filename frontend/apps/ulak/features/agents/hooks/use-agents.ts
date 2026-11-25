"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { useTranslations } from "next-intl";
import { isApiError } from "@oba/shared/api/axios";
import { APP_ACCESS_KEYS } from "@/features/organizations/hooks/query-keys";
import {
  createAgent,
  deleteAgent,
  getAgentOptions,
  issueAgentToken,
  listAgents,
  removeAgentRoutine,
  revokeAgentToken,
  setAgentRoutine,
  updateAgent,
  updateAgentStatus,
} from "../api";
import type {
  AgentCreateRequest,
  AgentRoutineRequest,
  AgentStatusUpdate,
  AgentUpdateRequest,
} from "../schemas";
import { AGENT_KEYS } from "./query-keys";

/**
 * Query keys live in the boundary-safe `./query-keys` module (no "use client").
 * Re-exported here for client consumers.
 */
export { AGENT_KEYS };

export interface AgentFormMutationOptions {
  /**
   * Error codes that the caller shows on its form as a field error. The hook
   * shows no toast for them, so the user does not read the same error twice.
   */
  formErrorCodes?: readonly string[];
}

function isFormError(error: Error, options: AgentFormMutationOptions | undefined): boolean {
  return options?.formErrorCodes?.some((code) => isApiError(error, code)) ?? false;
}

export function useAgents(organizationId: string | null | undefined) {
  return useQuery({
    queryKey: AGENT_KEYS.list(organizationId ?? ""),
    queryFn: () => listAgents(organizationId!),
    enabled: !!organizationId,
  });
}

/** The providers each flow accepts. Read only while a form needs them. */
export function useAgentOptions(organizationId: string | null | undefined, enabled = true) {
  return useQuery({
    queryKey: AGENT_KEYS.options(organizationId ?? ""),
    queryFn: () => getAgentOptions(organizationId!),
    enabled: !!organizationId && enabled,
  });
}

/**
 * An agent's apps are USER grants, so a change to them, or the agent's
 * removal, also changes the organization's grant list.
 */
function invalidateAgentsAndGrants(
  queryClient: ReturnType<typeof useQueryClient>,
  organizationId: string
) {
  return Promise.all([
    queryClient.invalidateQueries({ queryKey: AGENT_KEYS.list(organizationId) }),
    queryClient.invalidateQueries({ queryKey: APP_ACCESS_KEYS.organization(organizationId) }),
  ]);
}

export function useCreateAgent(organizationId: string, options?: AgentFormMutationOptions) {
  const t = useTranslations("agents");
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: AgentCreateRequest) => createAgent(organizationId, data),
    onSuccess: () => {
      toast.success(t("createSuccess"));
      // Returned so the mutation stays pending until the lists have refetched.
      return invalidateAgentsAndGrants(queryClient, organizationId);
    },
    onError: (error: Error) => {
      if (isFormError(error, options)) return;
      toast.error(error.message || t("createError"));
    },
  });
}

export function useUpdateAgent(organizationId: string, options?: AgentFormMutationOptions) {
  const t = useTranslations("agents");
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ agentId, data }: { agentId: string; data: AgentUpdateRequest }) =>
      updateAgent(organizationId, agentId, data),
    onSuccess: () => {
      toast.success(t("updateSuccess"));
      return invalidateAgentsAndGrants(queryClient, organizationId);
    },
    onError: (error: Error) => {
      if (isFormError(error, options)) return;
      toast.error(error.message || t("updateError"));
    },
  });
}

export function useUpdateAgentStatus(organizationId: string) {
  const t = useTranslations("agents");
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ agentId, status }: { agentId: string; status: AgentStatusUpdate }) =>
      updateAgentStatus(organizationId, agentId, status),
    onSuccess: (_, { status }) => {
      toast.success(status === "DISABLED" ? t("disableSuccess") : t("enableSuccess"));
      return queryClient.invalidateQueries({ queryKey: AGENT_KEYS.list(organizationId) });
    },
    onError: (error: Error) => {
      toast.error(error.message || t("statusError"));
    },
  });
}

export function useDeleteAgent(organizationId: string) {
  const t = useTranslations("agents");
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (agentId: string) => deleteAgent(organizationId, agentId),
    onSuccess: () => {
      toast.success(t("deleteSuccess"));
      return invalidateAgentsAndGrants(queryClient, organizationId);
    },
    onError: (error: Error) => {
      toast.error(error.message || t("deleteError"));
    },
  });
}

/**
 * Issues the agent's token. The plaintext is the mutation's result: the
 * caller copies it into its own state and resets the mutation, and `gcTime: 0`
 * drops the finished mutation at once, so the token never stays in a cache.
 */
export function useIssueAgentToken(organizationId: string) {
  const t = useTranslations("agents");
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (agentId: string) => issueAgentToken(organizationId, agentId),
    gcTime: 0,
    onSuccess: () => {
      toast.success(t("token.issueSuccess"));
      return queryClient.invalidateQueries({ queryKey: AGENT_KEYS.list(organizationId) });
    },
    onError: (error: Error) => {
      toast.error(error.message || t("token.issueError"));
    },
  });
}

export function useRevokeAgentToken(organizationId: string) {
  const t = useTranslations("agents");
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (agentId: string) => revokeAgentToken(organizationId, agentId),
    onSuccess: () => {
      toast.success(t("token.revokeSuccess"));
      return queryClient.invalidateQueries({ queryKey: AGENT_KEYS.list(organizationId) });
    },
    onError: (error: Error) => {
      toast.error(error.message || t("token.revokeError"));
    },
  });
}

export function useSetAgentRoutine(organizationId: string, options?: AgentFormMutationOptions) {
  const t = useTranslations("agents");
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ agentId, data }: { agentId: string; data: AgentRoutineRequest }) =>
      setAgentRoutine(organizationId, agentId, data),
    onSuccess: () => {
      toast.success(t("routine.setSuccess"));
      return queryClient.invalidateQueries({ queryKey: AGENT_KEYS.list(organizationId) });
    },
    onError: (error: Error) => {
      if (isFormError(error, options)) return;
      toast.error(error.message || t("routine.setError"));
    },
  });
}

export function useRemoveAgentRoutine(organizationId: string) {
  const t = useTranslations("agents");
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (agentId: string) => removeAgentRoutine(organizationId, agentId),
    onSuccess: () => {
      toast.success(t("routine.removeSuccess"));
      return queryClient.invalidateQueries({ queryKey: AGENT_KEYS.list(organizationId) });
    },
    onError: (error: Error) => {
      toast.error(error.message || t("routine.removeError"));
    },
  });
}
