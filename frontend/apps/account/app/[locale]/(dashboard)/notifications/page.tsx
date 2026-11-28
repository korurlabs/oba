/**
 * Notifications Page
 *
 * Not built yet: the page holds one panel that says what it will offer.
 */

import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { PageHeading } from "@oba/ui/sky/page-heading";
import { ComingSoonPanel } from "@/components/layout/page-heading";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("notifications");
  return {
    title: t("title"),
    description: t("subtitle"),
  };
}

export default async function NotificationsPage() {
  const t = await getTranslations("notifications");

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-6">
      <PageHeading title={t("title")} description={t("subtitle")} />

      <ComingSoonPanel
        description={t("panelSubtitle")}
        body={t("panelDescription")}
        comingSoon={t("comingSoon")}
      />
    </div>
  );
}
