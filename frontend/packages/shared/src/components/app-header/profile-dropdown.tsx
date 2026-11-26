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
import { Switch } from "@oba/ui/components/switch";
import { ChevronDown, ChevronsUpDown, LogOut, Moon, User } from "lucide-react";
import { useSession } from "next-auth/react";
import { useTranslations } from "next-intl";
import { useGetMe } from "@oba/shared/features/users/hooks/use-get-me";
import { useIsMobile } from "@oba/ui/hooks/use-mobile";
import { SidebarMenuButton } from "@oba/ui/sky/sidebar";
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
   * "header" renders the compact top bar button and the square grid panel.
   * "sidebar" renders a full-width sidebar menu button (avatar, name, email,
   * up/down chevron) whose plain rounded menu opens to the side. Only valid
   * inside the sky `SidebarProvider`.
   */
  variant?: "header" | "sidebar";
  /**
   * Called when the Profile link is chosen. The menu cannot close a
   * surrounding overlay itself, so a host that renders it inside one, such as
   * a mobile sidebar sheet, closes it here.
   */
  onNavigate?: () => void;
  /**
   * Whether the menu shows the disabled "Dark mode, Soon" row. An app that
   * offers a theme of its own (in `extraMenuContent` or its settings) turns
   * it off, so the menu does not promise what the app already has.
   */
  showDarkModeSoon?: boolean;
  /** Applied to the trigger. */
  className?: string;
  /**
   * Optional extra menu content rendered between Dark mode and Log out, such
   * as `LanguageSwitcherSubMenu`. Must be
   * dropdown-menu content (an item, a group or a submenu); it is rendered
   * directly inside `DropdownMenuContent`.
   */
  extraMenuContent?: React.ReactNode;
  /**
   * Where Profile leads. The default is the app's own page; an app without one
   * passes the absolute URL of the Account app's profile.
   */
  profileHref?: string;
}

const DEFAULT_PROFILE_HREF = "/profile";

/*
 * The menu is drawn in the square ops grid panel of the organization selector
 * beside it: hairlines part every row, a row is tinted while highlighted, and
 * icons take the accent colour.
 */
const GRID_PANEL = cn(
  OPS_GRID_SURFACE,
  OPS_GRID_SHADOW,
  "w-[min(18rem,calc(100vw-2rem))] min-w-[220px] px-2 py-1.5"
);
/** A row of the menu. */
const PROFILE_MENU_GRID_ITEM = cn(OPS_GRID_ROW, OPS_GRID_HIGHLIGHT, "cursor-pointer");
/**
 * The Dark mode row: drawn as a grid row, but not highlighted, not pointer-led
 * and not dimmed as a whole, since its own "Soon" and the disabled switch say
 * that it does not work yet.
 */
const DARK_MODE_ROW = cn(OPS_GRID_ROW, "cursor-default data-[disabled]:opacity-100");
/**
 * The off switch of the Dark mode row, its track drawn in the grid's hairline
 * blue so it stays visible on the pale panel, where the theme's input fill is
 * almost white.
 */
const DARK_MODE_SWITCH =
  "border-[rgb(47_143_233/0.35)] data-[state=unchecked]:bg-[rgb(47_143_233/0.14)] disabled:opacity-70";
/** A separator drawn as the row hairline: no height or fill of its own, and full width. */
const GRID_SEPARATOR = cn("mx-0 my-0 h-0 bg-transparent", OPS_GRID_HAIRLINE);
/** The sidebar variant keeps the theme's rounded menu. */
const PLAIN_PANEL = "border-border w-56 rounded-xl border p-2 shadow-xl";
const PLAIN_ITEM = "cursor-pointer rounded-lg px-3 py-2.5";
const PLAIN_ICON = "text-muted-foreground h-4 w-4";

/**
 * The user menu: name and email, Profile, Dark mode, optional extra content
 * (the language submenu), Log out.
 *
 * Dark mode is not available yet in most apps, so its row is disabled: the
 * menu skips it when moving by keyboard, it has no action, and its switch
 * stays off. An app with a working theme leaves the row out
 * (`showDarkModeSoon`).
 *
 * It is presentation only and owns no session side effects: the host calls
 * `useAccountSessionSync` (pending-invitation session sync, stored-theme sync)
 * from a place that is mounted on every page and at every viewport width.
 */
export function ProfileDropdown({
  variant = "header",
  onNavigate,
  showDarkModeSoon = true,
  className,
  extraMenuContent,
  profileHref = DEFAULT_PROFILE_HREF,
}: ProfileDropdownProps = {}) {
  const { data: session } = useSession();
  // Read from the media query rather than `useSidebar()`, so the header
  // variant keeps working in apps without a sidebar provider.
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
  const iconClassName = isSidebar ? PLAIN_ICON : cn("size-4", OPS_GRID_ACCENT);

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
        className={isSidebar ? PLAIN_PANEL : GRID_PANEL}
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
            <Link href={profileHref} onClick={onNavigate}>
              <User className={iconClassName} />
              <span>{t("profile")}</span>
            </Link>
          </DropdownMenuItem>
          {showDarkModeSoon && (
            <>
              <DropdownMenuSeparator className={separatorClassName} />
              <DropdownMenuItem
                disabled
                className={isSidebar ? "cursor-default rounded-lg px-3 py-2.5" : DARK_MODE_ROW}
              >
                <Moon className={iconClassName} />
                <span>{t("darkMode")}</span>
                <span className="text-muted-foreground ms-auto font-mono text-[10px] tracking-[0.14em] uppercase">
                  {t("soon")}
                </span>
                <Switch
                  checked={false}
                  disabled
                  tabIndex={-1}
                  aria-hidden="true"
                  className={DARK_MODE_SWITCH}
                />
              </DropdownMenuItem>
            </>
          )}
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
