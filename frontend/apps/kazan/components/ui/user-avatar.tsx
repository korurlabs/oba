import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip";
import { cn } from "@/lib/utils";

// ── Types ────────────────────────────────────────────────────────────

interface UserAvatarUser {
  firstName?: string;
  lastName?: string;
  name?: string;
  email?: string;
  avatar?: string | null;
  image?: string | null;
}

interface UserAvatarProps {
  user: UserAvatarUser;
  size?: "xs" | "sm" | "md" | "lg";
  showName?: boolean;
  /** Shown instead of the name derived from `user`, e.g. "Unknown member". */
  name?: string;
  /** Merged over the name's default color, weight and size. */
  nameClassName?: string;
  className?: string;
}

interface UserMultipleAvatarProps {
  users: UserAvatarUser[];
  size?: "xs" | "sm" | "md" | "lg";
  /** Merged over the name's classes when a single user is shown with a name. */
  nameClassName?: string;
  className?: string;
}

interface UserAvatarStackProps {
  users: UserAvatarUser[];
  /** How many avatars to show at most; the rest is counted in "+N". */
  max?: number;
  /**
   * The size of the whole group when `users` is only a preview of it, as a
   * thread summary lists only its recent members; defaults to `users.length`.
   */
  total?: number;
  size?: "xs" | "sm" | "md" | "lg";
  className?: string;
}

// ── Constants ────────────────────────────────────────────────────────

const sizeClasses = {
  xs: "h-5 w-5",
  sm: "h-6 w-6",
  md: "h-8 w-8",
  lg: "h-10 w-10",
} as const;

const textClasses = {
  xs: "text-[8px]",
  sm: "text-[10px]",
  md: "text-xs",
  lg: "text-sm",
} as const;

const nameTextClasses = {
  xs: "text-xs",
  sm: "text-sm",
  md: "text-md",
  lg: "text-base",
} as const;

// ── Helpers ──────────────────────────────────────────────────────────

function getInitials(user: UserAvatarUser): string {
  if (user.firstName && user.lastName) {
    return `${user.firstName[0]}${user.lastName[0]}`.toUpperCase();
  }
  if (user.name) {
    return user.name
      .split(" ")
      .map((n) => n[0])
      .join("")
      .toUpperCase()
      .slice(0, 2);
  }
  if (user.email?.[0]) {
    return user.email[0].toUpperCase();
  }
  return "?";
}

function getDisplayName(user: UserAvatarUser): string {
  if (user.firstName && user.lastName) {
    return `${user.firstName} ${user.lastName}`;
  }
  if (user.name) return user.name;
  if (user.email) return user.email;
  return "";
}

// ── UserAvatar ───────────────────────────────────────────────────────

/**
 * A user's avatar. With `showName` it is the one unit for an avatar beside a
 * name, in a row or inside a translated sentence: the root is an inline `span`,
 * so it may sit in a `<button>` or in running text, and the avatar never ends
 * up on the other side of a translated word from its name. The initials are
 * hidden from screen readers there, because the name says the same. Without
 * `showName` the avatar stands alone and shows the name in a tooltip.
 */
export function UserAvatar({
  user,
  size = "sm",
  showName = true,
  name,
  nameClassName,
  className,
}: UserAvatarProps) {
  const imageUrl = user.avatar ?? user.image ?? null;

  const avatar = (
    <Avatar className={cn(sizeClasses[size], "rounded-full")} aria-hidden={showName || undefined}>
      {imageUrl && <AvatarImage src={imageUrl} />}
      <AvatarFallback
        className={cn(
          "bg-korur-blue-light text-korur-blue-strong rounded-full font-semibold",
          textClasses[size]
        )}
      >
        {getInitials(user)}
      </AvatarFallback>
    </Avatar>
  );

  if (showName) {
    return (
      <span className={cn("inline-flex max-w-full min-w-0 items-center gap-1.5 align-middle", className)}>
        {avatar}
        <span className={cn("text-foreground truncate font-medium", nameTextClasses[size], nameClassName)}>
          {name ?? getDisplayName(user)}
        </span>
      </span>
    );
  }

  return (
    <TooltipProvider>
      <Tooltip>
        <TooltipTrigger asChild>
          <span className={cn("inline-flex shrink-0", className)}>{avatar}</span>
        </TooltipTrigger>
        <TooltipContent>
          <p>{getDisplayName(user)}</p>
        </TooltipContent>
      </Tooltip>
    </TooltipProvider>
  );
}

// ── UserMultipleAvatar ───────────────────────────────────────────────

export function UserMultipleAvatar({ users, size = "sm", nameClassName, className }: UserMultipleAvatarProps) {
  if (users.length === 0) return null;

  if (users.length === 1) {
    return (
      <UserAvatar
        user={users?.[0] ?? {}}
        size={size}
        showName
        nameClassName={nameClassName}
        className={className}
      />
    );
  }

  return (
    <div className={cn("flex items-center -space-x-1.5", className)}>
      {users.map((user, index) => (
        <UserAvatar
          key={index}
          user={user}
          size={size}
          showName={false}
          className="ring-background relative z-0 cursor-default rounded-full ring-2 transition-[box-shadow,ring-color] duration-150 hover:z-10 hover:ring-korur-blue"
        />
      ))}
    </div>
  );
}

// ── UserAvatarStack ──────────────────────────────────────────────────

/**
 * Overlapping avatars of a group, at most `max` of them, followed by "+N"
 * for the members not shown. Unlike `UserMultipleAvatar` it never shows a
 * name, so it fits a compact summary line.
 */
export function UserAvatarStack({ users, max = 3, total, size = "xs", className }: UserAvatarStackProps) {
  const shown = users.slice(0, max);
  const hidden = Math.max((total ?? users.length) - shown.length, 0);
  if (shown.length === 0 && hidden === 0) return null;

  return (
    <div className={cn("flex items-center -space-x-1.5", className)}>
      {shown.map((user, index) => (
        <UserAvatar
          key={index}
          user={user}
          size={size}
          showName={false}
          className="ring-background relative z-0 rounded-full ring-2 hover:z-10"
        />
      ))}
      {hidden > 0 && (
        <span
          className={cn(
            "bg-muted text-muted-foreground ring-background relative inline-flex shrink-0 items-center justify-center rounded-full px-1 font-semibold ring-2",
            sizeClasses[size],
            "w-auto min-w-5",
            textClasses[size],
          )}
        >
          +{hidden}
        </span>
      )}
    </div>
  );
}

export { getInitials, getDisplayName };
export type { UserAvatarUser, UserAvatarProps, UserMultipleAvatarProps, UserAvatarStackProps };
