"use client";

import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectLabel,
  SelectSeparator,
  SelectTrigger,
  SelectValue,
} from "@oba/ui/components/select";
import { useQueryClient } from "@tanstack/react-query";
import { useSession } from "next-auth/react";
import Image from "next/image";
import { usePathname, useRouter } from "@oba/shared/libs/i18n/navigation";
import { useTranslations } from "next-intl";
import { Building2, ChevronsUpDown, Loader2, Plus, User as UserIcon } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import { useGetMe } from "@oba/shared/features/users/hooks/use-get-me";
import type { GrantedApp } from "@oba/shared/features/users/schema";
import { useUpdateMe } from "@oba/shared/features/users/hooks/use-update-me";
import { useClearDefaultOrganization } from "@oba/shared/features/users/hooks/use-clear-default-organization";
import { cn } from "@oba/ui/lib/utils";
import { markAllOrganizationQueriesStale } from "../../hooks/use-invalidate-org-queries";
import { ACCOUNT_LINKS } from "../../constants/apps";
import { organizationsWithApp } from "../../features/organizations/lib/app-access";

// Sentinel for the "Personal" entry. The Radix Select won't accept an
// empty string as a value, so the personal mode has an explicit token here,
// which stands for "no default organization" on the user.
const PERSONAL_VALUE = "__personal__";

export interface OrganizationSelectorProps {
  /**
   * "default" renders the bordered header select.
   * "sidebar" renders a sidebar menu-button shaped trigger (org icon, name,
   * secondary line, and -- unless `showChevron` is false -- an up/down
   * chevron) that collapses to icon-only inside a shadcn sidebar with
   * collapsible="icon". The menu itself is identical.
   */
  variant?: "default" | "sidebar";
  /**
   * Secondary line rendered under the org name in the "sidebar" variant.
   * Supplied by the host app so the copy stays in that app's messages.
   */
  secondaryLabel?: string;
  /**
   * Whether the "sidebar" variant draws the up/down chevron on the right.
   * Defaults to true so every existing caller keeps the chevron it has
   * today; Kazan opts out because its sidebar header reads as a title
   * block rather than a form control. Ignored by the "default" variant,
   * which uses the Select's own chevron.
   */
  showChevron?: boolean;
  /**
   * Public path of a logo the "sidebar" variant shows for an organization in
   * place of the generic building icon. It sits on a white tile so a colored
   * line mark stays legible on both the light and the dark sidebar. Personal
   * mode and the switching spinner keep their own icons.
   */
  organizationLogoSrc?: string;
  /**
   * Where the "Create organization" row navigates: the account app's create
   * screen by default, because any user may create an organization. An in-app path is also accepted and navigates client-side.
   */
  createOrganizationHref?: string;
  /**
   * Called when the "Create organization" row navigates. The selector cannot
   * close a surrounding overlay itself (the header variant runs without a
   * SidebarProvider), so a host that renders it inside one, such as a mobile
   * sidebar sheet, closes it here.
   */
  onNavigate?: () => void;
  /**
   * The app the host needs a grant for. When set, the menu offers only the
   * ACTIVE organizations that grant it, so a switch never lands the user in an
   * organization where the app is closed. The trigger still names a selected
   * organization without the grant, so the user sees where they are. Without
   * it, the menu offers every organization of the user.
   */
  app?: GrantedApp;
  /** Applied to the trigger. */
  className?: string;
}

