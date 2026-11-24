"use client";

import { useState, type ComponentType } from "react";
import {
  Activity,
  BookOpen,
  Boxes,
  ChevronRight,
  ClipboardCheck,
  FileCheck,
  FilePlus,
  HandCoins,
  History,
  KeyRound,
  Layers,
  LayoutDashboard,
  Network,
  Scale,
  ScrollText,
  Server,
  ShieldCheck,
  ShieldHalf,
  Trash2,
  TriangleAlert,
  Truck,
} from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import { isNavItemActive, type SkyLinkProps, type SkyNavItem } from "@oba/ui/sky/app-shell";
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarMenu,
  SidebarMenuAction,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarMenuSub,
  SidebarMenuSubButton,
  SidebarMenuSubItem,
  SidebarRail,
  SidebarSeparator,
} from "@oba/ui/sky/sidebar";
import { useSectionRoot } from "@/features/pages/hooks/use-section-root";
import { pageTitle } from "@/features/pages/lib/page-tree";
import { useIsOrganizationMember } from "@/hooks/use-is-organization-admin";
import { NEW_PAGE_HREF, RECENT_HREF, TRASH_HREF, pageHref, sectionHref } from "@/lib/routes";
import { SECTION_GROUPS, type SectionSlug } from "@/lib/sections";
import { cn } from "@/lib/utils";

type Icon = ComponentType<{ className?: string }>;

/** The icon of each section in the menu. */
const SECTION_ICONS: Record<SectionSlug, Icon> = {
  frameworks: Layers,
  controls: ShieldCheck,
  policies: ScrollText,
  risks: TriangleAlert,
  evidence: FileCheck,
  audits: ClipboardCheck,
  assets: Boxes,
  suppliers: Truck,
  access: KeyRound,
  architecture: Network,
  protection: ShieldHalf,
  monitoring: Activity,
  infrastructure: Server,
  guides: BookOpen,
  laws: Scale,
  incentives: HandCoins,
};

type NavEntryProps = {
  item: SkyNavItem;
  pathname: string;
  link: ComponentType<SkyLinkProps>;
};

/**
 * One menu link, drawn as the sky shell draws its links, so the neon rail on
 * the page in view and the hover glow of `styles.css` apply.
 */
function NavEntry({ item, pathname, link: Link }: NavEntryProps) {
  return (
    <SidebarMenuItem>
      <SidebarMenuButton
        asChild
        isActive={isNavItemActive(pathname, item)}
        tooltip={item.label}
        className="h-9 gap-3 px-3"
      >
        <Link href={item.href}>
          <item.icon className="shrink-0" />
          <span>{item.label}</span>
        </Link>
      </SidebarMenuButton>
    </SidebarMenuItem>
  );
}

/**
 * A knowledge section: the link to the section, and below it the pages under
 * the section's root, which a chevron folds open and shut. The list opens by
 * itself while one of its pages is in view.
 */
function KnowledgeEntry({
  item,
  slug,
  pathname,
  link: Link,
}: NavEntryProps & { slug: SectionSlug }) {
  const t = useTranslations("sidebar");
  const locale = useLocale();
  const { section } = useSectionRoot(slug);
  const children = section?.children ?? [];
  const childInView = children.some((child) => pathname === pageHref(child.id));
  const [openChoice, setOpenChoice] = useState<boolean | null>(null);
  const expanded = openChoice ?? childInView;
  const hasChildren = children.length > 0;

  return (
    <SidebarMenuItem>
      <SidebarMenuButton
        asChild
        isActive={isNavItemActive(pathname, item)}
        tooltip={item.label}
        className={cn("h-9 gap-3 px-3", hasChildren && "pr-8")}
      >
        <Link href={item.href}>
          <item.icon className="shrink-0" />
          <span>{item.label}</span>
        </Link>
      </SidebarMenuButton>
      {hasChildren && (
        <SidebarMenuAction
          className="top-2"
          aria-expanded={expanded}
          aria-label={t(expanded ? "collapse" : "expand", { name: item.label })}
          onClick={() => setOpenChoice(!expanded)}
        >
          <ChevronRight className={cn("transition-transform", expanded && "rotate-90")} />
        </SidebarMenuAction>
      )}
      {hasChildren && expanded && (
        <SidebarMenuSub>
          {children.map((child) => {
            const href = pageHref(child.id);
            return (
              <SidebarMenuSubItem key={child.id}>
                <SidebarMenuSubButton asChild isActive={pathname === href}>
                  <Link href={href}>
                    <span>{pageTitle(child, locale)}</span>
                  </Link>
                </SidebarMenuSubButton>
              </SidebarMenuSubItem>
            );
          })}
        </SidebarMenuSub>
      )}
    </SidebarMenuItem>
  );
}

