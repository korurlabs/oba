import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("icps");
  return { title: t("title"), description: t("subtitle") };
}

export default function IcpsLayout({ children }: { children: React.ReactNode }) {
  return children;
}
