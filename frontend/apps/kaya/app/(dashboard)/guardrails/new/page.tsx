import type { Metadata } from "next";
import { Suspense } from "react";

import { GuardrailDetail } from "@/features/guardrails/components";

export const metadata: Metadata = {
  title: "New guardrail",
};

/** A new guardrail in the viewer's open draft. */
export default function NewGuardrailRoute() {
  return (
    <Suspense>
      <GuardrailDetail path={null} />
    </Suspense>
  );
}
