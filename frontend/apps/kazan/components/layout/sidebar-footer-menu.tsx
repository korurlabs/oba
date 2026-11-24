"use client";

import { Building2, Download } from "lucide-react";
import { useTranslations } from "next-intl";
import { DropdownMenuItem } from "@oba/ui/components/dropdown-menu";
import { SidebarMenu, SidebarMenuItem } from "@oba/ui/components/sidebar";
import { ACCOUNT_LINKS, ULAK_LINKS } from "@oba/shared/constants/apps";
import { ProfileDropdown } from "@oba/shared/components/app-header/profile-dropdown";
import { LanguageSwitcherSubMenu } from "@oba/shared/components/language-switcher";
import { canManageOrganizations } from "@oba/shared/features/organizations/lib/managed-organizations";
import { useGetMe } from "@oba/shared/features/users/hooks/use-get-me";
import { SUPPORTED_LOCALES } from "@/lib/i18n/supported-locales";
import { useSidebarContext } from "./sidebar-context";
import { SidebarItem } from "./sidebar-item";

/**
 * Profile leads to the account app, which holds it for every Oba app.
 * Settings opens the Kazan-only settings, whose navigation row links to the
 * general settings in the account app.
 */
const PROFILE_SETTINGS_HREF = "/settings/columns";

export function SidebarFooterMenu() {
  const t = useTranslations("sidebar");
  const { closeMobile } = useSidebarContext();
  const { data: meResponse } = useGetMe();
  const managesOrganizations = canManageOrganizations(meResponse?.data);

  return (
    <SidebarMenu>
      <SidebarItem href="/downloads" label={t("downloads")} icon={Download} />
      <SidebarMenuItem>
        <ProfileDropdown
          variant="sidebar"
          onNavigate={closeMobile}
          hrefs={{ profile: ACCOUNT_LINKS.profile, settings: PROFILE_SETTINGS_HREF }}
          extraMenuContent={
            <>
              {/* Organizations are managed in Ulak, so this item leaves Kazan. Ulak
                  refuses a user who manages none, so only a manager sees the item. */}
              {managesOrganizations && (
                <DropdownMenuItem asChild className="cursor-pointer rounded-lg px-3 py-2.5">
                  <a href={ULAK_LINKS.organizations}>
                    <Building2 className="text-muted-foreground h-4 w-4" />
                    <span>{t("organizations")}</span>
                  </a>
                </DropdownMenuItem>
              )}
              <LanguageSwitcherSubMenu supportedLocales={SUPPORTED_LOCALES} label={t("language")} />
            </>
          }
        />
      </SidebarMenuItem>
    </SidebarMenu>
  );
}
