import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { Suspense } from "react";

import { SkillsPage } from "@/features/skills/components";
import { legacySkillRedirect } from "@/features/skills/components/skill-links";

export const metadata: Metadata = {
  title: "Skills",
};

type SkillsRouteProps = {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
};

function queryOf(params: Record<string, string | string[] | undefined>): URLSearchParams {
  const query = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    for (const entry of Array.isArray(value) ? value : value === undefined ? [] : [value]) {
      query.append(key, entry);
    }
  }
  return query;
}

/**
 * The skills of a repository or pack; the scope lives in the URL. An older
 * link that names a skill (`?skill=`) leads to that skill's own page, before
 * the list renders.
 */
export default async function SkillsRoute({ searchParams }: SkillsRouteProps) {
  const target = legacySkillRedirect(queryOf(await searchParams));
  if (target !== null) redirect(target);
  return (
    <Suspense>
      <SkillsPage />
    </Suspense>
  );
}
