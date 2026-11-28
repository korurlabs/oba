import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { Suspense } from "react";

import { RuleDetail } from "@/features/rules/components";
import { RULE_NEW_LABELS, kindOfSegment } from "@/features/rules/components/rule-links";

/** The tab's create label, such as "New rule", which the page's heading also shows. */
export async function generateMetadata({
  params,
}: {
  params: Promise<{ kind: string }>;
}): Promise<Metadata> {
  const tab = kindOfSegment((await params).kind);
  return { title: tab === null ? "New file" : RULE_NEW_LABELS[tab] };
}

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
