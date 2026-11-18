import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { PAGE_TITLE_TEMPLATE } from "@/lib/page-title";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("relations");
  // A layout title given as a plain string stops the root template for every
  // child segment, so the create and detail pages would lose the brand. The
  // object form keeps the list title and passes the template on to them.
  return {
    title: { default: t("title"), template: PAGE_TITLE_TEMPLATE },
    description: t("subtitle"),
  };
}

export default function RelationsLayout({ children }: { children: React.ReactNode }) {
  return children;
}
