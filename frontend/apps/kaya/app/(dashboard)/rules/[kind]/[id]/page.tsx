import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { Suspense } from "react";

import { RuleDetail } from "@/features/rules/components";
import { kindOfSegment } from "@/features/rules/components/rule-links";

export const metadata: Metadata = {
  title: "Rules file",
};

type RuleFileRouteProps = {
  params: Promise<{ kind: string; id: string }>;
};

/**
 * The path a segment names. The path is one segment, its slashes encoded,
 * and the segment arrives still encoded, so it is decoded once here; a
 * segment that is no valid encoding names no file.
 */
function pathOf(segment: string): string | null {
  try {
    return decodeURIComponent(segment);
  } catch (failure: unknown) {
    if (failure instanceof URIError) return null;
    throw failure;
  }
}

/** One rule or agent file of a scope, addressed by its tab and repository path. */
export default async function RuleFileRoute({ params }: RuleFileRouteProps) {
  const { kind, id } = await params;
  const tab = kindOfSegment(kind);
  const path = pathOf(id);
  if (tab === null || path === null || path === "") notFound();
  return (
    <Suspense>
      <RuleDetail tab={tab} path={path} />
    </Suspense>
  );
}
