"use client";

import { forwardRef, type ReactNode } from "react";
import { History, Trash2 } from "lucide-react";
import { useTranslations } from "next-intl";
import { AppSwitcher } from "@oba/shared/components/app-header/app-switcher";
import { OrganizationSelector } from "@oba/shared/components/app-header/organization-selector";
import { ProfileDropdown } from "@oba/shared/components/app-header/profile-dropdown";
import { LanguageSwitcherSubMenu } from "@oba/shared/components/language-switcher";
import { ACCOUNT_LINKS } from "@oba/shared/constants/apps";
import { GrantedApp } from "@oba/shared/features/users/schema";
import { Link, usePathname } from "@oba/shared/libs/i18n/navigation";
import { SkyAppShell, type SkyLinkProps, type SkyNavItem } from "@oba/ui/sky/app-shell";
import { useIsOrganizationMember } from "@/hooks/use-is-organization-admin";
import { OrganizationIdProvider } from "@/hooks/use-organization-id";
import { SUPPORTED_LOCALES } from "@/lib/i18n/supported-locales";
import { RECENT_HREF, TRASH_HREF } from "@/lib/routes";
import { AccountSessionSync } from "./account-session-sync";
import { KalkanLogo } from "./kalkan-logo";
import { KalkanMenu } from "./kalkan-menu";
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
 * Page frame of every signed-in Kalkan page: the shared sky shell with
 * Kalkan's page tree as its menu and Recent and Trash at the menu's foot, and
 * the organization selector (offering the organizations that grant Kalkan),
 * the app switcher and the account menu in the top bar. A guest cannot write,
 * so Trash shows only to a member or above. The account session side effects
 * run on every page, and a user without an organization sees the gate
 * instead of the page. `organizationId` is the default organization of the
 * session the server rendered with; see `useOrganizationId`.
 */
export function KalkanAppShell({
  organizationId,
  children,
}: {
  organizationId: string | null;
  children: ReactNode;
}) {
  return (
    <OrganizationIdProvider organizationId={organizationId}>
      <KalkanFrame>{children}</KalkanFrame>
    </OrganizationIdProvider>
  );
}

/**
 * The frame inside the organization's provider, since the menu and the
 * member check read the organization.
 */
function KalkanFrame({ children }: { children: ReactNode }) {
  const t = useTranslations("sidebar");
  const tApps = useTranslations("apps");
  const isMember = useIsOrganizationMember();
  // The i18n pathname has the locale stripped, so it compares with the hrefs.
  const pathname = usePathname();
  // Profile is a page of the account app; Kalkan owns only the document
  // pages, so the user menu sends it there. Read on render, because the
  // account origin arrives with the run-time config.
  const profileHref = ACCOUNT_LINKS.profile;
  const footerItems: SkyNavItem[] = [
    { label: t("recent"), href: RECENT_HREF, icon: History },
    ...(isMember ? [{ label: t("trash"), href: TRASH_HREF, icon: Trash2 }] : []),
  ];

  return (
    <SkyAppShell
      logo={<KalkanLogo className="h-6 w-auto sm:h-7" />}
      homeLabel={tApps("kalkan.name")}
      linkComponent={LocaleLink}
      pathname={pathname}
      actions={
        <>
          <OrganizationSelector app={GrantedApp.KALKAN} />
          <AppSwitcher currentAppId="kalkan" />
          <ProfileDropdown
            profileHref={profileHref}
            extraMenuContent={
              <LanguageSwitcherSubMenu supportedLocales={SUPPORTED_LOCALES} label={t("language")} />
            }
          />
        </>
      }
      menu={<KalkanMenu pathname={pathname} link={LocaleLink} />}
      footerItems={footerItems}
    >
      <AccountSessionSync />
      <OrganizationGate>{children}</OrganizationGate>
    </SkyAppShell>
  );
}
