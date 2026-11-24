import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { EmptyState } from "@oba/ui/sky/page-states";
import { PageHeader } from "@/components/layout/page-header";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("sidebar");
  return { title: t("agents") };
}

/**
 * Agents: the AI agents of the current organization. Oba has no agent
 * identity yet, so the page shows only its heading and an empty state, and
 * sends no request.
 */
export default async function AgentsPage() {
  const t = await getTranslations("agents");

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title={t("title")} description={t("description")} />
      <EmptyState title={t("emptyTitle")} description={t("emptyDescription")} />
    </div>
  );
}
