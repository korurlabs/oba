"use client";

import { Download } from "lucide-react";
import { useTranslations } from "next-intl";
import { SidebarMenu, SidebarMenuItem } from "@oba/ui/sky/sidebar";
import { ProfileDropdown } from "@oba/shared/components/app-header/profile-dropdown";
import { LanguageSwitcherSubMenu } from "@oba/shared/components/language-switcher";
import { ACCOUNT_LINKS } from "@oba/shared/constants/apps";
import { SUPPORTED_LOCALES } from "@/lib/i18n/supported-locales";
import { AppearanceMenuItem } from "../appearance-menu-item";
import { useSidebarContext } from "../sidebar-context";
import { SidebarItem } from "../sidebar-item";

/**
 * The foot of the classic sidebar: the CLI download page and the account
 * menu, whose rows match the top bar's account menu of the modern layout
 * (Profile, Appearance, Language, Log out) in the plain sidebar look.
 */
export function ClassicSidebarFooter() {
  const t = useTranslations("sidebar");
  const { closeMobile } = useSidebarContext();

  return (
    <SidebarMenu>
      <SidebarItem href="/settings/cli" label={t("downloads")} icon={Download} />
      <SidebarMenuItem>
        <ProfileDropdown
          variant="sidebar"
          showDarkModeSoon={false}
          onNavigate={closeMobile}
          profileHref={ACCOUNT_LINKS.profile}
          extraMenuContent={
            <>
              <AppearanceMenuItem onNavigate={closeMobile} />
              <LanguageSwitcherSubMenu supportedLocales={SUPPORTED_LOCALES} label={t("language")} />
            </>
          }
        />
      </SidebarMenuItem>
    </SidebarMenu>
  );
}
