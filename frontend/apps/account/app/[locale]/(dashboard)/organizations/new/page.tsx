import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { CreateOrganizationForm } from "@/features/organizations/components/create-organization-form";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("organization.new");
  return {
    title: t("title"),
    description: t("description"),
  };
}

/**
 * New Organization Page
 *
 * Thin page: the form lives in the organizations feature. Nothing is
 * prefetched, because the form fires no query on mount.
 */
export default function NewOrganizationPage() {
  return <CreateOrganizationForm />;
}
