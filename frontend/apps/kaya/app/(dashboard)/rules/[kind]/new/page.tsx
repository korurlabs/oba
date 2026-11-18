import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { Suspense } from "react";

import { RuleDetail } from "@/features/rules/components";
import { kindOfSegment } from "@/features/rules/components/rule-links";

export const metadata: Metadata = {
  title: "New file",
};

/** A new rule or agent file of a tab, in the viewer's open draft. */
export default async function NewRuleFileRoute({ params }: { params: Promise<{ kind: string }> }) {
  const { kind } = await params;
  const tab = kindOfSegment(kind);
  if (tab === null) notFound();
  return (
    <Suspense>
      <RuleDetail tab={tab} path={null} />
    </Suspense>
  );
}
