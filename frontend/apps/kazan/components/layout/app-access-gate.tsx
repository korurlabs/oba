"use client";

import { useEffect, useRef } from "react";
import { Building2, ShieldOff, UserCog } from "lucide-react";
import { useTranslations } from "next-intl";
import { OrganizationSelector } from "@oba/shared/components/app-header/organization-selector";
import { ACCOUNT_LINKS } from "@oba/shared/constants/apps";
import { GrantedApp } from "@oba/shared/features/users/schema";
import { usePathname, useRouter } from "@oba/shared/libs/i18n/navigation";
import { Button } from "@/components/ui/button";
import { isKazanShut, useKazanAccess } from "./use-kazan-access";

/**
 * The dashboard pages that work without an organization: they read and write
 * only the user's own preferences, never a board, ticket or chat.
 */
const ORGANIZATION_FREE_PATHS: readonly string[] = ["/settings/columns"];

interface AppAccessGateProps {
  children: React.ReactNode;
}

/**
 * Shows a screen in place of the page when Kazan cannot serve it: the
 * choose-an-organization screen when no organization is chosen (every
 * board, ticket and chat belongs to an organization), and the no-access panel when the selected organization does
 * not grant Kazan to the user. The API refuses every Kazan call in both
 * cases; the screen says why, instead of a page of error states. The pages in
 * `ORGANIZATION_FREE_PATHS` still render without an organization.
 *
 * The page renders in every other state of `useKazanAccess`, also while it is
 * still pending, so the server-rendered page shows at once; the queries that
 * run on every page wait for the same answer before they go out. The gate
 * sits inside the content area, so the top bar stays and the user can switch
 * to an organization with Kazan from its selector.
 *
 * The server renders no Kazan data while Kazan is shut, and a board or
 * ticket page renders nothing at all. An organization switch does not reload
 * the page, so when Kazan opens the gate asks the server to render the page
 * again, now with its data. A pending answer between the two (the user is
 * still loading after the switch) does not count as a change.
 */
export function AppAccessGate({ children }: AppAccessGateProps) {
  const access = useKazanAccess();
  const router = useRouter();
  const pathname = usePathname();
  const wasShut = useRef(isKazanShut(access));

  useEffect(() => {
    if (access.status === "pending") return;
    const reopened = wasShut.current && access.status === "open";
    wasShut.current = isKazanShut(access);
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
 * Asks the user to choose an organization. Kazan has no boards outside an
 * organization, so without one it has nothing to show. The switcher offers
 * the organizations that grant Kazan, the same menu as the top bar's.
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
          <OrganizationSelector app={GrantedApp.KAZAN} className="w-full max-w-xs" />
        </div>
      </div>
    </div>
  );
}

interface NoAppAccessProps {
  organizationName: string;
}

/**
 * Tells the user that Kazan is closed to them in this organization and who
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
