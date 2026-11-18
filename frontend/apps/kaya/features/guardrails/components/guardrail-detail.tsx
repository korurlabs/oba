"use client";

import { RuleSectionDetail } from "@/features/rules/components/rule-detail";

import { GUARDRAILS_SECTION } from "./guardrail-links";

/** A guardrail's own page, or the page that starts a new one, in the scope the URL names. */
export function GuardrailDetail({ path }: { path: string | null }) {
  return <RuleSectionDetail section={GUARDRAILS_SECTION} path={path} />;
}