export function OrganizationSelector({
  variant = "default",
  secondaryLabel,
  showChevron = true,
  organizationLogoSrc,
  createOrganizationHref = ACCOUNT_LINKS.newOrganization,
  onNavigate,
  app,
  className,
}: OrganizationSelectorProps = {}) {
  const queryClient = useQueryClient();
  const router = useRouter();
  const pathname = usePathname();
  const { data: session, status, update } = useSession();
  const { data: meResponse, isFetching } = useGetMe();
  const { mutateAsync: updateMe } = useUpdateMe();
  const { mutateAsync: clearDefaultOrganization } = useClearDefaultOrganization();
  const [isSwitching, setIsSwitching] = useState(false);
  // Controlled, because the "Create organization" row is not a SelectItem:
  // nothing closes the dropdown for it, and with a soft navigation the open
  // dropdown would otherwise stay over the new page.
  const [open, setOpen] = useState(false);
  const t = useTranslations();

  const organizations = meResponse?.data?.organizations ?? [];
  const selectableOrganizations = app ? organizationsWithApp(meResponse?.data, app) : organizations;
  const isSyncing = isFetching || isSwitching;

  // Personal mode is active under a /personal route, or when the session
  // carries no default organization. The trigger label and icon follow the
  // same value the menu marks as selected, so the two cannot disagree in an
  // app that has no /personal route.
  const isPersonalRoute = pathname?.includes("/personal") ?? false;
  const selectedValue = isPersonalRoute
    ? PERSONAL_VALUE
    : session?.user?.defaultOrganizationId || PERSONAL_VALUE;
  // Until the session has arrived there is no default organization to read,
  // which would otherwise look like personal mode for a moment.
  const isSessionPending = status === "loading" && !session;
  const isPersonal = !isSessionPending && selectedValue === PERSONAL_VALUE;
  const showOrganizationLogo = Boolean(organizationLogoSrc) && !isSyncing && !isPersonal;

  const handleCreateOrganization = () => {
    setOpen(false);
    onNavigate?.();
    // An absolute URL is another app; the locale-aware router only handles
    // paths of this one.
    if (/^https?:\/\//.test(createOrganizationHref)) {
      window.location.assign(createOrganizationHref);
      return;
    }
    router.push(createOrganizationHref);
  };

  const handleValueChange = async (value: string) => {
    // The default organization is null for personal, the org UUID otherwise.
    const nextOrganizationId = value === PERSONAL_VALUE ? null : value;
    if (nextOrganizationId === (session?.user?.defaultOrganizationId || null)) {
      // Same value already persisted — nothing to do.
      return;
    }

    setIsSwitching(true);
    try {
      // Both mutations reject when the API refuses the change. Personal has
      // its own endpoint, because `PUT /users/me` keeps the current default
      // for an absent or empty value.
      if (nextOrganizationId === null) {
        await clearDefaultOrganization();
      } else {
        await updateMe({ defaultOrganizationId: nextOrganizationId });
      }
      await update({
        user: {
          ...session?.user,
          defaultOrganizationId: nextOrganizationId,
        },
      });
      // The user stays on the current page. The cached data belongs to the
      // previous organization, so it is marked stale here, but nothing
      // refetches yet: the old page is still mounted and would call the API
      // without an organization or in one without the app. The session change
      // makes OrganizationSyncProvider refetch the queries the new
      // organization enables, after the page has rendered for it.
      await markAllOrganizationQueriesStale(queryClient);
    } catch (error) {
      console.error("[OrganizationSelector] organization switch failed:", error);
      toast.error(t("organizationSwitchFailed"));
    } finally {
      setIsSwitching(false);
    }
  };

  // Display label for the trigger: "Personal" when in personal mode,
  // otherwise the resolved org name (or the placeholder when nothing
  // matches yet).
  const triggerLabel = isPersonal
    ? t("personalWorkspace")
    : (organizations.find((org) => org.id === session?.user?.defaultOrganizationId)?.name ??
      t("selectOrganization"));

  return (
    <Select
      value={selectedValue}
      onValueChange={handleValueChange}
      disabled={isSyncing}
      open={open}
      onOpenChange={setOpen}
    >
      {variant === "sidebar" ? (
        /*
         * Sidebar trigger. Shaped like the shadcn `SidebarMenuButton size="lg"`
         * rather than a bordered select, and rendered WITHOUT `SelectValue` so
         * the two-line label is a plain grid (the value slot is styled
         * `line-clamp-1` by the base trigger, which would collapse it).
         * `[&>svg]:hidden` drops the Select's own chevron -- the nested
         * ChevronsUpDown below takes its place.
         */
        <SelectTrigger
          aria-label={t("selectOrganization")}
          className={cn(
            "hover:bg-sidebar-accent hover:text-sidebar-accent-foreground data-[state=open]:bg-sidebar-accent data-[state=open]:text-sidebar-accent-foreground dark:hover:bg-sidebar-accent h-12 max-h-12 w-full max-w-none justify-start gap-2 rounded-md border-0 bg-transparent p-2 shadow-none focus-visible:ring-0 dark:bg-transparent",
            "min-h-12! group-data-[collapsible=icon]:size-8! group-data-[collapsible=icon]:min-h-8! group-data-[collapsible=icon]:p-1!",
            "[&>svg]:hidden",
            className
          )}
        >
          <span
            className={cn(
              "flex aspect-square size-8 shrink-0 items-center justify-center rounded-lg group-data-[collapsible=icon]:size-6",
              showOrganizationLogo
                ? "ring-sidebar-border bg-white p-0.5 ring-1"
                : "bg-sidebar-primary text-sidebar-primary-foreground"
            )}
          >
            {showOrganizationLogo && organizationLogoSrc ? (
              <Image
                src={organizationLogoSrc}
                alt=""
                width={32}
                height={32}
                className="size-full object-contain"
              />
            ) : isSyncing ? (
              <Loader2 className="size-4 animate-spin" />
            ) : isPersonal ? (
              <UserIcon className="size-4" />
            ) : (
              <Building2 className="size-4" />
            )}
          </span>
          <span className="grid min-w-0 flex-1 text-left leading-tight group-data-[collapsible=icon]:hidden">
            <span className="truncate text-sm font-medium">{triggerLabel}</span>
            {secondaryLabel && (
              <span className="text-muted-foreground truncate text-xs">{secondaryLabel}</span>
            )}
          </span>
          {showChevron && (
            <span className="ml-auto shrink-0 group-data-[collapsible=icon]:hidden">
              <ChevronsUpDown className="size-4" />
            </span>
          )}
        </SelectTrigger>
      ) : (
        <SelectTrigger
          className={cn("h-9 max-h-9 !min-h-9 max-w-[200px]", className)}
          aria-label={t("selectOrganization")}
        >
          <SelectValue aria-label={t("selectOrganization")}>
            <span className="flex items-center gap-2">
              {isSyncing && <Loader2 className="h-3.5 w-3.5 shrink-0 animate-spin" />}
              <span className="truncate text-sm">{triggerLabel}</span>
            </span>
          </SelectValue>
        </SelectTrigger>
      )}
      <SelectContent>
        {/* Personal entry stays at the top so it's always one click away. */}
        <SelectItem value={PERSONAL_VALUE}>
          <div className="flex items-center gap-2">
            <UserIcon className="h-4 w-4" />
            {t("personalWorkspace")}
          </div>
        </SelectItem>
        <SelectSeparator />
        {selectableOrganizations.length > 0 && (
          <SelectGroup>
            <SelectLabel className="text-muted-foreground text-xs">
              {t("yourOrganizations")}
            </SelectLabel>
            {selectableOrganizations.map((org) => (
              <SelectItem key={org.id} value={org.id}>
                {org.name}
              </SelectItem>
            ))}
          </SelectGroup>
        )}
        <SelectSeparator />
        <div
          role="option"
          tabIndex={0}
          className="text-muted-foreground hover:bg-accent hover:text-accent-foreground relative flex w-full cursor-pointer items-center gap-2 rounded-sm px-2 py-1.5 text-sm outline-none select-none"
          onMouseDown={(e) => {
            e.preventDefault();
            handleCreateOrganization();
          }}
          onKeyDown={(e) => {
            if (e.key === "Enter" || e.key === " ") {
              e.preventDefault();
              handleCreateOrganization();
            }
          }}
        >
          <Plus className="h-3.5 w-3.5" />
          {t("createOrganization")}
        </div>
      </SelectContent>
    </Select>
  );
}
