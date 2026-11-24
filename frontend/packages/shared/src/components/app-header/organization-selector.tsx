"use client";

import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectLabel,
  SelectTrigger,
  SelectValue,
} from "@oba/ui/components/select";
import { useQueryClient } from "@tanstack/react-query";
import { useSession } from "next-auth/react";
import { useRouter } from "@oba/shared/libs/i18n/navigation";
import { useTranslations } from "next-intl";
import { Building2, Loader2, Plus } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import { useGetMe } from "@oba/shared/features/users/hooks/use-get-me";
import type { GrantedApp } from "@oba/shared/features/users/schema";
import { useUpdateMe } from "@oba/shared/features/users/hooks/use-update-me";
import {
  OPS_GRID_ACCENT,
  OPS_GRID_CURRENT,
  OPS_GRID_HAIRLINE,
  OPS_GRID_HEADING,
  OPS_GRID_HIGHLIGHT,
  OPS_GRID_NO_INDICATOR,
  OPS_GRID_ROW,
  OPS_GRID_SHADOW,
  OPS_GRID_SURFACE,
} from "@oba/ui/components/ops-grid";
import { cn } from "@oba/ui/lib/utils";
import { markAllOrganizationQueriesStale } from "../../hooks/use-invalidate-org-queries";
import { ACCOUNT_LINKS } from "../../constants/apps";
import { organizationsWithApp } from "../../features/organizations/lib/app-access";
import { managedOrganizations } from "../../features/organizations/lib/managed-organizations";

/*
 * The look of the Korur website's language picker: a ghost trigger, and a
 * square grid panel whose rows are parted by hairlines. The current row is
 * marked by a tint, a rail on its start edge and a heavier weight, so it never
 * depends on colour alone. Long names wrap in their row and are cut only in
 * the trigger, which carries the full name as its `title`.
 */
const GRID_TRIGGER =
  "h-8 max-h-8 !min-h-8 gap-2 rounded-lg border-0 bg-transparent px-2.5 text-sm font-medium shadow-none hover:bg-muted data-[state=open]:bg-muted dark:bg-transparent dark:hover:bg-muted [&>svg:last-child]:size-3.5";
const GRID_PANEL = cn(
  OPS_GRID_SURFACE,
  OPS_GRID_SHADOW,
  "w-[min(20rem,calc(100vw-2rem))] min-w-[220px] [&_[data-radix-select-viewport]]:px-2 [&_[data-radix-select-viewport]]:py-1.5"
);
const GRID_ROW = cn(OPS_GRID_ROW, OPS_GRID_NO_INDICATOR, OPS_GRID_HIGHLIGHT);
const GRID_SELECTABLE_ROW = cn(GRID_ROW, OPS_GRID_HAIRLINE, OPS_GRID_CURRENT);

export interface OrganizationSelectorProps {
  /**
   * Where the "Create organization" row navigates: the account app's create
   * screen by default, because any user may create an organization. An in-app path is also accepted and navigates client-side.
   */
  createOrganizationHref?: string;
  /**
   * The app the host needs a grant for. When set, the menu offers only the
   * ACTIVE organizations that grant it, so a switch never lands the user in an
   * organization where the app is closed. The trigger still names a selected
   * organization without the grant, so the user sees where they are. Without
   * it, the menu offers every organization of the user.
   */
  app?: GrantedApp;
  /**
   * Offer only the organizations the user manages (OWNER or ADMIN of an ACTIVE
   * organization) instead of every organization or those granting `app`.
   */
  managedOnly?: boolean;
  /**
   * The host holds the current organization itself, as an app whose session
   * carries no default organization does. The value and the list come from
   * here and the session is not read; a switch saves the default
   * organization, then calls `onSwitch`, so the other apps follow too.
   */
  controlled?: {
    organizationId: string;
    organizations: readonly { id: string; name: string }[];
    onSwitch: (organizationId: string) => void;
  };
  /** Applied to the trigger. */
  className?: string;
}

