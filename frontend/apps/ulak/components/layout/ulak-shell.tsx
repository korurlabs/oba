"use client";

import { Bot, KeyRound, Landmark, Mail, Settings, Users, UsersRound } from "lucide-react";
import { useTranslations } from "next-intl";
import type { ReactNode } from "react";
import { AppSwitcher } from "@oba/shared/components/app-header/app-switcher";
import { OrganizationSelector } from "@oba/shared/components/app-header/organization-selector";
import { ProfileDropdown } from "@oba/shared/components/app-header/profile-dropdown";
import { LanguageSwitcherSubMenu } from "@oba/shared/components/language-switcher";
import { ACCOUNT_LINKS } from "@oba/shared/constants/apps";
import { Link, usePathname } from "@oba/shared/libs/i18n/navigation";
import { SkyAppShell, type SkyNavItem, type SkyNavSection } from "@oba/ui/sky/app-shell";
import { OrganizationIdProvider } from "@/hooks/use-organization-id";
import { SUPPORTED_LOCALES } from "@/lib/i18n/supported-locales";
import { AccountSessionSync } from "./account-session-sync";
import { OrganizationGate } from "./organization-gate";
import { UlakAccessGate } from "./ulak-access-gate";
import { UlakLogo } from "./ulak-logo";

/**
 * Page frame of every signed-in Ulak page: the shared sky shell with Ulak's
 * wordmark, the organization selector, the app switcher and the account menu
 * in the top bar, and the pages of the current organization in the floating
 * menu. Every page administers the organization chosen in the header, so the
 * gate shows the page only for one the user manages. It also keeps the
 * session in step with the account app and sends a user who manages no
 * organization to the no-access page.
 *
 * `organizationId` is the default organization of the session the server
 * rendered with, so the first render keys its queries as the server did.
 */
export function UlakShell({
  organizationId,
  children,
}: {
  organizationId: string | null;
  children: ReactNode;
}) {
  const t = useTranslations("sidebar");
  const tApps = useTranslations("apps");
  // The i18n pathname has the locale prefix stripped, so it compares with the hrefs.
  const pathname = usePathname();
  const appName = tApps("ulak.name");
  // Profile is a page of the account app; Ulak owns only the organization
  // screens, so the user menu sends it there. Read on render, because the
  // account origin arrives with the run-time config.
  const profileHref = ACCOUNT_LINKS.profile;

  const sections: readonly SkyNavSection[] = [
    {
      label: t("navigation"),
      items: [
        { label: t("agents"), href: "/agents", icon: Bot },
        { label: t("humans"), href: "/humans", icon: Users },
        { label: t("groups"), href: "/groups", icon: UsersRound },
        { label: t("legalEntities"), href: "/legal-entities", icon: Landmark },
        { label: t("access"), href: "/access", icon: KeyRound },
        { label: t("invitations"), href: "/invitations", icon: Mail },
      ],
    },
  ];
  const footerItems: readonly SkyNavItem[] = [
    { label: t("organizationSettings"), href: "/settings", icon: Settings },
  ];

  return (
    <OrganizationIdProvider organizationId={organizationId}>
      <AccountSessionSync />
      <UlakAccessGate />
      <SkyAppShell
        logo={<UlakLogo className="h-6 w-auto sm:h-7" />}
        homeLabel={appName}
        actions={
          <>
            <OrganizationSelector managedOnly />
            <AppSwitcher currentAppId="ulak" />
            <ProfileDropdown
              profileHref={profileHref}
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
        footerItems={footerItems}
        pathname={pathname}
        linkComponent={Link}
      >
        <OrganizationGate>{children}</OrganizationGate>
      </SkyAppShell>
    </OrganizationIdProvider>
  );
}
