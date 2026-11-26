"use client";

import { forwardRef, type ReactNode } from "react";
import { Settings } from "lucide-react";
import { useTranslations } from "next-intl";
import { AppSwitcher } from "@oba/shared/components/app-header/app-switcher";
import { OrganizationSelector } from "@oba/shared/components/app-header/organization-selector";
import { ProfileDropdown } from "@oba/shared/components/app-header/profile-dropdown";
import { LanguageSwitcherSubMenu } from "@oba/shared/components/language-switcher";
import { ACCOUNT_LINKS } from "@oba/shared/constants/apps";
import { GrantedApp } from "@oba/shared/features/users/schema";
import { Link, usePathname } from "@oba/shared/libs/i18n/navigation";
import { SkyAppShell, type SkyLinkProps, type SkyNavItem } from "@oba/ui/sky/app-shell";
import { IncidentAlertWatcher } from "@/features/incidents/components/incident-alert-watcher";
import { SUPPORTED_LOCALES } from "@/lib/i18n/supported-locales";
import { useKazanAppearance } from "@/features/settings/hooks/use-kazan-appearance";
import { AccountSessionSync } from "./account-session-sync";
import { AppAccessGate } from "./app-access-gate";
import { AppearanceMenuItem } from "./appearance-menu-item";
import { ChatUnreadTitle } from "./chat-unread-title";
import { ClassicAppShell } from "./classic/classic-app-shell";
import { KazanAppearanceRoot } from "./kazan-appearance-root";
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
 * The Settings link of the menu's foot opens the Kazan-only settings, whose
 * tab row links on to the account app's general settings. The link stands
 * for every `/settings` page.
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
 * rail) and the width is remembered in this browser. On a wide window the
 * menu is always shown; the top bar's toggle opens it as a sheet only on a
 * narrow one, as in the other apps. Boards lay out and scroll their own
 * panes, so the page fills the space beside the menu and scrolls in its own
 * area here.
 *
 * The user picks the layout in the appearance settings: this modern frame,
 * or the classic one (`ClassicAppShell`), a full-height inset sidebar with
 * no top bar. Only the frame differs; the page body, its data and its
 * providers are the same in both.
 */
export function KazanAppShell({ children }: { children: ReactNode }) {
  const { layout } = useKazanAppearance();
  const body = <KazanShellBody>{children}</KazanShellBody>;

  return (
    <SidebarWidthProvider>
      {layout === "classic" ? (
        <ClassicAppShell>{body}</ClassicAppShell>
      ) : (
        <KazanFrame>{body}</KazanFrame>
      )}
    </SidebarWidthProvider>
  );
}

/**
 * The page body of both frames. The side effects render nothing in place and
 * sit outside the gate, so the session sync, the appearance on <html>, the
 * alert for open P1 and P2 incidents and the unread count in the tab title
 * reach every page, also behind the no-access panel and while the menu is a
 * closed sheet.
 */
function KazanShellBody({ children }: { children: ReactNode }) {
  return (
    <>
      <AccountSessionSync />
      <KazanAppearanceRoot />
      <IncidentAlertWatcher />
      <ChatUnreadTitle />
      {/* The page area is Kazan's canvas, painted with the chosen background
          on the scroll container so it stays put while a page scrolls.
          Toolbars, cards and tables lie on it as solid plates. */}
      <div className="kazan-canvas min-h-0 flex-1 overflow-auto">
        {/* One gate holds the page and the ticket dialog, so an organization
            without Kazan shows one panel and no dialog over it. The dialog
            renders through a portal, so the scroll area does not clip it. */}
        <AppAccessGate>{children}</AppAccessGate>
      </div>
    </>
  );
}

/** The modern frame inside the width provider, since the shell's provider reads the width. */
function KazanFrame({ children }: { children: ReactNode }) {
  const t = useTranslations("sidebar");
  const tApps = useTranslations("apps");
  const { providerStyle, providerClassName } = useSidebarWidthContext();
  // The i18n pathname has the locale stripped, so it compares with the hrefs.
  const pathname = usePathname();
  // Profile is a page of the account app, read on render because the account
  // origin arrives with the run-time config.
  const profileHref = ACCOUNT_LINKS.profile;
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
      fill
      edge={<SidebarResizeHandle />}
      providerStyle={providerStyle}
      providerClassName={providerClassName}
      actions={
        <>
          <OrganizationSelector app={GrantedApp.KAZAN} />
          <AppSwitcher currentAppId="kazan" />
          <ProfileDropdown
            profileHref={profileHref}
            showDarkModeSoon={false}
            extraMenuContent={
              <>
                <AppearanceMenuItem />
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
      {children}
    </SkyAppShell>
  );
}
