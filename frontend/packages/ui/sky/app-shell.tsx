"use client";

import NextLink from "next/link";
import { usePathname } from "next/navigation";
import type {
  ComponentPropsWithRef,
  ComponentType,
  CSSProperties,
  MouseEvent,
  ReactNode,
} from "react";

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
  useSidebar,
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

/**
 * Runs the app's handler for a click on a menu link, then closes the menu
 * sheet of a narrow window unless the handler kept the page: the sheet covers
 * the page it opens, so it must not stay over it.
 */
export function handleNavClick(
  event: MouseEvent<HTMLAnchorElement>,
  onNavClick: SkyNavClickHandler | undefined,
  closeMobile: () => void
): void {
  onNavClick?.(event);
  if (!event.defaultPrevented) closeMobile();
}

export type SkyNavEntryProps = {
  item: SkyNavItem;
  pathname: string;
  link: ComponentType<SkyLinkProps>;
  onNavClick?: SkyNavClickHandler;
};

/**
 * One menu link. Its look (the neon rail on the page in view, the hover glow)
 * lives in `styles.css` with the rest of the menu panel. An app that draws
 * its own menu content uses it for its plain links, so they match the
 * sections' links. A click closes the menu sheet on a narrow window.
 */
export function SkyNavEntry({ item, pathname, link: Link, onNavClick }: SkyNavEntryProps) {
  const { setOpenMobile } = useSidebar();
  return (
    <SidebarMenuItem>
      <SidebarMenuButton
        asChild
        isActive={isNavItemActive(pathname, item)}
        tooltip={item.label}
        className="h-9 gap-3 px-3"
      >
        <Link
          href={item.href}
          onClick={(event: MouseEvent<HTMLAnchorElement>) =>
            handleNavClick(event, onNavClick, () => setOpenMobile(false))
          }
        >
          <item.icon className="shrink-0" />
          <span>{item.label}</span>
        </Link>
      </SidebarMenuButton>
    </SidebarMenuItem>
  );
}

export type SkyMenuSectionsProps = {
  sections: readonly SkyNavSection[];
  pathname: string;
  link: ComponentType<SkyLinkProps>;
  onNavClick?: SkyNavClickHandler;
};

/** The labelled groups of links, each a menu of `SkyNavEntry` links. */
export function SkyMenuSections({ sections, pathname, link, onNavClick }: SkyMenuSectionsProps) {
  return sections.map((section: SkyNavSection) => (
    <SidebarGroup key={section.label} className="py-0">
      <SidebarGroupLabel>{section.label}</SidebarGroupLabel>
      <SidebarGroupContent>
        <SidebarMenu className="gap-0.5">
          {section.items.map((item: SkyNavItem) => (
            <SkyNavEntry
              key={item.href}
              item={item}
              pathname={pathname}
              link={link}
              onNavClick={onNavClick}
            />
          ))}
        </SidebarMenu>
      </SidebarGroupContent>
    </SidebarGroup>
  ));
}

export type SkySidebarProps = {
  /** The app's pages in labelled groups; an app that draws its whole menu passes none. */
  sections?: readonly SkyNavSection[];
  /** Menu content the app draws itself, such as a page tree, placed after the sections. */
  children?: ReactNode;
  /** Links set apart below the menu, above a lattice divider, such as Settings. */
  footerItems?: readonly SkyNavItem[];
  /** A faint mark at the foot of the panel, such as the Korur horse archer; shown with the footer links. */
  footerMark?: ReactNode;
  /** The current path; defaults to the router's. */
  pathname?: string;
  linkComponent?: ComponentType<SkyLinkProps>;
  onNavClick?: SkyNavClickHandler;
  /**
   * Drawn at the panel's right edge in place of the rail that toggles the
   * menu, such as a handle that resizes it.
   */
  edge?: ReactNode;
};

/**
 * The left menu: the app's pages in sections and any menu content of its
 * own, then the footer links, on a sky-blue HUD panel that floats beside the
 * page, centred between the top bar and the window's foot, and only as tall
 * as its links. On a window too short for it, the links scroll inside the
 * panel and the footer stays in view. It collapses to icons. The panel's
 * surface and placement are styled in `styles.css`.
 */
