"use client";

import { useMemo, useState } from "react";
import { useTranslations } from "next-intl";
import { Plus, Trash2 } from "lucide-react";
import { Badge } from "@oba/ui/sky/badge";
import { Button } from "@oba/ui/sky/button";
import { Card, CardContent, CardHeader } from "@oba/ui/sky/card";
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@oba/ui/sky/dialog";
import { ListToolbar } from "@oba/ui/sky/list-toolbar";
import { ListEmptyState, TableLoading } from "@oba/ui/sky/page-states";
import { SearchInput } from "@oba/ui/sky/search-input";
import { Tabs, TabsList, TabsTrigger } from "@oba/ui/sky/tabs";
import { DataTable } from "@/components/data-table";
import { useClearSearchLabel, useListEmptyLabels } from "@/components/list-labels";
import { cn } from "@/lib/utils";
import {
  GrantedAppSchema,
  type AppGrant,
  type MemberAppAccess,
} from "@oba/shared/features/organizations/schemas";
import { useAppGrants, useMemberAppAccess, useRevokeAppGrant } from "../hooks/use-app-access";
import { useAppAccessColumns } from "../hooks/use-app-access-columns";
import { useAccessFilterParams } from "../hooks/use-access-filter-params";
import {
  ACCESS_TABS,
  filterGrants,
  filterMemberAccess,
  hasActiveAccessFilter,
  isAccessTab,
} from "../lib/access-filters";
import { useAppName } from "./app-badges";
import { GrantAppDialog } from "./grant-app-dialog";
import { OrganizationLoadError } from "./organization-load-error";

interface AccessTabProps {
  organizationId: string;
}

/**
 * The organization's app access in two tabs, with the tab and a search in
 * the URL: the grants per app, searched by member or group, and the apps
 * each member ends up with, searched by name. Both lists come whole from the
 * API, so the search runs here. Shown only to an OWNER or ADMIN, who are the
 * only roles that can read grants, so every control is editable here.
 */
export function AccessTab({ organizationId }: AccessTabProps) {
  const t = useTranslations("organization");
  const [grantOpen, setGrantOpen] = useState(false);
  const [filters, setFilters] = useAccessFilterParams();
  const clearSearchLabel = useClearSearchLabel();
  const isFiltered = hasActiveAccessFilter(filters);
  const clearSearch = () => void setFilters({ search: null });

  const tabs = (
    <Tabs
      value={filters.tab}
      onValueChange={(value) => {
        if (!isAccessTab(value)) return;
        // The search belongs to the list that was open; the other tab starts afresh.
        void setFilters({ tab: value, search: null });
      }}
    >
      <TabsList aria-label={t("access.tabsLabel")}>
        {ACCESS_TABS.map((tab) => (
          <TabsTrigger key={tab} value={tab}>
            {t(`access.tabs.${tab}`)}
          </TabsTrigger>
        ))}
      </TabsList>
    </Tabs>
  );

  return (
    <div className="flex flex-col gap-4">
      <ListToolbar
        // A new key per tab drops the typed text and its pending search with the old tab.
        search={
          <SearchInput
            key={filters.tab}
            value={filters.search}
            onSearchChange={(search) => void setFilters({ search: search ?? null })}
            label={
              filters.tab === "grants"
                ? t("access.searchGrantsLabel")
                : t("access.searchMembersLabel")
            }
            clearLabel={clearSearchLabel}
          />
        }
        tabs={tabs}
        create={
          <Button onClick={() => setGrantOpen(true)}>
            <Plus aria-hidden="true" />
            {t("access.grantAccess")}
          </Button>
        }
      />

      {filters.tab === "grants" ? (
        <GrantsList
          organizationId={organizationId}
          search={filters.search}
          isFiltered={isFiltered}
          onClearSearch={clearSearch}
        />
      ) : (
        <EffectiveAccessList
          organizationId={organizationId}
          search={filters.search}
          isFiltered={isFiltered}
          onClearSearch={clearSearch}
        />
      )}

      <GrantAppDialog
        organizationId={organizationId}
        open={grantOpen}
        onOpenChange={setGrantOpen}
      />
    </div>
  );
}

interface AccessListProps {
  organizationId: string;
  search: string | null;
  isFiltered: boolean;
  onClearSearch: () => void;
}

/**
 * One card per grantable app with its grants. An app value this build does
 * not know yet still gets a card under its raw name, so no grant is hidden
 * from the admin who has to revoke it. While a search narrows the grants,
 * only the apps with a matching grant keep their card.
 */
