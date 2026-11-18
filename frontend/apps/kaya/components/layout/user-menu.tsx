"use client";

import { Building2, LogOut, UserCog } from "lucide-react";

import { ACCOUNT_LINKS, ADMIN_LINKS } from "@oba/shared/constants/apps";
import { Button } from "@oba/ui/sky/button";
import { submitLogout } from "@/lib/auth/logout-client";
import { useIdentity } from "@/lib/identity/identity-context";

import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@oba/ui/sky/dropdown-menu";

function initials(name: string): string {
  const letters = name
    .split(/[\s@.]+/)
    .filter((part: string) => part !== "")
    .slice(0, 2)
    .map((part: string) => part[0]?.toUpperCase() ?? "");
  return letters.join("") || "?";
}

/**
 * The signed-in user, the way to their account, to the organization and its
 * members (for an organization manager only), and signing out.
 */
export function UserMenu() {
  const { me } = useIdentity();
  const name = me.displayName ?? me.email;

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button
          variant="ghost"
          size="icon"
          className="rounded-full"
          aria-label={`Account of ${name}`}
        >
          <span className="bg-secondary text-secondary-foreground flex size-8 items-center justify-center rounded-full text-xs font-semibold">
            {initials(name)}
          </span>
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="min-w-56">
        <DropdownMenuLabel className="flex flex-col gap-0.5">
          <span className="truncate font-semibold">{name}</span>
          {me.displayName && (
            <span className="text-muted-foreground truncate text-xs font-normal">{me.email}</span>
          )}
        </DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuItem asChild>
          <a href={ACCOUNT_LINKS.profile}>
            <UserCog className="size-4" aria-hidden />
            Account
          </a>
        </DropdownMenuItem>
        {/* Organization settings live in Admin, which refuses a user who manages no organization. */}
        {me.managesOrganizations && (
          <DropdownMenuItem asChild>
            <a href={ADMIN_LINKS.organizations}>
              <Building2 className="size-4" aria-hidden />
              Organization
            </a>
          </DropdownMenuItem>
        )}
        {/* Ends the browser's Keycloak session too, so the next sign-in asks for the password. */}
        <DropdownMenuItem onSelect={submitLogout}>
          <LogOut className="size-4" aria-hidden />
          Sign out
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
