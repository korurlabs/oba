import type { AgentProvider } from "../schemas";

/**
 * How a provider names the two values that fire its routine, as message keys
 * under `agents.routine`, and the format of its routine ids. Each vendor
 * labels them in its own words, so the dialog uses the words the ADMIN sees
 * where they copy the values from.
 */
export interface RoutineFields {
  idLabelKey: "anthropicIdLabel" | "idLabel";
  secretLabelKey: "anthropicSecretLabel" | "secretLabel";
  idPlaceholder: string;
  idPattern: RegExp | null;
}

/** The fields of a provider whose routines this client knows no format for: the API checks them. */
const GENERIC_ROUTINE_FIELDS: RoutineFields = {
  idLabelKey: "idLabel",
  secretLabelKey: "secretLabel",
  idPlaceholder: "",
  idPattern: null,
};

/** Anthropic routines: the API trigger's `trig_` id and its fire token. */
const ROUTINE_FIELDS_OF: Partial<Record<AgentProvider, RoutineFields>> = {
  ANTHROPIC: {
    idLabelKey: "anthropicIdLabel",
    secretLabelKey: "anthropicSecretLabel",
    idPlaceholder: "trig_...",
    idPattern: /^trig_[A-Za-z0-9]{1,60}$/,
  },
};

export function routineFieldsOf(provider: AgentProvider): RoutineFields {
  return ROUTINE_FIELDS_OF[provider] ?? GENERIC_ROUTINE_FIELDS;
}
