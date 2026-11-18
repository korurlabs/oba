import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { Suspense } from "react";

import { ConventionDetail } from "@/features/conventions/components";

export const metadata: Metadata = {
  title: "Convention doc",
};

type ConventionDocRouteProps = {
  params: Promise<{ id: string }>;
};

/**
 * The path a segment names. The path is one segment, its slashes encoded,
 * and the segment arrives still encoded, so it is decoded once here; a
 * segment that is no valid encoding names no doc.
 */
function pathOf(segment: string): string | null {
  try {
    return decodeURIComponent(segment);
  } catch (failure: unknown) {
    if (failure instanceof URIError) return null;
    throw failure;
  }
}

/** One convention doc of a scope, addressed by its repository path. */
export default async function ConventionDocRoute({ params }: ConventionDocRouteProps) {
  const { id } = await params;
  const path = pathOf(id);
  if (path === null || path === "") notFound();
  return (
    <Suspense>
      <ConventionDetail path={path} />
    </Suspense>
  );
}
