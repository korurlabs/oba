import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { PageCreateView } from "@/features/pages/components/page-create-view";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("sidebar");
  return { title: t("newPage") };
}

/** New Page: the form that creates a page. The dashboard layout gates auth. */
export default function NewPagePage() {
  return <PageCreateView />;
}
