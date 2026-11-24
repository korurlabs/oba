"use client";

import { Link } from "@oba/shared/libs/i18n/navigation";
import { Avatar, AvatarFallback, AvatarImage } from "@oba/ui/components/avatar";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@oba/ui/components/dropdown-menu";
import { ChevronDown, ChevronsUpDown, LogOut, Settings, User } from "lucide-react";
import { useSession } from "next-auth/react";
import { useTranslations } from "next-intl";
import { useGetMe } from "@oba/shared/features/users/hooks/use-get-me";
import { SidebarMenuButton } from "@oba/ui/components/sidebar";
import { useIsMobile } from "@oba/ui/hooks/use-mobile";
import {
  OPS_GRID_ACCENT,
  OPS_GRID_HAIRLINE,
  OPS_GRID_HIGHLIGHT,
  OPS_GRID_ROW,
  OPS_GRID_SHADOW,
  OPS_GRID_SURFACE,
} from "@oba/ui/components/ops-grid";
import { cn } from "@oba/ui/lib/utils";
import { submitLogout } from "@oba/shared/libs/auth/logout-client";
import { MenuLookContext } from "./menu-look";

export interface ProfileDropdownProps {
  /**
   * "default" renders the compact header button.
   * "sidebar" renders a full-width sidebar menu button (avatar, name, email,
   * up/down chevron) whose menu opens to the side. Only valid inside a
   * SidebarProvider.
   */
  variant?: "default" | "sidebar";
  /** Applied to the trigger. */
  className?: string;
  /**
   * Optional extra menu content rendered between Settings and Log out --
   * Kazan passes `LanguageSwitcherSubMenu` here so locale switching lives in
   * the account menu instead of floating in the sidebar footer. Must be
   * dropdown-menu content (an item, a group or a submenu); it is rendered
   * directly inside `DropdownMenuContent`.
   */
  extraMenuContent?: React.ReactNode;
  /**
   * Called when the Profile or Settings link is chosen. The menu cannot close
   * a surrounding overlay itself (the default variant runs without a
   * SidebarProvider), so a host that renders it inside one, such as a mobile
   * sidebar sheet, closes it here.
   */
  onNavigate?: () => void;
  /**
   * Where Profile and Settings lead. The defaults are the app's own pages; an
   * app without them (Ulak) passes the absolute URLs of the app that has them.
   */
  hrefs?: { profile: string; settings: string };
}

const DEFAULT_HREFS = { profile: "/profile", settings: "/settings" };

/*
 * The header ("default") variant draws the menu in the square ops grid panel
 * of the organization selector beside it: hairlines part every row, a row is
 * tinted while highlighted, and icons take the accent colour. The "sidebar"
 * variant keeps the theme's rounded menu.
 */
const GRID_PANEL = cn(
  OPS_GRID_SURFACE,
  OPS_GRID_SHADOW,
  "w-[min(18rem,calc(100vw-2rem))] min-w-[220px] px-2 py-1.5"
);
/**
 * A row of the header variant. Exported for a row a host app passes in
 * `extraMenuContent`, so it matches Profile and Settings.
 */
export const PROFILE_MENU_GRID_ITEM = cn(OPS_GRID_ROW, OPS_GRID_HIGHLIGHT, "cursor-pointer");
const PLAIN_ITEM = "cursor-pointer rounded-lg px-3 py-2.5";
/** A separator drawn as the row hairline: no height or fill of its own, and full width. */
const GRID_SEPARATOR = cn("mx-0 my-0 h-0 bg-transparent", OPS_GRID_HAIRLINE);

/**
 * The user menu: name and email, Profile, Settings, optional extra content,
 * Log out.
 *
 * It is presentation only. Inside a sidebar the menu is unmounted while the
 * off-canvas sheet is closed, so it cannot own session side effects: the host
 * calls `useAccountSessionSync` (pending-invitation session sync, stored-theme
 * sync) from a place that is mounted on every page and at every viewport
 * width.
 */
