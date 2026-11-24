"use client";

import { useState, type ComponentType } from "react";
import {
  Activity,
  BookOpen,
  Boxes,
  ClipboardCheck,
  FileCheck,
  HandCoins,
  KeyRound,
  Layers,
  LayoutDashboard,
  Network,
  Scale,
  ScrollText,
  Server,
  ShieldCheck,
  ShieldHalf,
  TriangleAlert,
  Truck,
} from "lucide-react";
import { useTranslations } from "next-intl";
import { SkyNavEntry, type SkyLinkProps, type SkyNavItem } from "@oba/ui/sky/app-shell";
import {
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarMenu,
} from "@oba/ui/sky/sidebar";
import { PageTreeNode } from "@/features/pages/components/page-tree-node";
import { usePageTree } from "@/features/pages/hooks/use-pages";
import { ancestorIds, findSectionRoot, pageIdFromPathname } from "@/features/pages/lib/page-tree";
import { isLevelAtLeast, KalkanLevel } from "@/features/pages/schemas";
import { useIsOrganizationMember } from "@/hooks/use-is-organization-admin";
import { sectionHref } from "@/lib/routes";
import { SECTION_GROUPS, type SectionSlug } from "@/lib/sections";

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

export type KalkanMenuProps = {
  /** The current path with the locale stripped, so it compares with the hrefs. */
  pathname: string;
  link: ComponentType<SkyLinkProps>;
};

/**
 * Kalkan's menu inside the sky shell's floating panel: Overview, the
 * dashboard, as a plain link, then every section in its group as a page
 * tree. All trees read the one cached page tree query. They start folded:
 * on each navigation only the rows above the page in view open by
 * themselves, and a row the user opened stays open until closed. A section whose root page is not set up, or not visible to the
 * reader, is a plain link to the section. Labels are singular and routes
 * plural.
 */
export function KalkanMenu({ pathname, link }: KalkanMenuProps) {
  const t = useTranslations("sidebar");
  const isMember = useIsOrganizationMember();
  const pages = usePageTree().data?.data;
  // The rows the user opened, which stay open across navigation, and the rows
  // open now: those plus the ancestors of the page in view.
  const [userOpenIds, setUserOpenIds] = useState<ReadonlySet<string>>(() => new Set());
  const [openIds, setOpenIds] = useState<ReadonlySet<string>>(() => new Set());
  const [openedFor, setOpenedFor] = useState<string | null>(null);

  // On each new path the open rows become the user's rows plus the ancestors
  // of the page in view, so a branch opened only to reveal an earlier page
  // folds again and the page in view keeps its own children folded. A path
  // counts as handled only once the tree holds its page: a page just created
  // from the tree is routed to before the refetched tree arrives, and its
  // branch must still open then.
  const pageInView = pageIdFromPathname(pathname);
  if (
    pages &&
    openedFor !== pathname &&
    (pageInView === null || pages.some((page) => page.id === pageInView))
  ) {
    setOpenedFor(pathname);
    setOpenIds(new Set([...userOpenIds, ...ancestorIds(pages, pageInView)]));
  }

  const toggle = (pageId: string, open: boolean) => {
    const update = (current: ReadonlySet<string>) => {
      const next = new Set(current);
      if (open) next.add(pageId);
      else next.delete(pageId);
      return next;
    };
    setUserOpenIds(update);
    setOpenIds(update);
  };

  const overview: SkyNavItem = { label: t("overview"), href: "/", icon: LayoutDashboard };

  return (
    <>
      <SidebarGroup className="py-0">
        <SidebarGroupContent>
          <SidebarMenu className="gap-0.5">
            <SkyNavEntry item={overview} pathname={pathname} link={link} />
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
                const root = pages ? findSectionRoot(pages, slug) : null;
                if (!pages || !root) {
                  return <SkyNavEntry key={slug} item={item} pathname={pathname} link={link} />;
                }
                return (
                  <PageTreeNode
                    key={slug}
                    page={root}
                    pages={pages}
                    section={item}
                    canEdit={isMember && isLevelAtLeast(root.level, KalkanLevel.EDIT)}
                    openIds={openIds}
                    onToggle={toggle}
                    pathname={pathname}
                    link={link}
                  />
                );
              })}
            </SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>
      ))}
    </>
  );
}
