"use client";

import { Fragment } from "react";
import { useTranslations } from "next-intl";
import {
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarMenu,
} from "@oba/ui/sky/sidebar";
import { useKazanNavGroups, type KazanNavRow } from "./kazan-nav-items";

/**
 * Kazan's navigation inside the sky shell's floating panel, in two labelled
 * sections like the other sky apps: Operations holds the security operations
 * pages, Work the tickets and the team's conversations. The daily updates and
 * pull requests channels sit in Work, next to the Chats and Boards rows; each
 * channel row shows only once its channel exists. The Cyberscore row is left
 * out for a guest of the organization, whom the API refuses the cyberscore.
 * Settings sits apart at the panel's foot, drawn by the shell. When the rows
 * do not fit, the panel scrolls.
 *
 * The rows close the menu sheet of a narrow window on a click, and the Chats
 * and Boards rows hide their channel list, board tree and "+" while Kazan is
 * shut in the selected organization.
 */
export function KazanMenu() {
  const t = useTranslations("sidebar");
  const { operations, work } = useKazanNavGroups();

  return (
    <>
      <SidebarGroup className="py-0">
        <SidebarGroupLabel>{t("operations")}</SidebarGroupLabel>
        <SidebarGroupContent>
          <SidebarMenu className="gap-0.5">
            <NavRows rows={operations} />
          </SidebarMenu>
        </SidebarGroupContent>
      </SidebarGroup>
      <SidebarGroup className="py-0">
        <SidebarGroupLabel>{t("work")}</SidebarGroupLabel>
        <SidebarGroupContent>
          <SidebarMenu className="gap-0.5">
            <NavRows rows={work} />
          </SidebarMenu>
        </SidebarGroupContent>
      </SidebarGroup>
    </>
  );
}

export function NavRows({ rows }: { rows: KazanNavRow[] }) {
  return rows.map((row) => <Fragment key={row.id}>{row.node}</Fragment>);
}
