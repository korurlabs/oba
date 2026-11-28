import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { PAGE_TITLE_TEMPLATE } from "@/lib/page-title";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("campaigns");
  // The object form keeps the list title and passes the root template on to
  // the campaign page, which a plain string title would stop.
  return {
    title: { default: t("title"), template: PAGE_TITLE_TEMPLATE },
    description: t("subtitle"),
  };
}

export default function CampaignsLayout({ children }: { children: React.ReactNode }) {
  return children;
}
