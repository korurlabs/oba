"use client";

import { useRef, useState, type ComponentType } from "react";
import { ChevronRight, Plus } from "lucide-react";
import { useLocale, useTranslations } from "next-intl";
import { OPS_GRID_ACCENT, OPS_GRID_HIGHLIGHT, OPS_GRID_SURFACE } from "@oba/ui/components/ops-grid";
import {
  isNavItemActive,
  matchesPath,
  type SkyLinkProps,
  type SkyNavItem,
} from "@oba/ui/sky/app-shell";
import {
  SidebarMenuAction,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarMenuSub,
  SidebarMenuSubButton,
  SidebarMenuSubItem,
} from "@oba/ui/sky/sidebar";
import { pageHref } from "@/lib/routes";
import type { SectionSlug } from "@/lib/sections";
import { cn } from "@/lib/utils";
import { childrenOf, depthOf, pageTitle } from "../lib/page-tree";
import { PAGE_LIMITS, type PageListItem } from "../schemas";
import { NewChildPageField } from "./new-child-page-field";

/**
 * The "+" in the look of the organization selector's grid panel, at row size.
 * It shows while its own row is hovered or holds the focus, and always on a
 * touch screen below `md`. The row group is the row's own line, not the list
 * item, because a list item also holds the rows below it: hovering a child
 * must not light up every "+" above it.
 */
const ADD_ACTION_CLASS = cn(
  OPS_GRID_SURFACE,
  OPS_GRID_HIGHLIGHT,
  OPS_GRID_ACCENT,
  "focus-visible:ring-2 [&>svg]:size-3.5",
  "group-focus-within/tree-row:opacity-100 group-hover/tree-row:opacity-100 md:opacity-0"
);

/**
 * The row's page, or, on a section row whose section has no root page yet,
 * no page and the section the "+" sets up before it adds the first page.
 */
type PageTreeNodeRow =
  | { page: PageListItem; setUpSection?: never }
  | { page: null; setUpSection: SectionSlug; section: SkyNavItem };

export type PageTreeNodeProps = PageTreeNodeRow & {
  /** The organization's whole tree, flat, as `usePageTree` gives it. */
  pages: readonly PageListItem[];
  /**
   * Whether the reader may add pages in this section: a member with EDIT on
   * its root, or an organization OWNER or ADMIN where the root does not exist
   * yet, since only they may create it.
   */
  canEdit: boolean;
  /** The pages whose children show. */
  openIds: ReadonlySet<string>;
  onToggle: (pageId: string, open: boolean) => void;
  /** The current path with the locale stripped, so it compares with the hrefs. */
  pathname: string;
  link: ComponentType<SkyLinkProps>;
  /**
   * Set on a section's root page: the row is then the section's menu link,
   * with its icon and route, in place of the page's own link.
   */
  section?: SkyNavItem;
};

/**
 * One page of the menu's tree with the pages under it, to any depth. A
 * chevron folds the children open and shut, and a "+" opens a name field
 * under the row that adds a child page. The "+" shows only to a reader who
 * may edit the section, and not on a page at the deepest level the server
 * allows.
 */
export function PageTreeNode({
  page,
  setUpSection,
  pages,
  canEdit,
  openIds,
  onToggle,
  pathname,
  link: Link,
  section,
}: PageTreeNodeProps) {
  const t = useTranslations("sidebar");
  const locale = useLocale();
  const [adding, setAdding] = useState(false);
  const addRef = useRef<HTMLButtonElement>(null);

  const children = page ? childrenOf(pages, page.id) : [];
  const hasChildren = page !== null && children.length > 0;
  const expanded = hasChildren && openIds.has(page.id);
  const canAdd = canEdit && (page === null || depthOf(page) < PAGE_LIMITS.maxDepth);
  const label = section?.label ?? (page ? pageTitle(page, locale) : "");
  const href = section?.href ?? (page ? pageHref(page.id) : "");
  const isActive = section ? isNavItemActive(pathname, section) : matchesPath(pathname, href);

  // The new page sits under this row, so the row opens to show it once the
  // refetched tree holds it. The id comes from the field, because a section
  // set up by the field had no root page when the field opened.
  const closeField = (parentId: string) => {
    setAdding(false);
    onToggle(parentId, true);
  };
  const cancelField = () => {
    // Escape hands the focus back to the "+" that opened the field.
    addRef.current?.focus();
    setAdding(false);
  };

  // The actions sit at the row's right end: the "+" outermost, the chevron
  // beside it. Each takes a 20px square with a 4px gap.
  const actionCount = (hasChildren ? 1 : 0) + (canAdd ? 1 : 0);
  const chevronRight = canAdd ? "right-7" : "right-1";
  const actionTop = section ? "peer-data-[size=default]/menu-button:top-2" : "top-1";

  const actions = (
    <>
      {page && hasChildren && (
        <SidebarMenuAction
          className={cn(actionTop, chevronRight)}
          aria-expanded={expanded}
          aria-label={t(expanded ? "collapse" : "expand", { name: label })}
          onClick={() => onToggle(page.id, !expanded)}
        >
          <ChevronRight className={cn("transition-transform", expanded && "rotate-90")} />
        </SidebarMenuAction>
      )}
      {canAdd && (
        <SidebarMenuAction
          ref={addRef}
          className={cn(ADD_ACTION_CLASS, actionTop, adding && "md:opacity-100")}
          aria-label={t("addChild", { name: label })}
          aria-expanded={adding}
          onClick={() => setAdding(true)}
        >
          <Plus />
        </SidebarMenuAction>
      )}
    </>
  );

  const childSlugs = children.map((child) => child.slug);
  const field =
    adding &&
    (page ? (
      <NewChildPageField
        parentId={page.id}
        siblingSlugs={childSlugs}
        onCancel={cancelField}
        onCreated={closeField}
      />
    ) : (
      <NewChildPageField
        parentId={null}
        setUpSection={setUpSection}
        siblingSlugs={childSlugs}
        onCancel={cancelField}
        onCreated={closeField}
      />
    ));

  const subtree = expanded && (
    <SidebarMenuSub className={cn(!section && "mr-0 ml-3 pr-0")}>
      {children.map((child) => (
        <PageTreeNode
          key={child.id}
          page={child}
          pages={pages}
          canEdit={canEdit}
          openIds={openIds}
          onToggle={onToggle}
          pathname={pathname}
          link={Link}
        />
      ))}
    </SidebarMenuSub>
  );

  if (section) {
    return (
      <SidebarMenuItem>
        <div className="group/tree-row relative">
          <SidebarMenuButton
            asChild
            isActive={isActive}
            tooltip={label}
            className={cn(
              "h-9 gap-3 px-3",
              actionCount === 1 && "group-has-data-[sidebar=menu-action]/menu-item:pr-8",
              actionCount === 2 && "group-has-data-[sidebar=menu-action]/menu-item:pr-14"
            )}
          >
            <Link href={href}>
              <section.icon className="shrink-0" />
              <span>{label}</span>
            </Link>
          </SidebarMenuButton>
          {actions}
        </div>
        {field}
        {subtree}
      </SidebarMenuItem>
    );
  }

  return (
    <SidebarMenuSubItem>
      <div className="group/tree-row relative">
        <SidebarMenuSubButton
          asChild
          isActive={isActive}
          className={cn(actionCount === 1 && "pr-7", actionCount === 2 && "pr-13")}
        >
          <Link href={href}>
            <span>{label}</span>
          </Link>
        </SidebarMenuSubButton>
        {actions}
      </div>
      {field}
      {subtree}
    </SidebarMenuSubItem>
  );
}
