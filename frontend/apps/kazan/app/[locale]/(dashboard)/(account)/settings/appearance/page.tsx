import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { AppearanceSettings } from "@/features/settings/components/appearance-settings";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("appearance");
  return { title: t("title"), description: t("description") };
}

/**
 * Appearance Page
 *
 * Where the user picks Kazan's layout, the theme and the background of the
 * page area. The choices live in the user's preferences, which the dashboard
 * layout already hydrates, so the page renders the saved choices in the first
 * HTML payload.
 */
export default async function AppearancePage() {
  const t = await getTranslations("appearance");

  return (
    <div className="border-border bg-card mx-auto min-h-full max-w-3xl border-x px-8 py-10">
      <div className="mb-8">
        <h1 className="text-foreground text-3xl font-bold tracking-tight">{t("title")}</h1>
        <p className="text-muted-foreground mt-2 text-base">{t("description")}</p>
      </div>
      <AppearanceSettings />
    </div>
  );
}
