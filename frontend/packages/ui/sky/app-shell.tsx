"use client";

import NextLink from "next/link";
import { usePathname } from "next/navigation";
import type { ComponentPropsWithRef, ComponentType, MouseEvent, ReactNode } from "react";

import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarProvider,
  SidebarRail,
  SidebarSeparator,
  SidebarTrigger,
} from "./sidebar";

/** One link of the menu. */
export type SkyNavItem = {
  label: string;
  href: string;
  icon: ComponentType<{ className?: string }>;
  /**
   * Whether the page at `pathname` belongs to this link. Without it the link
   * is active on its own href and every path below it.
   */
  isActive?: (pathname: string) => boolean;
};

/** A labelled group of links. */
export type SkyNavSection = {
  label: string;
  items: readonly SkyNavItem[];
};

/**
 * The props a link component receives. `next/link` fits; an app with locale
 * routes passes its locale-aware link instead. The menu renders the link
 * through a Radix slot, so the component must forward its ref and props to
 * the anchor.
 */
export type SkyLinkProps = Omit<ComponentPropsWithRef<"a">, "href"> & { href: string };

/** Handles a click on a menu link; calling `preventDefault` keeps the page. */
export type SkyNavClickHandler = (event: MouseEvent<HTMLAnchorElement>) => void;

/**
 * Whether `pathname` is `prefix` or lies below it. The root matches only
 * itself, or every page would light up the link to the start page.
 */
export function matchesPath(pathname: string, prefix: string): boolean {
  if (prefix === "/") return pathname === "/";
  return pathname === prefix || pathname.startsWith(`${prefix}/`);
}

/** Whether the menu marks `item` as the page in view. */
export function isNavItemActive(pathname: string, item: SkyNavItem): boolean {
  return item.isActive ? item.isActive(pathname) : matchesPath(pathname, item.href);
}

type NavEntryProps = {
  item: SkyNavItem;
  pathname: string;
  link: ComponentType<SkyLinkProps>;
  onNavClick?: SkyNavClickHandler;
};

/**
 * One menu link. Its look (the neon rail on the page in view, the hover glow)
 * lives in `styles.css` with the rest of the menu panel.
 */
function NavEntry({ item, pathname, link: Link, onNavClick }: NavEntryProps) {
  return (
    <SidebarMenuItem>
      <SidebarMenuButton
        asChild
        isActive={isNavItemActive(pathname, item)}
        tooltip={item.label}
        className="h-9 gap-3 px-3"
      >
        <Link href={item.href} onClick={onNavClick}>
          <item.icon className="shrink-0" />
          <span>{item.label}</span>
        </Link>
      </SidebarMenuButton>
    </SidebarMenuItem>
  );
}

export type SkySidebarProps = {
  sections: readonly SkyNavSection[];
  /** A link set apart below the sections, above a lattice divider, such as Settings. */
  footerItem?: SkyNavItem;
  /** A faint mark at the foot of the panel, such as the Korur horse archer; shown with the footer item. */
  footerMark?: ReactNode;
  /** The current path; defaults to the router's. */
  pathname?: string;
  linkComponent?: ComponentType<SkyLinkProps>;
  onNavClick?: SkyNavClickHandler;
};

/**
 * The left menu: the app's pages in sections, then the footer item, on a
 * sky-blue HUD panel that floats beside the page, level with its filter row,
 * and is only as tall as its links. It collapses to icons. The panel's
 * surface is styled in `styles.css`.
 */
