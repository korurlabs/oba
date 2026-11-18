import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { Suspense } from "react";

import { RulesPage } from "@/features/rules/components";
import { legacyFileRedirect } from "@/features/rules/components/rule-links";

export const metadata: Metadata = {
  title: "Rules",
};

type RulesRouteProps = {
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
 * The rule and agent files of a repository or pack; the scope and tab live in
 * the URL. An older link that names a file (`?file=`) leads to that file's
 * own page, before the list renders.
 */
export default async function RulesRoute({ searchParams }: RulesRouteProps) {
  const target = legacyFileRedirect(queryOf(await searchParams));
  if (target !== null) redirect(target);
  return (
    <Suspense>
      <RulesPage />
    </Suspense>
  );
}
