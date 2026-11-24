import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { OrganizationsView } from "@/features/organizations/components/organizations-view";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("organization.list");
  return {
    title: t("title"),
    description: t("subtitle"),
  };
}

/**
 * Organizations Page
 *
 * OrganizationsView reads the memberships from the current-user ("me") query,
 * which the dashboard layout prefetches and hydrates for the whole shell (see
 * server-prefetch-convention). The dashboard layout also gates auth; this page
 * adds neither.
 */
export default function OrganizationsPage() {
  return <OrganizationsView />;
}