export type KalkanSidebarProps = {
  /** The current path with the locale stripped, so it compares with the hrefs. */
  pathname: string;
  linkComponent: ComponentType<SkyLinkProps>;
};

/**
 * The left menu of every Kalkan page, on the sky shell's floating panel:
 * Overview, then the sections in their groups, with the knowledge sections
 * as page trees, and Recent and Trash at the foot. Labels are singular and
 * routes plural. A guest cannot write, so New page and Trash show only to a
 * member or above.
 */
export function KalkanSidebar({ pathname, linkComponent }: KalkanSidebarProps) {
  const t = useTranslations("sidebar");
  const isMember = useIsOrganizationMember();

  const overview: SkyNavItem = { label: t("overview"), href: "/", icon: LayoutDashboard };
  const footerItems: SkyNavItem[] = [
    { label: t("recent"), href: RECENT_HREF, icon: History },
    ...(isMember ? [{ label: t("trash"), href: TRASH_HREF, icon: Trash2 }] : []),
  ];

  return (
    <Sidebar
      variant="floating"
      collapsible="icon"
      // The container spans from the top bar to the window's foot, as in the
      // sky shell; the panel hangs from the filter row's height in that span.
      style={{ top: "3.5rem", bottom: 0, height: "auto" }}
    >
      <SidebarContent className="gap-4 pt-4 pb-2">
        <SidebarGroup className="py-0">
          <SidebarGroupContent>
            <SidebarMenu className="gap-0.5">
              <NavEntry item={overview} pathname={pathname} link={linkComponent} />
            </SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>
        {SECTION_GROUPS.map((group) => (
          <SidebarGroup key={group.key} className="py-0">
            <SidebarGroupLabel>{t(`groups.${group.key}`)}</SidebarGroupLabel>
            <SidebarGroupContent>
              <SidebarMenu className="gap-0.5">
                {group.slugs.map((slug: SectionSlug) => {
                  const item: SkyNavItem = {
                    label: t(`sections.${slug}`),
                    href: sectionHref(slug),
                    icon: SECTION_ICONS[slug],
                  };
                  return group.key === "knowledge" ? (
                    <KnowledgeEntry
                      key={slug}
                      slug={slug}
                      item={item}
                      pathname={pathname}
                      link={linkComponent}
                    />
                  ) : (
                    <NavEntry key={slug} item={item} pathname={pathname} link={linkComponent} />
                  );
                })}
                {group.key === "knowledge" && isMember && (
                  <NavEntry
                    item={{ label: t("newPage"), href: NEW_PAGE_HREF, icon: FilePlus }}
                    pathname={pathname}
                    link={linkComponent}
                  />
                )}
              </SidebarMenu>
            </SidebarGroupContent>
          </SidebarGroup>
        ))}
      </SidebarContent>
      <SidebarSeparator className="sky-lattice mx-3" />
      <SidebarFooter className="relative pt-2 pb-3">
        <SidebarMenu className="gap-0.5">
          {footerItems.map((item) => (
            <NavEntry key={item.href} item={item} pathname={pathname} link={linkComponent} />
          ))}
        </SidebarMenu>
      </SidebarFooter>
      <SidebarRail />
    </Sidebar>
  );
}
