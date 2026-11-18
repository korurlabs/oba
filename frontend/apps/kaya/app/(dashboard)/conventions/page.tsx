import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { Suspense } from "react";

import { ConventionsPage } from "@/features/conventions/components";
import { legacyConventionRedirect } from "@/features/conventions/components/convention-links";

export const metadata: Metadata = {
  title: "Conventions",
};

type ConventionsRouteProps = {
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
 * The convention docs of a repository or pack; the scope lives in the URL. A
 * link that names a `tab` is one to the old tabbed page, which
 * `next.config.mjs` sends to /rules before this page renders. A link that
 * names a doc (`?file=`), such as one from a Links tab, leads to that doc's
 * own page, before the list renders.
 */
export default async function ConventionsRoute({ searchParams }: ConventionsRouteProps) {
  const target = legacyConventionRedirect(queryOf(await searchParams));
  if (target !== null) redirect(target);
  return (
    <Suspense>
      <ConventionsPage />
    </Suspense>
  );
}
