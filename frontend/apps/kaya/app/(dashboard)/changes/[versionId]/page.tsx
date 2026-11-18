import type { Metadata } from "next";

import { ChangeDetail } from "@/features/changes/components";

export const metadata: Metadata = {
  title: "Change",
};

/** One config change with its diff, reviews and the viewer's actions. */
export default async function ChangeRoute({ params }: { params: Promise<{ versionId: string }> }) {
  const { versionId } = await params;
  return <ChangeDetail versionId={versionId} />;
}
