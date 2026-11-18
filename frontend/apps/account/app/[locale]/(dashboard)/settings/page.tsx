/**
 * Settings Page
 *
 * Application settings and preferences: the language, appearance,
 * integrations and data & privacy panels.
 */

import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { LanguageSwitcher } from "@oba/shared/components/language-switcher";
import { Card, CardAction, CardDescription, CardHeader } from "@oba/ui/sky/card";
import { ComingSoonPanel, PageHeading, PanelTitle } from "@/components/layout/page-heading";
import { AppearanceCard } from "@/features/settings/components/appearance-card";
import { SUPPORTED_LOCALES } from "@/lib/i18n/supported-locales";

export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("settings");
  return {
    title: t("title"),
    description: t("subtitle"),
  };
}

export default async function SettingsPage() {
  const t = await getTranslations();

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-6">
      <PageHeading title={t("settings.title")} description={t("settings.subtitle")} />

      <Card role="region" aria-labelledby="settings-language-heading">
        <CardHeader>
          <PanelTitle id="settings-language-heading">{t("settings.language")}</PanelTitle>
          <CardDescription>{t("settings.languageSubtitle")}</CardDescription>
          <CardAction>
            <LanguageSwitcher supportedLocales={SUPPORTED_LOCALES} />
          </CardAction>
        </CardHeader>
      </Card>

      <AppearanceCard />

      {/* Integrations and data & privacy: not built yet */}
      <div className="grid gap-6 md:grid-cols-2">
        <ComingSoonPanel
          id="settings-integrations-heading"
          title={t("settings.integrations")}
          description={t("settings.integrationsSubtitle")}
          body={t("settings.integrationsDescription")}
          comingSoon={t("settings.comingSoon")}
        />
        <ComingSoonPanel
          id="settings-data-privacy-heading"
          title={t("settings.dataPrivacy")}
          description={t("settings.dataPrivacySubtitle")}
          body={t("settings.dataPrivacyDescription")}
          comingSoon={t("settings.comingSoon")}
        />
      </div>
    </div>
  );
}
