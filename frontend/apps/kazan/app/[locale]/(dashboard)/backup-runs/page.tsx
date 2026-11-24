import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { EmptyState } from "@oba/ui/sky/page-states";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("backupRuns");
  return { title: t("title") };
}

/**
 * Backup Runs: each backup of the organization's systems and whether it
 * succeeded. Kazan does not track them yet, so the page shows only its
 * heading and an empty state, and sends no request.
 */
export default async function BackupRunsPage() {
  const t = await getTranslations("backupRuns");

  return (
    <div className="mx-auto flex w-full max-w-6xl flex-col gap-6 px-4 py-6 sm:px-6 sm:py-8">
      <header className="flex flex-col gap-1">
        <h1 className="text-foreground text-2xl font-semibold">{t("title")}</h1>
        <p className="text-muted-foreground text-sm">{t("description")}</p>
      </header>
      <EmptyState title={t("emptyTitle")} description={t("emptyDescription")} />
    </div>
  );
}
