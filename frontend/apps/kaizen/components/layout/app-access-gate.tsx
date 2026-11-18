"use client";

import { useEffect, useRef } from "react";
import { Building2, ShieldOff, UserCog } from "lucide-react";
import { useTranslations } from "next-intl";
import { OrganizationSelector } from "@oba/shared/components/app-header/organization-selector";
import { ACCOUNT_LINKS } from "@oba/shared/constants/apps";
import { GrantedApp } from "@oba/shared/features/users/schema";
import { usePathname, useRouter } from "@oba/shared/libs/i18n/navigation";
import { Button } from "@/components/ui/button";
import { isKaizenShut, useKaizenAccess } from "./use-kaizen-access";

/**
 * The dashboard pages that work without an organization: they read and write
 * only the user's own preferences, never a board, ticket or chat. `/profile`
 * and `/settings` need no entry: their pages redirect to the account app on
 * the server, before anything renders.
 */
const ORGANIZATION_FREE_PATHS: readonly string[] = ["/settings/columns"];

interface AppAccessGateProps {
  children: React.ReactNode;
}

/**
 * Shows a screen in place of the page when Kaizen cannot serve it: the
 * choose-an-organization screen when no organization is selected (the
 * personal workspace; every board, ticket and chat belongs to an
 * organization), and the no-access panel when the selected organization does
 * not grant Kaizen to the user. The API refuses every Kaizen call in both
 * cases; the screen says why, instead of a page of error states. The pages in
 * `ORGANIZATION_FREE_PATHS` still render without an organization.
 *
 * The page renders in every other state of `useKaizenAccess`, also while it is
 * still pending, so the server-rendered page shows at once; the queries that
 * run on every page wait for the same answer before they go out. The gate
 * sits inside the content area, so the sidebar stays and the user can switch
 * to an organization with Kaizen.
 *
 * The server renders no Kaizen data while Kaizen is shut, and a board or
 * ticket page renders nothing at all. An organization switch does not reload
 * the page, so when Kaizen opens the gate asks the server to render the page
 * again, now with its data. A pending answer between the two (the user is
 * still loading after the switch) does not count as a change.
 */
export function AppAccessGate({ children }: AppAccessGateProps) {
  const access = useKaizenAccess();
  const router = useRouter();
  const pathname = usePathname();
  const wasShut = useRef(isKaizenShut(access));

  useEffect(() => {
    if (access.status === "pending") return;
    const reopened = wasShut.current && access.status === "open";
    wasShut.current = isKaizenShut(access);
    if (reopened) router.refresh();
  }, [access, router]);

  if (access.status === "closed") {
    return <NoAppAccess organizationName={access.organization.name} />;
  }
  if (access.status === "noOrganization" && !ORGANIZATION_FREE_PATHS.includes(pathname)) {
    return <NoOrganization />;
  }
  return children;
}

/**
 * Asks the user to choose an organization. Kaizen has no personal boards, so
 * the personal workspace has nothing to show. The switcher offers the
 * organizations that grant Kaizen, the same menu as the sidebar.
 */
function NoOrganization() {
  const t = useTranslations("noOrganization");

  return (
    <div className="bg-background flex min-h-full items-center justify-center p-4">
      <div className="w-full max-w-md space-y-6 text-center">
        <div className="flex justify-center">
          <div className="bg-muted rounded-full p-4">
            <Building2 className="text-muted-foreground h-12 w-12" aria-hidden="true" />
          </div>
        </div>

        <div className="space-y-2">
          <h1 className="text-foreground text-2xl font-bold tracking-tight">{t("title")}</h1>
          <p className="text-muted-foreground text-sm">{t("description")}</p>
        </div>

        <div className="flex justify-center">
          <OrganizationSelector app={GrantedApp.KAIZEN} className="w-full max-w-xs" />
        </div>
      </div>
    </div>
  );
}

interface NoAppAccessProps {
  organizationName: string;
}

/**
 * Tells the user that Kaizen is closed to them in this organization and who
 * can open it. Only an owner or admin grants an app, so the panel offers no
 * action of its own beyond the account app.
 */
function NoAppAccess({ organizationName }: NoAppAccessProps) {
  const t = useTranslations("noAppAccess");

  return (
    <div className="bg-background flex min-h-full items-center justify-center p-4">
      <div className="w-full max-w-md space-y-6 text-center">
        <div className="flex justify-center">
          <div className="bg-muted rounded-full p-4">
            <ShieldOff className="text-muted-foreground h-12 w-12" aria-hidden="true" />
          </div>
        </div>

        <div className="space-y-2">
          <h1 className="text-foreground text-2xl font-bold tracking-tight">
            {t("title", { organization: organizationName })}
          </h1>
          <p className="text-muted-foreground text-sm">{t("description")}</p>
        </div>

        <Button asChild variant="outline" className="gap-2">
          <a href={ACCOUNT_LINKS.profile}>
            <UserCog className="h-4 w-4" aria-hidden="true" />
            {t("openAccount")}
          </a>
        </Button>
      </div>
    </div>
  );
}
