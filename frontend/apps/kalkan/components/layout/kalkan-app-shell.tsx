"use client";

import { forwardRef, type ReactNode } from "react";
import { useTranslations } from "next-intl";
import { AppSwitcher } from "@oba/shared/components/app-header/app-switcher";
import { OrganizationSelector } from "@oba/shared/components/app-header/organization-selector";
import { ProfileDropdown } from "@oba/shared/components/app-header/profile-dropdown";
import { LanguageSwitcherSubMenu } from "@oba/shared/components/language-switcher";
import { ACCOUNT_LINKS } from "@oba/shared/constants/apps";
import { Link, usePathname } from "@oba/shared/libs/i18n/navigation";
import { SkyTopBar, type SkyLinkProps } from "@oba/ui/sky/app-shell";
import { SidebarProvider } from "@oba/ui/sky/sidebar";
import { OrganizationIdProvider } from "@/hooks/use-organization-id";
import { SUPPORTED_LOCALES } from "@/lib/i18n/supported-locales";
import { AccountSessionSync } from "./account-session-sync";
import { KalkanLogo } from "./kalkan-logo";
import { KalkanSidebar } from "./kalkan-sidebar";
import { OrganizationGate } from "./organization-gate";

/**
 * The shell's menu and top bar link through the locale-aware link, so a page
 * keeps the visitor's language.
 */
const LocaleLink = forwardRef<HTMLAnchorElement, SkyLinkProps>(function LocaleLink(
  { href, ...props },
  ref
) {
  return <Link ref={ref} href={href} {...props} />;
});

/**
 * Page frame of every signed-in Kalkan page: the sky shell's top bar with the
 * organization the pages belong to, the app switcher and the account menu,
 * and Kalkan's floating menu beside the page. It lays the frame out as
 * `SkyAppShell` does, with Kalkan's own menu in place of the sky menu, whose
 * links cannot hold a page tree or more than one footer link. The account
 * session side effects run on every page, and a user without an organization
 * sees the gate instead of the page. `organizationId` is the default
 * organization of the session the server rendered with; see
 * `useOrganizationId`.
 */
export function KalkanAppShell({
  organizationId,
  children,
}: {
  organizationId: string | null;
  children: ReactNode;
}) {
  const t = useTranslations("sidebar");
  const tApps = useTranslations("apps");
  // The i18n pathname has the locale stripped, so it compares with the hrefs.
  const pathname = usePathname();
  // Profile and settings are pages of the account app; Kalkan owns only the
  // document pages, so the user menu sends them there. Read on render, because
  // the account origin arrives with the run-time config.
  const accountHrefs = { profile: ACCOUNT_LINKS.profile, settings: ACCOUNT_LINKS.settings };

  return (
    <OrganizationIdProvider organizationId={organizationId}>
      <SidebarProvider>
        <div className="flex h-svh w-full flex-col overflow-hidden">
          <SkyTopBar
            logo={<KalkanLogo className="h-6 w-auto sm:h-7" />}
            homeLabel={tApps("kalkan.name")}
            linkComponent={LocaleLink}
            actions={
              <>
                <OrganizationSelector />
                <AppSwitcher currentAppId="kalkan" />
                <ProfileDropdown
                  hrefs={accountHrefs}
                  extraMenuContent={
                    <LanguageSwitcherSubMenu
                      supportedLocales={SUPPORTED_LOCALES}
                      label={t("language")}
                    />
                  }
                />
              </>
            }
          />
          <div className="flex min-h-0 flex-1">
            <KalkanSidebar pathname={pathname} linkComponent={LocaleLink} />
            <main className="min-w-0 flex-1 overflow-auto">
              <div className="mx-auto w-full max-w-7xl px-4 py-6 sm:px-8 lg:py-10">
                <AccountSessionSync />
                <OrganizationGate>{children}</OrganizationGate>
              </div>
            </main>
          </div>
        </div>
      </SidebarProvider>
    </OrganizationIdProvider>
  );
}
