import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { Suspense } from "react";

import { GuardrailDetail } from "@/features/guardrails/components";
import { pathOfSegment } from "@/features/rules/components/rule-links";

export const metadata: Metadata = {
  title: "Guardrail",
};

type GuardrailRouteProps = {
  params: Promise<{ id: string }>;
};

/** One guardrail of a scope, addressed by its repository path. */
export default async function GuardrailRoute({ params }: GuardrailRouteProps) {
  const { id } = await params;
  const path = pathOfSegment(id);
  if (path === null) notFound();
  return (
    <Suspense>
      <GuardrailDetail path={path} />
    </Suspense>
  );
}