export function ProfileDropdown({
  variant = "default",
  className,
  extraMenuContent,
  onNavigate,
  hrefs = DEFAULT_HREFS,
}: ProfileDropdownProps = {}) {
  const { data: session } = useSession();
  // Read from the media query rather than `useSidebar()` so the default
  // (header) variant keeps working in apps that have no SidebarProvider.
  const isMobile = useIsMobile();
  const { data: user } = useGetMe();
  const t = useTranslations("common");

  // Derive display values from useGetMe data, falling back to session data
  const firstName = user?.data?.profile?.firstName ?? session?.user?.name?.split(" ")[0];
  const lastName =
    user?.data?.profile?.lastName ?? session?.user?.name?.split(" ").slice(1).join(" ");
  const email = user?.data?.email ?? session?.user?.email;

  const userInitials =
    user?.data?.profile?.firstName && user?.data?.profile?.lastName
      ? `${user.data.profile.firstName.charAt(0)}${user.data.profile.lastName.charAt(0)}`
      : email
          ?.split("@")[0]
          ?.split("")
          .map((n) => n[0])
          .join("")
          .toUpperCase() || "U";

  const handleLogout = () => {
    // Ends the app and the Keycloak session; the user lands on the app's
    // public home. Re-auth goes through /api/auth/login on demand.
    submitLogout();
  };

  // Don't render if no session at all
  if (!session?.user) {
    return null;
  }

  const isSidebar = variant === "sidebar";
  const itemClassName = isSidebar ? PLAIN_ITEM : PROFILE_MENU_GRID_ITEM;
  const separatorClassName = isSidebar ? "my-1" : GRID_SEPARATOR;
  const iconClassName = isSidebar ? "text-muted-foreground h-4 w-4" : cn("size-4", OPS_GRID_ACCENT);

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        {isSidebar ? (
          <SidebarMenuButton
            size="lg"
            className={cn(
              "data-[state=open]:bg-sidebar-accent data-[state=open]:text-sidebar-accent-foreground",
              className
            )}
          >
            <Avatar className="size-8 rounded-lg">
              <AvatarImage src={undefined} alt={userInitials} />
              <AvatarFallback className="bg-korur-blue-light text-korur-blue-strong rounded-lg text-sm font-semibold">
                {userInitials}
              </AvatarFallback>
            </Avatar>
            <div className="grid min-w-0 flex-1 text-left leading-tight">
              <span className="truncate text-sm font-medium">
                {firstName} {lastName}
              </span>
              {email && <span className="text-muted-foreground truncate text-xs">{email}</span>}
            </div>
            <ChevronsUpDown className="ml-auto size-4" />
          </SidebarMenuButton>
        ) : (
          <button
            className={cn(
              "hover:bg-muted data-[state=open]:bg-muted flex items-center gap-1 rounded-lg px-1 py-0.5 transition-colors duration-150 md:gap-3 md:rounded-xl md:px-3 md:py-1.5",
              className
            )}
          >
            <Avatar className="h-7 w-7 rounded-md md:rounded-lg">
              <AvatarImage src={undefined} alt={userInitials} />
              <AvatarFallback className="bg-korur-blue-light text-korur-blue-strong rounded-md text-sm font-semibold md:rounded-lg">
                {userInitials}
              </AvatarFallback>
            </Avatar>
            <span className="text-foreground hidden text-sm font-medium md:block">{firstName}</span>
            <ChevronDown className="hidden h-4 w-4 md:block" />
          </button>
        )}
      </DropdownMenuTrigger>
      <DropdownMenuContent
        align="end"
        side={isSidebar && !isMobile ? "right" : "bottom"}
        sideOffset={isSidebar ? 4 : 8}
        className={isSidebar ? "border-border w-56 rounded-xl border p-2 shadow-xl" : GRID_PANEL}
      >
        <MenuLookContext.Provider value={isSidebar ? "plain" : "grid"}>
          <DropdownMenuLabel className={isSidebar ? "px-3 py-3" : "px-3 py-2.5"}>
            <div className="flex flex-col space-y-1">
              <p
                className={cn(
                  "font-semibold",
                  isSidebar ? "text-foreground text-sm" : "text-[15px] leading-snug break-words"
                )}
              >
                {firstName} {lastName}
              </p>
              {email && (
                <p className={cn("text-muted-foreground text-xs", !isSidebar && "break-all")}>
                  {email}
                </p>
              )}
            </div>
          </DropdownMenuLabel>
          <DropdownMenuSeparator className={separatorClassName} />
          <DropdownMenuItem asChild className={itemClassName}>
            <Link href={hrefs.profile} onClick={onNavigate}>
              <User className={iconClassName} />
              <span>{t("profile")}</span>
            </Link>
          </DropdownMenuItem>
          {!isSidebar && <DropdownMenuSeparator className={separatorClassName} />}
          <DropdownMenuItem asChild className={itemClassName}>
            <Link href={hrefs.settings} onClick={onNavigate}>
              <Settings className={iconClassName} />
              <span>{t("settings")}</span>
            </Link>
          </DropdownMenuItem>
          {extraMenuContent && (
            <>
              <DropdownMenuSeparator className={separatorClassName} />
              {extraMenuContent}
            </>
          )}
          <DropdownMenuSeparator className={separatorClassName} />
          <DropdownMenuItem
            onClick={handleLogout}
            className={cn(itemClassName, isSidebar && "text-foreground")}
          >
            <LogOut className={iconClassName} />
            <span>{t("logOut")}</span>
          </DropdownMenuItem>
        </MenuLookContext.Provider>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
