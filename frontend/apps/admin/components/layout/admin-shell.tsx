"use client";

import Image from "next/image";
import { Building2 } from "lucide-react";
import { useTranslations } from "next-intl";
import type { ReactNode } from "react";
import { AppSwitcher } from "@oba/shared/components/app-header/app-switcher";
import { ProfileDropdown } from "@oba/shared/components/app-header/profile-dropdown";
import { LanguageSwitcherSubMenu } from "@oba/shared/components/language-switcher";
import { ACCOUNT_LINKS } from "@oba/shared/constants/apps";
import { Link, usePathname } from "@oba/shared/libs/i18n/navigation";
import { matchesPath, SkyAppShell, type SkyNavSection } from "@oba/ui/sky/app-shell";
import { SUPPORTED_LOCALES } from "@/lib/i18n/supported-locales";
import { AccountSessionSync } from "./account-session-sync";
import { AdminAccessGate } from "./admin-access-gate";

/**
 * Profile and settings are pages of the account app; Admin owns only the
 * organization screens, so the user menu sends them there. Built on render,
 * because the account app's origin is known only once the run-time config
 * has arrived.
 */
const accountHrefs = () => ({
  profile: ACCOUNT_LINKS.profile,
  settings: ACCOUNT_LINKS.settings,
});

/**
 * Whether the page in view belongs to Organizations. The organization list is
 * Admin's home page at `/`, while an organization's pages live under
 * `/organizations`, so the link owns both.
 */
export function isOrganizationsPage(pathname: string): boolean {
  return pathname === "/" || matchesPath(pathname, "/organizations");
}

/**
 * Page frame of every signed-in Admin page, shared by the organization list
 * (which is also the sign-in gateway) and the dashboard layout: the shared
 * sky shell, as in Kaya, with Admin's wordmark, the app switcher and the
 * account menu in the top bar and Organizations in the floating menu. It also
 * keeps the session in step with the account app and sends a user who
 * manages no organization to the no-access page.
 */
export function AdminShell({ children }: { children: ReactNode }) {
  const t = useTranslations("sidebar");
  const tApps = useTranslations("apps");
  // The i18n pathname has the locale prefix stripped, so it compares with the hrefs.
  const pathname = usePathname();
  const appName = tApps("admin.name");

  const sections: readonly SkyNavSection[] = [
    {
      label: t("navigation"),
      items: [
        {
          label: t("organizations"),
          href: "/",
          icon: Building2,
          isActive: isOrganizationsPage,
        },
      ],
    },
  ];

  return (
    <>
      <AccountSessionSync />
      <AdminAccessGate />
      <SkyAppShell
        logo={
          <span className="flex items-center gap-2">
            <Image
              src="/assets/korur-mark.png"
              alt=""
              width={24}
              height={24}
              className="size-6 object-contain"
            />
            <span className="text-foreground text-base font-bold tracking-tight">{appName}</span>
          </span>
        }
        homeLabel={appName}
        actions={
          <>
            <AppSwitcher currentAppId="admin" />
            <ProfileDropdown
              hrefs={accountHrefs()}
              extraMenuContent={
                <LanguageSwitcherSubMenu
                  supportedLocales={SUPPORTED_LOCALES}
                  label={t("language")}
                />
              }
            />
          </>
        }
        sections={sections}
        pathname={pathname}
        linkComponent={Link}
      >
        {children}
      </SkyAppShell>
    </>
  );
}
