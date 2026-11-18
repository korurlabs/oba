"use client";

import Image from "next/image";
import { useTranslations } from "next-intl";
import { AppSwitcher } from "@oba/shared/components/app-header/app-switcher";
import { OrganizationSelector } from "@oba/shared/components/app-header/organization-selector";
import { ProfileDropdown } from "@oba/shared/components/app-header/profile-dropdown";
import { LanguageSwitcherSubMenu } from "@oba/shared/components/language-switcher";
import { ACCOUNT_LINKS } from "@oba/shared/constants/apps";
import { Link } from "@oba/shared/libs/i18n/navigation";
import { SUPPORTED_LOCALES } from "@/lib/i18n/supported-locales";

/**
 * Profile and settings are pages of the account app; Kalkan owns only the
 * document pages, so the user menu sends them there. Built on render, because
 * the account origin is known only once the run-time config has arrived.
 */
const accountHrefs = () => ({
  profile: ACCOUNT_LINKS.profile,
  settings: ACCOUNT_LINKS.settings,
});

/**
 * Top bar of every Kalkan page: the app switcher, the brand, the organization
 * the pages belong to and the user menu. The pages themselves are reached
 * from the sidebar.
 */
export function KalkanHeader() {
  const t = useTranslations("sidebar");
  const tApps = useTranslations("apps");

  return (
    <header className="bg-card border-border sticky top-0 z-40 w-full border-b">
      <div className="flex h-14 items-center gap-3 px-4">
        <AppSwitcher currentAppId="kalkan" />
        <Link href="/" className="flex shrink-0 items-center gap-2">
          <Image
            src="/assets/korur-mark.png"
            alt=""
            width={24}
            height={24}
            className="rounded bg-white"
          />
          <span className="text-sm font-semibold">{tApps("kalkan.name")}</span>
        </Link>

        <div className="ml-auto flex min-w-0 items-center gap-3">
          <OrganizationSelector />
          <ProfileDropdown
            hrefs={accountHrefs()}
            extraMenuContent={
              <LanguageSwitcherSubMenu supportedLocales={SUPPORTED_LOCALES} label={t("language")} />
            }
          />
        </div>
      </div>
    </header>
  );
}
