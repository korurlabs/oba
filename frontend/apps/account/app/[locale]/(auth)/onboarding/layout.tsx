import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("onboarding");
  return {
    title: t("title"),
    description: t("description"),
  };
}

export default function OnboardingLayout({ children }: { children: React.ReactNode }) {
  return <div className="w-full min-w-0">{children}</div>;
}
