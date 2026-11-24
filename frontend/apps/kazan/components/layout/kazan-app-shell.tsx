"use client";

import { forwardRef, type ReactNode } from "react";
import { Building2, Settings } from "lucide-react";
import { useTranslations } from "next-intl";
import { AppSwitcher } from "@oba/shared/components/app-header/app-switcher";
import { OrganizationSelector } from "@oba/shared/components/app-header/organization-selector";
import {
  PROFILE_MENU_GRID_ITEM,
  ProfileDropdown,
} from "@oba/shared/components/app-header/profile-dropdown";
import { LanguageSwitcherSubMenu } from "@oba/shared/components/language-switcher";
import { ACCOUNT_LINKS, ULAK_LINKS } from "@oba/shared/constants/apps";
import { canManageOrganizations } from "@oba/shared/features/organizations/lib/managed-organizations";
import { useGetMe } from "@oba/shared/features/users/hooks/use-get-me";
import { GrantedApp } from "@oba/shared/features/users/schema";
import { Link, usePathname } from "@oba/shared/libs/i18n/navigation";
import { DropdownMenuItem } from "@oba/ui/components/dropdown-menu";
import { OPS_GRID_ACCENT } from "@oba/ui/components/ops-grid";
import { cn } from "@oba/ui/lib/utils";
import { SkyAppShell, type SkyLinkProps, type SkyNavItem } from "@oba/ui/sky/app-shell";
import { IncidentAlertWatcher } from "@/features/incidents/components/incident-alert-watcher";
import { SUPPORTED_LOCALES } from "@/lib/i18n/supported-locales";
import { AccountSessionSync } from "./account-session-sync";
import { AppAccessGate } from "./app-access-gate";
import { ChatUnreadTitle } from "./chat-unread-title";
import { KazanLogo } from "./kazan-logo";
import { KazanMenu } from "./kazan-menu";
import { SidebarWidthProvider, useSidebarWidthContext } from "./sidebar-context";
import { SidebarResizeHandle } from "./sidebar-resize-handle";

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
 * The Settings link of the menu's foot and the account menu open the
 * Kazan-only settings, whose tab row links on to the account app's general
 * settings. The link stands for every `/settings` page.
 */
const SETTINGS_HREF = "/settings/columns";

function isSettingsPath(pathname: string): boolean {
  return pathname === "/settings" || pathname.startsWith("/settings/");
}

/**
 * Page frame of every signed-in Kazan page: the shared sky shell with
 * Kazan's navigation and board tree as its menu and Settings at the menu's
 * foot, and the organization selector (offering the organizations that grant
 * Kazan), the app switcher and the account menu in the top bar.
 *
 * Kazan's menu is wide enough for nested board names, so the viewer resizes
 * it by dragging its right edge (the handle takes the place of the shell's
 * rail) and the width is remembered in this browser; the top bar's toggle
 * collapses it to icons at every width. Boards lay out and scroll their own
 * panes, so the page fills the space beside the menu and scrolls in its own
 * area here.
 */
export function KazanAppShell({ children }: { children: ReactNode }) {
  return (
    <SidebarWidthProvider>
      <KazanFrame>{children}</KazanFrame>
    </SidebarWidthProvider>
  );
}

/** The frame inside the width provider, since the shell's provider reads the width. */
function KazanFrame({ children }: { children: ReactNode }) {
  const t = useTranslations("sidebar");
  const tApps = useTranslations("apps");
  const { providerStyle, providerClassName } = useSidebarWidthContext();
  const { data: meResponse } = useGetMe();
  const managesOrganizations = canManageOrganizations(meResponse?.data);
  // The i18n pathname has the locale stripped, so it compares with the hrefs.
  const pathname = usePathname();
  // Profile is a page of the account app, read on render because the account
  // origin arrives with the run-time config.
  const accountHrefs = { profile: ACCOUNT_LINKS.profile, settings: SETTINGS_HREF };
  const footerItems: SkyNavItem[] = [
    { label: t("settings"), href: SETTINGS_HREF, icon: Settings, isActive: isSettingsPath },
  ];

  return (
    <SkyAppShell
      logo={<KazanLogo className="h-6 w-auto sm:h-7" />}
      homeLabel={tApps("kazan.name")}
      homeHref="/home"
      linkComponent={LocaleLink}
      pathname={pathname}
      menuToggle="always"
      fill
      edge={<SidebarResizeHandle />}
      providerStyle={providerStyle}
      providerClassName={providerClassName}
      actions={
        <>
          <OrganizationSelector app={GrantedApp.KAZAN} />
          <AppSwitcher currentAppId="kazan" />
          <ProfileDropdown
            hrefs={accountHrefs}
            extraMenuContent={
              <>
                {/* Organizations are managed in Ulak, so this item leaves Kazan. Ulak
                    refuses a user who manages none, so only a manager sees the item. */}
                {managesOrganizations && (
                  <DropdownMenuItem asChild className={PROFILE_MENU_GRID_ITEM}>
                    <a href={ULAK_LINKS.organizations}>
                      <Building2 className={cn("size-4", OPS_GRID_ACCENT)} aria-hidden />
                      <span>{t("organizations")}</span>
                    </a>
                  </DropdownMenuItem>
                )}
                <LanguageSwitcherSubMenu
                  supportedLocales={SUPPORTED_LOCALES}
                  label={t("language")}
                />
              </>
            }
          />
        </>
      }
      menu={<KazanMenu />}
      footerItems={footerItems}
    >
      {/* These render nothing in place and sit outside the gate, so the
          session side effects, the alert for open P1 and P2 incidents and the
          unread count in the tab title reach every page, also behind the
          no-access panel and while the menu is a closed sheet. */}
      <AccountSessionSync />
      <IncidentAlertWatcher />
      <ChatUnreadTitle />
      <div className="min-h-0 flex-1 overflow-auto">
        {/* One gate holds the page and the ticket dialog, so an organization
            without Kazan shows one panel and no dialog over it. The dialog
            renders through a portal, so the scroll area does not clip it. */}
        <AppAccessGate>{children}</AppAccessGate>
      </div>
    </SkyAppShell>
  );
}
