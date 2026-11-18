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
import { cn } from "@oba/ui/lib/utils";
import { submitLogout } from "@oba/shared/libs/auth/logout-client";

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
   * app without them (Admin) passes the absolute URLs of the app that has them.
   */
  hrefs?: { profile: string; settings: string };
}

const DEFAULT_HREFS = { profile: "/profile", settings: "/settings" };

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
              "hover:bg-muted flex items-center gap-1 rounded-lg px-1 py-0.5 transition-colors duration-150 md:gap-3 md:rounded-xl md:px-3 md:py-1.5",
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
        sideOffset={isSidebar ? 4 : undefined}
        className="border-border w-56 rounded-xl border p-2 shadow-xl"
      >
        <DropdownMenuLabel className="px-3 py-3">
          <div className="flex flex-col space-y-1">
            <p className="text-foreground text-sm font-semibold">
              {firstName} {lastName}
            </p>
            {email && <p className="text-muted-foreground text-xs">{email}</p>}
          </div>
        </DropdownMenuLabel>
        <DropdownMenuSeparator className="my-1" />
        <DropdownMenuItem asChild className="cursor-pointer rounded-lg px-3 py-2.5">
          <Link href={hrefs.profile} onClick={onNavigate}>
            <User className="text-muted-foreground h-4 w-4" />
            <span>{t("profile")}</span>
          </Link>
        </DropdownMenuItem>
        <DropdownMenuItem asChild className="cursor-pointer rounded-lg px-3 py-2.5">
          <Link href={hrefs.settings} onClick={onNavigate}>
            <Settings className="text-muted-foreground h-4 w-4" />
            <span>{t("settings")}</span>
          </Link>
        </DropdownMenuItem>
        {extraMenuContent && (
          <>
            <DropdownMenuSeparator className="my-1" />
            {extraMenuContent}
          </>
        )}
        <DropdownMenuSeparator className="my-1" />
        <DropdownMenuItem
          onClick={handleLogout}
          className="text-foreground cursor-pointer rounded-lg px-3 py-2.5"
        >
          <LogOut className="text-muted-foreground h-4 w-4" />
          <span>{t("logOut")}</span>
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
