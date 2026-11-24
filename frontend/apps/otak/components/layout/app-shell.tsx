"use client";

import { forwardRef, useMemo, type ReactNode } from "react";
import {
  Building2,
  CalendarDays,
  Crosshair,
  FileText,
  Kanban,
  LayoutTemplate,
  Mail,
  Send,
} from "lucide-react";
import { useTranslations } from "next-intl";
import { AppSwitcher } from "@oba/shared/components/app-header/app-switcher";
import { OrganizationSelector } from "@oba/shared/components/app-header/organization-selector";
import { ProfileDropdown } from "@oba/shared/components/app-header/profile-dropdown";
import { LanguageSwitcherSubMenu } from "@oba/shared/components/language-switcher";
import { ACCOUNT_LINKS } from "@oba/shared/constants/apps";
import { GrantedApp } from "@oba/shared/features/users/schema";
import { Link, usePathname } from "@oba/shared/libs/i18n/navigation";
import { SkyAppShell, type SkyLinkProps, type SkyNavSection } from "@oba/ui/sky/app-shell";
import { OrganizationIdProvider } from "@/hooks/use-organization-id";
import { SUPPORTED_LOCALES } from "@/lib/i18n/supported-locales";
import { AccountSessionSync } from "./account-session-sync";
import { OrganizationGate } from "./organization-gate";
import { OtakLogo } from "./otak-logo";

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
 * Page frame of every signed-in Otak page: the shared sky shell with Otak's
 * sections in the floating menu, and the organization selector (offering
 * the organizations that grant Otak), the app switcher and the account menu
 * in the top bar. The account session side effects run on every page, and a
 * user without an organization sees the gate instead of the page.
 * `organizationId` is the default organization of the session the server
 * rendered with; see `useOrganizationId`.
 */
export function OtakAppShell({
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
  // Profile and settings are pages of the account app; Otak owns only the
  // battlefield and relation screens, so the user menu sends them there. Read
  // on render, because the account origin arrives with the run-time config.
  const accountHrefs = { profile: ACCOUNT_LINKS.profile, settings: ACCOUNT_LINKS.settings };

  const sections = useMemo(
    (): SkyNavSection[] => [
      {
        label: t("navigation"),
        items: [
          { label: t("battlefield"), href: "/battlefield", icon: Kanban },
          { label: t("relations"), href: "/relations", icon: Building2 },
          { label: t("outreaches"), href: "/outreaches", icon: Send },
          { label: t("mail"), href: "/mail", icon: Mail },
          { label: t("icps"), href: "/icps", icon: Crosshair },
          { label: t("documents"), href: "/documents", icon: FileText },
          { label: t("templates"), href: "/templates", icon: LayoutTemplate },
          { label: t("events"), href: "/events", icon: CalendarDays },
        ],
      },
    ],
    [t]
  );

  return (
    <OrganizationIdProvider organizationId={organizationId}>
      <SkyAppShell
        logo={<OtakLogo className="h-6 w-auto sm:h-7" />}
        homeLabel={tApps("otak.name")}
        actions={
          <>
            <OrganizationSelector app={GrantedApp.OTAK} />
            <AppSwitcher currentAppId="otak" />
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
        sections={sections}
        pathname={pathname}
        linkComponent={LocaleLink}
      >
        <AccountSessionSync />
        <OrganizationGate>{children}</OrganizationGate>
      </SkyAppShell>
    </OrganizationIdProvider>
  );
}
