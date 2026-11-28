"use client";

import { useMemo } from "react";
import { useTranslations } from "next-intl";
import { RefreshCw } from "lucide-react";
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from "@oba/ui/sky/sheet";
import { Button } from "@oba/ui/sky/button";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@oba/ui/sky/tabs";
import { useDateFormat } from "@oba/shared/i18n/use-date-format";
import type { AzureConnection } from "../schemas";
import { useVerifyAzureConnection } from "../hooks/use-azure-connections";
import {
  buildAzureSetupSnippets,
  toAzureSetupValues,
  type AzureSetupSnippet,
} from "../lib/setup-snippets";
import { AzureLastError } from "./azure-last-error";
import { CopyValue } from "./copy-value";

interface AzureConnectionSheetProps {
  organizationId: string;
  /**
   * The connection shown, read from the list on every render so a check's
   * result appears as soon as the list refetches. It is kept after the sheet
   * closes, so the content does not vanish under the closing animation.
   */
  connection: AzureConnection | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

/**
 * Everything an admin needs to make Azure trust one connection and to see
 * whether it does: the check result with a Verify button, the three values of
 * the federated credential, the setup snippets and the subscriptions found.
 */
export function AzureConnectionSheet({
  organizationId,
  connection,
  open,
  onOpenChange,
}: AzureConnectionSheetProps) {
  const t = useTranslations("integrations.azure");

  return (
    <Sheet open={open && connection !== null} onOpenChange={onOpenChange}>
      <SheetContent className="flex w-full flex-col gap-0 sm:max-w-2xl">
        <SheetHeader>
          <SheetTitle>{connection?.name ?? ""}</SheetTitle>
          <SheetDescription>{t("description")}</SheetDescription>
        </SheetHeader>

        {connection && (
          <div className="flex flex-1 flex-col gap-6 overflow-y-auto px-4 pb-4">
            <ConnectionStatus organizationId={organizationId} connection={connection} />
            <SetupSection connection={connection} />
            <SubscriptionList connection={connection} />
          </div>
        )}
      </SheetContent>
    </Sheet>
  );
}

/**
 * The stored check result. Verify answers 200 also when Azure refused, so
 * what is shown is the status the refetched connection carries.
 */
function ConnectionStatus({
  organizationId,
  connection,
}: {
  organizationId: string;
  connection: AzureConnection;
}) {
  const t = useTranslations("integrations.azure");
  const { formatDateTime } = useDateFormat();
  const verify = useVerifyAzureConnection(organizationId);
  const isVerifying = verify.isPending && verify.variables === connection.id;

  return (
    <section className="flex flex-col gap-3" aria-label={t("columns.status")}>
      <dl className="grid grid-cols-[max-content_1fr] gap-x-4 gap-y-2 text-sm">
        <dt className="text-muted-foreground">{t("columns.status")}</dt>
        <dd>{t(`statuses.${connection.status}`)}</dd>
        <dt className="text-muted-foreground">{t("columns.tenantId")}</dt>
        <dd className="font-mono text-xs break-all">{connection.tenantId}</dd>
        <dt className="text-muted-foreground">{t("form.clientIdLabel")}</dt>
        <dd className="font-mono text-xs break-all">{connection.clientId ?? t("noError")}</dd>
        <dt className="text-muted-foreground">{t("columns.lastVerifiedAt")}</dt>
        <dd>
          {connection.lastVerifiedAt
            ? formatDateTime(connection.lastVerifiedAt)
            : t("neverVerified")}
        </dd>
        <dt className="text-muted-foreground">{t("columns.lastError")}</dt>
        <dd>
          <AzureLastError lastError={connection.lastError} />
        </dd>
      </dl>
      <Button
        size="sm"
        className="self-start"
        disabled={verify.isPending}
        aria-busy={isVerifying}
        onClick={() => verify.mutate(connection.id)}
      >
        <RefreshCw aria-hidden="true" />
        {isVerifying ? t("actions.verifying") : t("actions.verify")}
      </Button>
    </section>
  );
}

/** The federated credential's values and the snippets that create it. */
function SetupSection({ connection }: { connection: AzureConnection }) {
  const t = useTranslations("integrations.azure.setup");
  const values = useMemo(() => toAzureSetupValues(connection), [connection]);
  const snippets = useMemo(() => (values ? buildAzureSetupSnippets(values) : null), [values]);

  return (
    <section className="flex flex-col gap-3" aria-labelledby="azure-setup-title">
      <div className="flex flex-col gap-1">
        <h3 id="azure-setup-title" className="text-base font-medium">
          {t("title")}
        </h3>
        <p className="text-muted-foreground text-sm">{t("description")}</p>
      </div>

      {!values || !snippets ? (
        <p className="text-muted-foreground text-sm">{t("noIssuer")}</p>
      ) : (
        <>
          <CopyValue label={t("issuerLabel")} value={values.issuer} />
          <CopyValue label={t("subjectLabel")} value={values.subject} />
          <CopyValue label={t("audienceLabel")} value={values.audience} />

          <Tabs defaultValue="managedIdentity">
            <TabsList>
              <TabsTrigger value="managedIdentity">{t("managedIdentityTab")}</TabsTrigger>
              <TabsTrigger value="appRegistration">{t("appRegistrationTab")}</TabsTrigger>
            </TabsList>
            <TabsContent value="managedIdentity">
              <SnippetTabs snippet={snippets.managedIdentity} />
            </TabsContent>
            <TabsContent value="appRegistration">
              <SnippetTabs snippet={snippets.appRegistration} />
            </TabsContent>
          </Tabs>
          <p className="text-muted-foreground text-xs">{t("placeholdersNote")}</p>
        </>
      )}
    </section>
  );
}

function SnippetTabs({ snippet }: { snippet: AzureSetupSnippet }) {
  const t = useTranslations("integrations.azure.setup");

  return (
    <Tabs defaultValue="azCli">
      <TabsList>
        <TabsTrigger value="azCli">{t("azCliTab")}</TabsTrigger>
        <TabsTrigger value="terraform">{t("terraformTab")}</TabsTrigger>
      </TabsList>
      <TabsContent value="azCli">
        <CopyValue label={t("azCliTab")} value={snippet.azCli} multiline />
      </TabsContent>
      <TabsContent value="terraform">
        <CopyValue label={t("terraformTab")} value={snippet.terraform} multiline />
      </TabsContent>
    </Tabs>
  );
}

/** The subscriptions of the last successful check, in the API's name order. */
function SubscriptionList({ connection }: { connection: AzureConnection }) {
  const t = useTranslations("integrations.azure.subscriptions");

  return (
    <section className="flex flex-col gap-3" aria-labelledby="azure-subscriptions-title">
      <div className="flex flex-col gap-1">
        <h3 id="azure-subscriptions-title" className="text-base font-medium">
          {t("title")}
        </h3>
        <p className="text-muted-foreground text-sm">{t("description")}</p>
      </div>
      {connection.subscriptions.length === 0 ? (
        <p className="text-muted-foreground rounded-lg border py-6 text-center text-sm">
          {t("empty")}
        </p>
      ) : (
        <ul className="divide-border divide-y rounded-lg border">
          {connection.subscriptions.map((subscription) => (
            <li key={subscription.subscriptionId} className="flex items-center gap-3 px-3 py-2">
              <div className="min-w-0 flex-1">
                <p className="text-foreground truncate text-sm font-medium">
                  {subscription.displayName}
                </p>
                <p className="text-muted-foreground truncate font-mono text-xs">
                  {subscription.subscriptionId}
                </p>
              </div>
              <span className="text-muted-foreground text-xs">
                <span className="sr-only">{t("columns.state")}: </span>
                {subscription.state}
              </span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
