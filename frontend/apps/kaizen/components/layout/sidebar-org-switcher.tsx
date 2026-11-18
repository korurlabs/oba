"use client";

import { useTranslations } from "next-intl";
import { SidebarMenu, SidebarMenuItem } from "@oba/ui/components/sidebar";
import { OrganizationSelector } from "@oba/shared/components/app-header/organization-selector";
import { GrantedApp } from "@oba/shared/features/users/schema";
import { useSidebarContext } from "./sidebar-context";

/**
 * Top block of the sidebar: the organization switcher on its own.
 *
 * The collapse toggle used to sit next to it; it now lives at the left edge of
 * the content area (see `content-sidebar-trigger.tsx`), which is where the
 * Kagi portal puts it and which keeps it reachable while the sidebar is
 * collapsed. The chevron is suppressed here so the row reads as a workspace
 * title block rather than a form control.
 *
 * The menu offers only the organizations that grant Kaizen, so a switch never
 * lands the user in an organization where every Kaizen call is refused.
 */
export function SidebarOrgSwitcher() {
  const t = useTranslations("sidebar");
  const { closeMobile } = useSidebarContext();

  return (
    <SidebarMenu>
      <SidebarMenuItem>
        <OrganizationSelector
          variant="sidebar"
          secondaryLabel={t("workspace")}
          showChevron={false}
          organizationLogoSrc="/assets/korur-mark.png"
          onNavigate={closeMobile}
          app={GrantedApp.KAIZEN}
        />
      </SidebarMenuItem>
    </SidebarMenu>
  );
}
