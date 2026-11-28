import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("mailPage");
  return { title: t("title"), description: t("subtitle") };
}

export default function MailLayout({ children }: { children: React.ReactNode }) {
  return children;
}
