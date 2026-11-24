import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { EmptyState } from "@/components/ui/empty-state";
import { PageHeader } from "@/components/ui/page-header";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("disasterDrills");
  return { title: t("title") };
}

/**
 * Disaster Drills: the planned exercises that prove the organization can
 * recover its work after an outage. Kazan does not track them yet, so the
 * page shows only its heading and an empty state, and sends no request.
 */
export default async function DisasterDrillsPage() {
  const t = await getTranslations("disasterDrills");

  return (
    <div className="mx-auto flex w-full max-w-6xl flex-col gap-6 px-4 py-6 sm:px-6 sm:py-8">
      <PageHeader title={t("title")} description={t("description")} />
      <div className="kazan-grid-frame bg-card py-10">
        <EmptyState title={t("emptyTitle")} description={t("emptyDescription")} />
      </div>
    </div>
  );
}