export function OrganizationSelector({
  createOrganizationHref = ACCOUNT_LINKS.newOrganization,
  app,
  managedOnly = false,
  controlled,
  className,
}: OrganizationSelectorProps = {}) {
  const queryClient = useQueryClient();
  const router = useRouter();
  const { data: session, update } = useSession();
  const { data: meResponse, isFetching } = useGetMe();
  const { mutateAsync: updateMe } = useUpdateMe();
  const [isSwitching, setIsSwitching] = useState(false);
  // Controlled, because the "Create organization" row is not a SelectItem:
  // nothing closes the dropdown for it, and with a soft navigation the open
  // dropdown would otherwise stay over the new page.
  const [open, setOpen] = useState(false);
  const t = useTranslations();

  const me = meResponse?.data;
  const organizations: readonly { id: string; name: string }[] =
    controlled?.organizations ?? me?.organizations ?? [];
  const selectableOrganizations: readonly { id: string; name: string }[] = controlled
    ? controlled.organizations
    : managedOnly
      ? managedOrganizations(me)
      : app
        ? organizationsWithApp(me, app)
        : organizations;
  // A controlled host does not wait for the current user: its list is its own.
  const isSyncing = controlled ? isSwitching : isFetching || isSwitching;

  // The API serves no request without an organization, so there is no mode
  // without one. With no organization chosen the Select gets the empty value
  // Radix reads as "nothing selected": no row is marked and the trigger asks
  // for a choice.
  const currentOrganizationId = controlled
    ? controlled.organizationId
    : session?.user?.defaultOrganizationId || null;

  const handleCreateOrganization = () => {
    setOpen(false);
    // An absolute URL is another app; the locale-aware router only handles
    // paths of this one.
    if (/^https?:\/\//.test(createOrganizationHref)) {
      window.location.assign(createOrganizationHref);
      return;
    }
    router.push(createOrganizationHref);
  };

  const handleControlledValueChange = async (
    host: NonNullable<OrganizationSelectorProps["controlled"]>,
    organizationId: string
  ) => {
    if (organizationId === host.organizationId) {
      return;
    }
    setIsSwitching(true);
    try {
      // Saved first, so the host switches only to an organization the API
      // accepted as the default; a refusal leaves the host where it was.
      await updateMe({ defaultOrganizationId: organizationId });
      host.onSwitch(organizationId);
    } catch (error) {
      console.error("[OrganizationSelector] organization switch failed:", error);
      toast.error(t("organizationSwitchFailed"));
    } finally {
      setIsSwitching(false);
    }
  };

  const handleValueChange = async (nextOrganizationId: string) => {
    if (controlled) {
      await handleControlledValueChange(controlled, nextOrganizationId);
      return;
    }
    if (nextOrganizationId === session?.user?.defaultOrganizationId) {
      // Same value already persisted — nothing to do.
      return;
    }

    setIsSwitching(true);
    try {
      // The mutation rejects when the API refuses the change.
      await updateMe({ defaultOrganizationId: nextOrganizationId });
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

  // The selected organization's name, or the request to choose one while
  // none is chosen or its name is not loaded yet.
  const triggerLabel =
    organizations.find((org) => org.id === currentOrganizationId)?.name ?? t("selectOrganization");

  // Radix shows the placeholder in place of the children while the value is
  // empty, so both carry the same content and the trigger keeps its icon and
  // label with no organization chosen.
  const gridTriggerContent = (
    <span className="flex min-w-0 items-center gap-2">
      {isSyncing ? (
        <Loader2 className={cn("size-4 shrink-0 animate-spin", OPS_GRID_ACCENT)} />
      ) : (
        <Building2 className={cn("size-4 shrink-0", OPS_GRID_ACCENT)} />
      )}
      {/* Below `sm` the trigger is the icon alone; its aria-label names it. */}
      <span className="hidden max-w-56 truncate sm:block">{triggerLabel}</span>
    </span>
  );

  return (
    <Select
      value={currentOrganizationId ?? ""}
      onValueChange={handleValueChange}
      disabled={isSyncing}
      open={open}
      onOpenChange={setOpen}
    >
      <SelectTrigger
        className={cn(GRID_TRIGGER, className)}
        aria-label={t("selectOrganization")}
        title={triggerLabel}
      >
        <SelectValue aria-label={t("selectOrganization")} placeholder={gridTriggerContent}>
          {gridTriggerContent}
        </SelectValue>
      </SelectTrigger>
      <SelectContent className={GRID_PANEL} align="end" sideOffset={8}>
        {selectableOrganizations.length > 0 && (
          <SelectGroup>
            <SelectLabel className={OPS_GRID_HEADING}>{t("yourOrganizations")}</SelectLabel>
            {selectableOrganizations.map((org) => (
              <SelectItem key={org.id} value={org.id} className={GRID_SELECTABLE_ROW}>
                {org.name}
              </SelectItem>
            ))}
          </SelectGroup>
        )}
        <div
          role="option"
          tabIndex={0}
          className={cn(
            "relative flex w-full cursor-pointer items-center outline-none select-none",
            GRID_ROW
          )}
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
          <Plus className={cn("size-4", OPS_GRID_ACCENT)} />
          {t("createOrganization")}
        </div>
      </SelectContent>
    </Select>
  );
}
