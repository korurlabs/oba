/**
 * The addresses of the Guardrails list and of one guardrail's detail page,
 * and the guardrails as a section of the Rules tab. A guardrail is addressed
 * by its repository path, one URL segment, like a Rules file.
 */

import { ruleDetailHref, withKept, type RuleSection } from "@/features/rules/components/rule-links";
import type { MountRef } from "@/features/scope/components/mount-paths";
import { ruleTabOf } from "@/features/scope/components/scope-files";

import { guardrailFiles, isGuardrailPath, newGuardrailStart } from "./guardrail-files";

export function guardrailsListHref(current: URLSearchParams): string {
  return withKept("/guardrails", current);
}

export function guardrailDetailHref(path: string, current: URLSearchParams): string {
  return withKept(`/guardrails/${encodeURIComponent(path)}`, current);
}

export function guardrailNewHref(current: URLSearchParams): string {
  return withKept("/guardrails/new", current);
}

/**
 * Where a file shows after a save: its guardrail page, or its Rules page
 * when the save moved it out of the guardrails folder.
 */
export function guardrailSavedHref(path: string, current: URLSearchParams): string {
  return isGuardrailPath(path)
    ? guardrailDetailHref(path, current)
    : ruleDetailHref(ruleTabOf(path) ?? "rules", path, current);
}

/** The guardrails: the rules under the guardrails folder, described by the rule list. */
export const GUARDRAILS_SECTION: RuleSection = {
  tab: "rules",
  title: "Guardrails",
  description: "The locked rules a repository or pack delivers.",
  back: "Back to Guardrails",
  newTitle: "New guardrail",
  files: guardrailFiles,
  listHref: guardrailsListHref,
  detailHref: guardrailDetailHref,
  savedHref: guardrailSavedHref,
  newStart: (mount: MountRef | null) => {
    const path = newGuardrailStart(mount);
    return path === null ? null : { path, executable: false };
  },
  mountedRefusal:
    "A pack mounted in a folder cannot add guardrails: they steer the whole repository, so they stay with the packs at the repository root.",
};
