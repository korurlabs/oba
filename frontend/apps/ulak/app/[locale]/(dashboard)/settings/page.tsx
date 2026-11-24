import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { dehydrate, HydrationBoundary } from "@tanstack/react-query";
import { makeQueryClient } from "@oba/shared/providers/query-client";
import { CurrentOrganizationPage } from "@/features/organizations/components/current-organization-page";
import { prefetchCurrentOrganization } from "../_lib/prefetch-current-organization";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("sidebar");
  return { title: t("organizationSettings") };
}

/**
 * Organization settings: the details of the current organization. The organization is the one chosen in the header; the dashboard
 * layout gates auth and the shell's organization gate decides whether the
 * user manages it.
 */
export default async function OrganizationSettingsPage() {
  const queryClient = makeQueryClient();
  await prefetchCurrentOrganization(queryClient, "settings");

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <CurrentOrganizationPage section="settings" />
    </HydrationBoundary>
  );
}
