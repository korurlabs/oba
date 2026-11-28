"use client";

import { useTranslations } from "next-intl";
import { ArrowRight, Cloud } from "lucide-react";
import { Button } from "@oba/ui/sky/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@oba/ui/sky/card";
import { Link } from "@oba/shared/libs/i18n/navigation";
import { useAzureConnections } from "../hooks/use-azure-connections";

interface IntegrationsTabProps {
  organizationId: string;
}

/**
 * The outside providers the organization can connect to Oba, one card each
 * with its connection count and a link to its page. Shown only to an OWNER or
 * ADMIN, the only roles the API lets read connections.
 */
export function IntegrationsTab({ organizationId }: IntegrationsTabProps) {
  const t = useTranslations("integrations");
  const { data: connectionsResponse, isLoading, isError } = useAzureConnections(organizationId);
  const count = connectionsResponse?.data?.length;

  return (
    <section className="flex flex-col gap-4" aria-labelledby="organization-integrations-title">
      <div className="flex min-h-9 flex-col gap-1">
        <h2 id="organization-integrations-title" className="text-xl">
          {t("title")}
        </h2>
        <p className="text-muted-foreground text-sm">{t("description")}</p>
      </div>

      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-lg">
              <Cloud className="text-primary size-5" aria-hidden />
              {t("providers.azure.name")}
            </CardTitle>
            <CardDescription>{t("providers.azure.description")}</CardDescription>
          </CardHeader>
          <CardContent className="flex flex-wrap items-center justify-between gap-3">
            {isError && count === undefined ? (
              <span className="text-error text-sm" role="alert">
                {t("azure.loadError")}
              </span>
            ) : (
              <span className="text-muted-foreground text-sm" aria-busy={isLoading}>
                {count !== undefined ? t("providers.azure.connectionCount", { count }) : null}
              </span>
            )}
            <Button size="sm" variant="outline" asChild>
              <Link href="/integrations/azure">
                {t("providers.azure.open")}
                <ArrowRight aria-hidden="true" />
              </Link>
            </Button>
          </CardContent>
        </Card>
      </div>
    </section>
  );
}
