"use client";

import { useTranslations } from "next-intl";
import { Building } from "lucide-react";
import { ACCOUNT_LINKS } from "@oba/shared/constants/apps";
import { Button } from "@/components/ui/button";
import { useOrganizationId } from "@/hooks/use-organization-id";

/**
 * Renders the sidebar and the page only for a user with a current
 * organization: every Kalkan page belongs to one, and the API refuses a
 * request without it. A user without one is sent to the account app to create
 * or join one. The check reads the same organization the queries are keyed
 * on, so the server render already shows the right screen.
 */
export function OrganizationGate({ children }: { children: React.ReactNode }) {
  const t = useTranslations("organizationGate");
  const organizationId = useOrganizationId();

  if (!organizationId) {
    return (
      <div className="flex flex-1 items-center justify-center p-4 py-16">
        <div className="w-full max-w-md space-y-6 text-center">
          <div className="flex justify-center">
            <div className="bg-primary/10 rounded-full p-4">
              <Building className="text-primary h-12 w-12" aria-hidden="true" />
            </div>
          </div>
          <div className="space-y-2">
            <h1 className="text-foreground text-2xl font-bold tracking-tight">{t("title")}</h1>
            <p className="text-muted-foreground text-sm">{t("description")}</p>
          </div>
          <Button asChild>
            <a href={ACCOUNT_LINKS.profile}>{t("action")}</a>
          </Button>
        </div>
      </div>
    );
  }

  return <>{children}</>;
}