function GrantsList({ organizationId, search, isFiltered, onClearSearch }: AccessListProps) {
  const t = useTranslations("organization");
  const appName = useAppName();
  const { data: grantsResponse, isLoading, isError, refetch } = useAppGrants(organizationId);
  const emptyLabels = useListEmptyLabels({
    emptyTitle: t("access.noGrants"),
    emptyDescription: "",
    filteredTitle: t("access.grantsFilteredTitle"),
  });

  const grantsByApp = useMemo(() => {
    const byApp = new Map<string, AppGrant[]>(
      GrantedAppSchema.options.map((app) => [app, [] as AppGrant[]])
    );
    for (const grant of filterGrants(grantsResponse?.data ?? [], search)) {
      const appGrants = byApp.get(grant.app);
      if (appGrants) {
        appGrants.push(grant);
      } else {
        byApp.set(grant.app, [grant]);
      }
    }
    const entries = [...byApp.entries()];
    return isFiltered ? entries.filter(([, grants]) => grants.length > 0) : entries;
  }, [grantsResponse, search, isFiltered]);

  if (isLoading) return <TableLoading label={t("access.grantsLoading")} />;
  if (isError && !grantsResponse) return <OrganizationLoadError onRetry={() => void refetch()} />;
  if (grantsByApp.length === 0) {
    return <ListEmptyState filtered onClearFilters={onClearSearch} labels={emptyLabels} />;
  }

  return (
    <div className="grid gap-4 md:grid-cols-2">
      {grantsByApp.map(([app, grants]) => (
        <Card key={app} className="gap-0 py-0">
          <CardHeader className="border-b py-3 [.border-b]:pb-3">
            <h2 className="text-sm">{appName(app)}</h2>
          </CardHeader>
          <CardContent className="px-0">
            {grants.length === 0 ? (
              <p className="text-muted-foreground px-6 py-6 text-center text-sm">
                {t("access.noGrants")}
              </p>
            ) : (
              <ul className="divide-y">
                {grants.map((grant) => (
                  <GrantRow
                    key={grant.id}
                    organizationId={organizationId}
                    grant={grant}
                    appLabel={appName(app)}
                  />
                ))}
              </ul>
            )}
          </CardContent>
        </Card>
      ))}
    </div>
  );
}

/** One grant with its target and a revoke that asks first. */
function GrantRow({
  organizationId,
  grant,
  appLabel,
}: {
  organizationId: string;
  grant: AppGrant;
  appLabel: string;
}) {
  const t = useTranslations("organization");
  const revokeGrant = useRevokeAppGrant(organizationId);
  const isGroup = grant.targetType === "GROUP";
  const isEveryone = grant.groupType === "EVERYONE";
  const targetName = (isGroup ? grant.groupName : grant.userFullName) ?? "";

  return (
    <li className="flex items-center gap-3 px-6 py-2">
      <span
        className={cn("text-foreground min-w-0 flex-1 truncate text-sm", isEveryone && "font-bold")}
      >
        {targetName}
      </span>
      <Badge variant="outline">{isGroup ? t("access.targetGroup") : t("access.targetUser")}</Badge>
      <Dialog>
        <DialogTrigger asChild>
          <Button
            variant="ghost"
            size="icon-sm"
            className="text-destructive hover:text-destructive"
            disabled={revokeGrant.isPending}
          >
            <Trash2 aria-hidden="true" />
            <span className="sr-only">{t("access.revoke")}</span>
          </Button>
        </DialogTrigger>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("access.revokeConfirmTitle")}</DialogTitle>
            <DialogDescription>
              {t("access.revokeConfirmDescription", { app: appLabel, target: targetName })}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <DialogClose asChild>
              <Button variant="outline">{t("access.cancel")}</Button>
            </DialogClose>
            <DialogClose asChild>
              <Button variant="destructive" onClick={() => revokeGrant.mutate(grant.id)}>
                {t("access.revoke")}
              </Button>
            </DialogClose>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </li>
  );
}

/** The apps each active member can open, whatever grant or role gives them. */
function EffectiveAccessList({
  organizationId,
  search,
  isFiltered,
  onClearSearch,
}: AccessListProps) {
  const t = useTranslations("organization");
  const columns = useAppAccessColumns();
  const { data: accessResponse, isLoading, isError, refetch } = useMemberAppAccess(organizationId);
  const emptyLabels = useListEmptyLabels({
    emptyTitle: t("access.noMembers"),
    emptyDescription: t("access.noMembersDescription"),
    filteredTitle: t("access.membersFilteredTitle"),
  });
  const members = useMemo(
    () => filterMemberAccess(accessResponse?.data ?? [], search),
    [accessResponse?.data, search]
  );

  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-col gap-1">
        <p className="text-muted-foreground text-sm">{t("access.effectiveDescription")}</p>
        <p className="text-muted-foreground text-sm">{t("access.ownerNote")}</p>
      </div>

      {isError && !accessResponse ? (
        <OrganizationLoadError onRetry={() => void refetch()} />
      ) : (
        <DataTable<MemberAppAccess>
          columns={columns}
          data={members}
          isLoading={isLoading}
          loadingLabel={t("access.loading")}
          getRowId={(member: MemberAppAccess) => member.userId}
          emptyMessage={
            <ListEmptyState
              filtered={isFiltered}
              onClearFilters={onClearSearch}
              labels={emptyLabels}
            />
          }
        />
      )}
    </div>
  );
}
