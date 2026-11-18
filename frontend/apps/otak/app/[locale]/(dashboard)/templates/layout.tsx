import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("documentTemplates");
  return { title: t("title"), description: t("subtitle") };
}

export default function TemplatesLayout({ children }: { children: React.ReactNode }) {
  return children;
}
