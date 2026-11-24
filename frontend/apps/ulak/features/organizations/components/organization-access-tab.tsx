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
import { EmptyState, TableLoading } from "@oba/ui/sky/page-states";
import { DataTable } from "@/components/data-table";
import { cn } from "@/lib/utils";
import {
  GrantedAppSchema,
  type AppGrant,
  type MemberAppAccess,
} from "@oba/shared/features/organizations/schemas";
import { useAppGrants, useMemberAppAccess, useRevokeAppGrant } from "../hooks/use-app-access";
import { useAppAccessColumns } from "../hooks/use-app-access-columns";
import { useAppName } from "./app-badges";
import { GrantAppDialog } from "./grant-app-dialog";
import { OrganizationLoadError } from "./organization-load-error";

interface AccessTabProps {
  organizationId: string;
}

/**
 * App grants of the organization and the effective apps of each member.
 * Shown only to an OWNER or ADMIN, who are the only roles that can read
 * grants, so every control is editable here.
 */
export function AccessTab({ organizationId }: AccessTabProps) {
  const t = useTranslations("organization");
  const [grantOpen, setGrantOpen] = useState(false);

  return (
    <section className="flex flex-col gap-8" aria-labelledby="organization-access-title">
      <div className="flex min-h-9 flex-wrap items-center justify-between gap-3">
        <div className="flex min-w-0 flex-col gap-1">
          <h2 id="organization-access-title" className="text-xl">
            {t("access.title")}
          </h2>
          <p className="text-muted-foreground text-sm">{t("access.description")}</p>
        </div>
        <Button size="sm" onClick={() => setGrantOpen(true)}>
          <Plus aria-hidden="true" />
          {t("access.grantAccess")}
        </Button>
      </div>

      <GrantsSection organizationId={organizationId} />

      <EffectiveAccessSection organizationId={organizationId} />

      <GrantAppDialog
        organizationId={organizationId}
        open={grantOpen}
        onOpenChange={setGrantOpen}
      />
    </section>
  );
}

/**
 * One card per grantable app with its grants. An app value this build does
 * not know yet still gets a card under its raw name, so no grant is hidden
 * from the admin who has to revoke it.
 */
function GrantsSection({ organizationId }: { organizationId: string }) {
  const t = useTranslations("organization");
  const appName = useAppName();
  const { data: grantsResponse, isLoading, isError, refetch } = useAppGrants(organizationId);

  const grantsByApp = useMemo(() => {
    const byApp = new Map<string, AppGrant[]>(
      GrantedAppSchema.options.map((app) => [app, [] as AppGrant[]])
    );
    for (const grant of grantsResponse?.data ?? []) {
      const appGrants = byApp.get(grant.app);
      if (appGrants) {
        appGrants.push(grant);
      } else {
        byApp.set(grant.app, [grant]);
      }
    }
    return [...byApp.entries()];
  }, [grantsResponse]);

  return (
    <section className="flex flex-col gap-3" aria-labelledby="organization-access-grants-title">
      <h3 id="organization-access-grants-title" className="text-lg">
        {t("access.grantsTitle")}
      </h3>

      {isLoading ? (
        <TableLoading label={t("access.grantsLoading")} />
      ) : isError && !grantsResponse ? (
        <OrganizationLoadError onRetry={() => void refetch()} />
      ) : (
        <div className="grid gap-4 md:grid-cols-2">
          {grantsByApp.map(([app, grants]) => (
            <Card key={app} className="gap-0 py-0">
              <CardHeader className="border-b py-3 [.border-b]:pb-3">
                <h4 className="text-sm">{appName(app)}</h4>
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
      )}
    </section>
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
function EffectiveAccessSection({ organizationId }: { organizationId: string }) {
  const t = useTranslations("organization");
  const columns = useAppAccessColumns();
  const { data: accessResponse, isLoading, isError, refetch } = useMemberAppAccess(organizationId);

  return (
    <section className="flex flex-col gap-3" aria-labelledby="organization-access-effective-title">
      <div className="flex flex-col gap-1">
        <h3 id="organization-access-effective-title" className="text-lg">
          {t("access.effectiveTitle")}
        </h3>
        <p className="text-muted-foreground text-sm">{t("access.effectiveDescription")}</p>
        <p className="text-muted-foreground text-sm">{t("access.ownerNote")}</p>
      </div>

      {isError && !accessResponse ? (
        <OrganizationLoadError onRetry={() => void refetch()} />
      ) : (
        <DataTable<MemberAppAccess>
          columns={columns}
          data={accessResponse?.data ?? []}
          isLoading={isLoading}
          loadingLabel={t("access.loading")}
          getRowId={(member: MemberAppAccess) => member.userId}
          emptyMessage={
            <EmptyState
              title={t("access.noMembers")}
              description={t("access.noMembersDescription")}
            />
          }
        />
      )}
    </section>
  );
}
