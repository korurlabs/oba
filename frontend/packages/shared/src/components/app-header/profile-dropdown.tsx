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
import { ChevronDown, LogOut, Moon, User } from "lucide-react";
import { useSession } from "next-auth/react";
import { useTranslations } from "next-intl";
import { useGetMe } from "@oba/shared/features/users/hooks/use-get-me";
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

export interface ProfileDropdownProps {
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

/**
 * The user menu: name and email, Profile, Dark mode, optional extra content
 * (the language submenu), Log out.
 *
 * Dark mode is not available yet, so its row is disabled: the menu skips it
 * when moving by keyboard, it has no action, and its switch stays off.
 *
 * It is presentation only and owns no session side effects: the host calls
 * `useAccountSessionSync` (pending-invitation session sync, stored-theme sync)
 * from a place that is mounted on every page and at every viewport width.
 */
export function ProfileDropdown({
  className,
  extraMenuContent,
  profileHref = DEFAULT_PROFILE_HREF,
}: ProfileDropdownProps = {}) {
  const { data: session } = useSession();
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

  const iconClassName = cn("size-4", OPS_GRID_ACCENT);

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
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
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" sideOffset={8} className={GRID_PANEL}>
        <DropdownMenuLabel className="px-3 py-2.5">
          <div className="flex flex-col space-y-1">
            <p className="text-[15px] leading-snug font-semibold break-words">
              {firstName} {lastName}
            </p>
            {email && <p className="text-muted-foreground text-xs break-all">{email}</p>}
          </div>
        </DropdownMenuLabel>
        <DropdownMenuSeparator className={GRID_SEPARATOR} />
        <DropdownMenuItem asChild className={PROFILE_MENU_GRID_ITEM}>
          <Link href={profileHref}>
            <User className={iconClassName} />
            <span>{t("profile")}</span>
          </Link>
        </DropdownMenuItem>
        <DropdownMenuSeparator className={GRID_SEPARATOR} />
        <DropdownMenuItem disabled className={DARK_MODE_ROW}>
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
        {extraMenuContent && (
          <>
            <DropdownMenuSeparator className={GRID_SEPARATOR} />
            {extraMenuContent}
          </>
        )}
        <DropdownMenuSeparator className={GRID_SEPARATOR} />
        <DropdownMenuItem onClick={handleLogout} className={PROFILE_MENU_GRID_ITEM}>
          <LogOut className={iconClassName} />
          <span>{t("logOut")}</span>
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