export function SkySidebar({
  sections,
  footerItem,
  footerMark,
  pathname,
  linkComponent = NextLink,
  onNavClick,
}: SkySidebarProps) {
  const routerPathname = usePathname();
  const current = pathname ?? routerPathname;
  return (
    <Sidebar
      variant="floating"
      collapsible="icon"
      // The container spans from the top bar to the window's foot; the panel
      // hangs from the filter row's height in that span (see `styles.css`).
      style={{ top: "3.5rem", bottom: 0, height: "auto" }}
    >
      <SidebarContent className="gap-4 pt-4 pb-2">
        {sections.map((section: SkyNavSection) => (
          <SidebarGroup key={section.label} className="py-0">
            <SidebarGroupLabel>{section.label}</SidebarGroupLabel>
            <SidebarGroupContent>
              <SidebarMenu className="gap-0.5">
                {section.items.map((item: SkyNavItem) => (
                  <NavEntry
                    key={item.href}
                    item={item}
                    pathname={current}
                    link={linkComponent}
                    onNavClick={onNavClick}
                  />
                ))}
              </SidebarMenu>
            </SidebarGroupContent>
          </SidebarGroup>
        ))}
      </SidebarContent>
      {footerItem && (
        <>
          <SidebarSeparator className="sky-lattice mx-3" />
          <SidebarFooter className="relative pt-2 pb-3">
            {footerMark}
            <SidebarMenu>
              <NavEntry
                item={footerItem}
                pathname={current}
                link={linkComponent}
                onNavClick={onNavClick}
              />
            </SidebarMenu>
          </SidebarFooter>
        </>
      )}
      <SidebarRail />
    </Sidebar>
  );
}

export type SkyTopBarProps = {
  /** The app's wordmark, linked to its start page. */
  logo: ReactNode;
  /** Names the start-page link for assistive technology, such as "Kaya home". */
  homeLabel: string;
  homeHref?: string;
  /** What sits at the right end of the bar, such as the account menu. */
  actions?: ReactNode;
  linkComponent?: ComponentType<SkyLinkProps>;
};

/**
 * The bar across the top: the wordmark and the app's actions. The menu
 * toggle shows only on narrow windows, where the menu is a sheet that has no
 * other way to open; on wider windows the menu floats beside the page, and
 * the 12px side padding puts the wordmark level with the panel's left edge.
 */
export function SkyTopBar({
  logo,
  homeLabel,
  homeHref = "/",
  actions,
  linkComponent: Link = NextLink,
}: SkyTopBarProps) {
  return (
    <header className="bg-background/80 sticky top-0 z-40 flex h-14 shrink-0 items-center gap-2 border-b px-3 backdrop-blur">
      <SidebarTrigger className="md:hidden" />
      <Link href={homeHref} aria-label={homeLabel} className="flex shrink-0 items-center">
        {logo}
      </Link>
      {actions !== undefined && (
        <div className="ml-auto flex min-w-0 items-center gap-2">{actions}</div>
      )}
    </header>
  );
}

export type SkyAppShellProps = Omit<SkyTopBarProps, "linkComponent"> &
  Omit<SkySidebarProps, "linkComponent"> & {
    children: ReactNode;
    /** Rendered after the frame, inside the sidebar's provider, such as a fixed overlay. */
    overlay?: ReactNode;
    /** The link used by the top bar and the menu; defaults to `next/link`. */
    linkComponent?: ComponentType<SkyLinkProps>;
  };

/**
 * Page frame: the top bar across the window, the floating menu on the left
 * below it, and the page scrolling on its own beside the menu, in a centred
 * column of at most 80rem. It is a client component, so an app passes its
 * menu from a client module: the icons are components and cannot cross from
 * the server.
 */
export function SkyAppShell({
  children,
  overlay,
  logo,
  homeLabel,
  homeHref,
  actions,
  sections,
  footerItem,
  footerMark,
  pathname,
  linkComponent,
  onNavClick,
}: SkyAppShellProps) {
  return (
    <SidebarProvider>
      <div className="flex h-svh w-full flex-col overflow-hidden">
        <SkyTopBar
          logo={logo}
          homeLabel={homeLabel}
          homeHref={homeHref}
          actions={actions}
          linkComponent={linkComponent}
        />
        <div className="flex min-h-0 flex-1">
          <SkySidebar
            sections={sections}
            footerItem={footerItem}
            footerMark={footerMark}
            pathname={pathname}
            linkComponent={linkComponent}
            onNavClick={onNavClick}
          />
          <main className="min-w-0 flex-1 overflow-auto">
            <div className="mx-auto w-full max-w-7xl px-4 py-6 sm:px-8 lg:py-10">{children}</div>
          </main>
        </div>
      </div>
      {overlay}
    </SidebarProvider>
  );
}
