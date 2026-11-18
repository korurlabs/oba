import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { Suspense } from "react";

import { pathOfSegment } from "@/features/rules/components/rule-links";
import { SkillDetail } from "@/features/skills/components";

export const metadata: Metadata = {
  title: "Skill",
};

type SkillRouteProps = {
  params: Promise<{ id: string }>;
};

/** One skill of a scope, addressed by its folder; the open file lives in the URL. */
export default async function SkillRoute({ params }: SkillRouteProps) {
  const { id } = await params;
  const directory = pathOfSegment(id);
  if (directory === null) notFound();
  return (
    <Suspense>
      <SkillDetail directory={directory} />
    </Suspense>
  );
}
