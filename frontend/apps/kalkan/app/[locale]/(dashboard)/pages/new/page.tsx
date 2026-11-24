import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { PageCreateView } from "@/features/pages/components/page-create-view";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("pageForm");
  return { title: t("newTitle") };
}

/**
 * New Page: the form that creates a page under `?parent=`. The dashboard
 * layout gates auth and prefetches the page tree the parent choice reads.
 */
export default function NewPagePage() {
  return <PageCreateView />;
}
