import type { Metadata } from "next";
import { Suspense } from "react";

import { GuardrailsPage } from "@/features/guardrails/components";

export const metadata: Metadata = {
  title: "Guardrails",
};

/** The guardrail rules of a repository or pack; the scope lives in the URL. */
export default function GuardrailsRoute() {
  return (
    <Suspense>
      <GuardrailsPage />
    </Suspense>
  );
}
