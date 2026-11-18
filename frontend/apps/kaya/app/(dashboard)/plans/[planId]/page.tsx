import type { Metadata } from "next";

import { PlanDetail } from "@/features/plans/components";

export const metadata: Metadata = {
  title: "Plan",
};

/** One plan with its outline, document, versions, feedback and planning handoff. */
export default async function PlanRoute({ params }: { params: Promise<{ planId: string }> }) {
  const { planId } = await params;
  return <PlanDetail planId={planId} />;
}