export function SkySidebar({
  sections = [],
  children,
  footerItems = [],
  footerMark,
  pathname,
  linkComponent = NextLink,
  onNavClick,
  edge,
}: SkySidebarProps) {
  const routerPathname = usePathname();
  const current = pathname ?? routerPathname;
  return (
    <Sidebar
      variant="floating"
      collapsible="icon"
      // The container spans from the top bar to the window's foot; the panel
      // is centred in that span (see `styles.css`).
      style={{ top: "3.5rem", bottom: 0, height: "auto" }}
    >
      <SidebarContent className="gap-4 pt-4 pb-2">
        <SkyMenuSections
          sections={sections}
          pathname={current}
          link={linkComponent}
          onNavClick={onNavClick}
        />
        {children}
      </SidebarContent>
      {footerItems.length > 0 && (
        <>
          <SidebarSeparator className="sky-lattice mx-3" />
          <SidebarFooter className="relative pt-2 pb-3">
            {footerMark}
            <SidebarMenu className="gap-0.5">
              {footerItems.map((item: SkyNavItem) => (
                <SkyNavEntry
                  key={item.href}
                  item={item}
                  pathname={current}
                  link={linkComponent}
                  onNavClick={onNavClick}
                />
              ))}
            </SidebarMenu>
          </SidebarFooter>
        </>
      )}
      {edge ?? <SidebarRail />}
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
 * The bar across the top: the wordmark and the app's actions. The menu toggle
 * shows only on narrow windows, where the menu is a sheet that has no other
 * way to open; on wider windows the menu floats beside the page, and the 12px
 * side padding puts the wordmark level with the panel's left edge.
 *
 * The toggle hides at 768px, the `MOBILE_BREAKPOINT` of `./use-mobile` where the
 * menu stops being a sheet, and not at `md:`: apps may set their own `md`
 * (Kazan's is 744px), which would leave a band of widths where the sheet has
 * no toggle. Change both together.
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
      <SidebarTrigger className="min-[768px]:hidden" />
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
  Omit<SkySidebarProps, "linkComponent" | "children"> & {
    /** The page. */
    children: ReactNode;
    /** Menu content the app draws itself, placed after `sections`. */
    menu?: ReactNode;
    /** Rendered after the frame, inside the sidebar's provider, such as a fixed overlay. */
    overlay?: ReactNode;
    /** The link used by the top bar and the menu; defaults to `next/link`. */
    linkComponent?: ComponentType<SkyLinkProps>;
    /**
     * The page fills the space beside the menu, as a flex column that does not
     * scroll, for a page that lays out and scrolls its own panes, such as a
     * board. Without it the page scrolls in a centred column of at most 80rem.
     */
    fill?: boolean;
    /** Style of the sidebar's provider, such as a `--sidebar-width` the app keeps. */
    providerStyle?: CSSProperties;
    /** Classes of the sidebar's provider. */
    providerClassName?: string;
  };

/**
 * Page frame: the top bar across the window, the floating menu on the left
 * below it, and the page scrolling on its own beside the menu, in a centred
 * column of at most 80rem unless `fill` is set. It is a client component, so an app passes its
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
  menu,
  footerItems,
  footerMark,
  pathname,
  linkComponent,
  onNavClick,
  edge,
  fill = false,
  providerStyle,
  providerClassName,
}: SkyAppShellProps) {
  return (
    <SidebarProvider style={providerStyle} className={providerClassName}>
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
            footerItems={footerItems}
            footerMark={footerMark}
            pathname={pathname}
            linkComponent={linkComponent}
            onNavClick={onNavClick}
            edge={edge}
          >
            {menu}
          </SkySidebar>
          {fill ? (
            <main className="flex min-w-0 flex-1 flex-col overflow-hidden">{children}</main>
          ) : (
            <main className="min-w-0 flex-1 overflow-auto">
              <div className="mx-auto w-full max-w-7xl px-4 py-6 sm:px-8 lg:py-10">{children}</div>
            </main>
          )}
        </div>
      </div>
      {overlay}
    </SidebarProvider>
  );
}
