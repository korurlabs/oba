"use client";

import { Settings } from "lucide-react";
import { useTranslations } from "next-intl";
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuItem,
} from "@oba/ui/sky/sidebar";
import { AppSwitcher } from "@oba/shared/components/app-header/app-switcher";
import { OrganizationSelector } from "@oba/shared/components/app-header/organization-selector";
import { GrantedApp } from "@oba/shared/features/users/schema";
import { NavRows } from "../kazan-menu";
import { useKazanNavGroups } from "../kazan-nav-items";
import { useSidebarContext } from "../sidebar-context";
import { SidebarItem } from "../sidebar-item";
import { SidebarResizeHandle } from "../sidebar-resize-handle";
import { ClassicSidebarFooter } from "./classic-sidebar-footer";

/**
 * The classic layout's sidebar: no top bar, so the sidebar runs the full
 * height of the window and holds the chrome. The organization switcher and
 * the app switcher sit at the top, the navigation in one list in the middle,
 * the CLI page and the account menu at the foot.
 *
 * `variant="inset"` floats the content as a rounded, bordered panel on the
 * sidebar's grey ground (see `ClassicAppShell`). The rows are the modern
 * menu's rows, so both layouts offer the same pages.
 */
export function ClassicSidebar() {
  const t = useTranslations("sidebar");
  const { closeMobile } = useSidebarContext();
  const { operations, work } = useKazanNavGroups();
  const rows = [...operations, ...work];
  // One list, as the classic sidebar had: the everyday pages first, then the
  // operations pages, then the team's channels, chats and boards.
  const ordered = [
    ...rows.filter((row) => row.id === "home" || row.id === "tickets"),
    ...operations.filter((row) => row.id !== "home"),
    ...work.filter((row) => row.id !== "tickets"),
  ];

  return (
    <Sidebar variant="inset" collapsible="icon">
      <SidebarHeader>
        <SidebarMenu>
          <SidebarMenuItem className="flex items-center gap-1">
            <div className="min-w-0 flex-1">
              <OrganizationSelector
                variant="sidebar"
                secondaryLabel={t("workspace")}
                showChevron={false}
                onNavigate={closeMobile}
                app={GrantedApp.KAZAN}
              />
            </div>
            <AppSwitcher
              currentAppId="kazan"
              className="shrink-0 group-data-[collapsible=icon]:hidden"
            />
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarHeader>

      <SidebarContent>
        <SidebarGroup>
          <SidebarGroupLabel>{t("navigation")}</SidebarGroupLabel>
          <SidebarGroupContent>
            <SidebarMenu>
              <NavRows rows={ordered} />
              <SidebarItem
                href="/settings/columns"
                label={t("settings")}
                icon={Settings}
                activePrefixes={["/settings"]}
              />
            </SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>
      </SidebarContent>

      <SidebarFooter>
        <ClassicSidebarFooter />
      </SidebarFooter>

      <SidebarResizeHandle />
    </Sidebar>
  );
}
