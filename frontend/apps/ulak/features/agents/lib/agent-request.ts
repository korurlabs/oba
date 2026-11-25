import type {
  AgentCreateRequest,
  AgentFormValues,
  AgentRoutineFormValues,
  AgentRoutineRequest,
  AgentUpdateRequest,
} from "../schemas";

/** The create body of a filled agent form; the name is sent trimmed, as it was checked. */
export function toAgentCreateRequest(values: AgentFormValues): AgentCreateRequest {
  return {
    name: values.name.trim(),
    flow: values.flow,
    provider: values.provider,
    role: values.role,
    apps: values.apps,
  };
}

/** The update body of a filled agent form: flow and provider are fixed, so they are left out. */
export function toAgentUpdateRequest(values: AgentFormValues): AgentUpdateRequest {
  return {
    name: values.name.trim(),
    role: values.role,
    apps: values.apps,
  };
}

export function toAgentRoutineRequest(values: AgentRoutineFormValues): AgentRoutineRequest {
  return {
    routineId: values.routineId.trim(),
    routineSecret: values.routineSecret.trim(),
  };
}
