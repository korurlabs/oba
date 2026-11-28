"use client";

import { useCallback, useState } from "react";
import { useTranslations } from "next-intl";
import { Info, Plus } from "lucide-react";
import { Alert, AlertDescription, AlertTitle } from "@oba/ui/sky/alert";
import { Button } from "@oba/ui/sky/button";
import { EmptyState } from "@oba/ui/sky/page-states";
import { DataTable } from "@/components/data-table";
import { OrganizationLoadError } from "@/features/organizations/components/organization-load-error";
import type { AzureConnection } from "../schemas";
import {
  useAzureConnections,
  useDeleteAzureConnection,
  useVerifyAzureConnection,
} from "../hooks/use-azure-connections";
import { useAzureConnectionColumns } from "./azure-connection-columns";
import { AzureConnectionFormDialog } from "./azure-connection-form-dialog";
import { AzureConnectionSheet } from "./azure-connection-sheet";

/** Which panel is open, and for which connection (null: a new connection). */
type OpenPanel =
  | { kind: "form"; connectionId: string | null }
  | { kind: "sheet"; connectionId: string }
  | null;

interface AzureConnectionsTabProps {
  organizationId: string;
}

/**
 * The Azure connections of the organization. Shown only to an OWNER or ADMIN,
 * the only roles the API lets read connections, so every control is editable.
 */
export function AzureConnectionsTab({ organizationId }: AzureConnectionsTabProps) {
  const t = useTranslations("integrations.azure");
  const {
    data: connectionsResponse,
    isLoading,
    isError,
    refetch,
  } = useAzureConnections(organizationId);
  const { mutate: verifyConnection, isPending: isVerifying } =
    useVerifyAzureConnection(organizationId);
  const { mutate: deleteConnection, isPending: isDeleting } =
    useDeleteAzureConnection(organizationId);
  const [panel, setPanel] = useState<OpenPanel>(null);
  // The last connection a panel was opened for, kept while the panel closes
  // so its content does not vanish under the closing animation.
  const [panelConnectionId, setPanelConnectionId] = useState<string | null>(null);

  const connections = connectionsResponse?.data ?? [];
  // Read from the list on every render, so the sheet shows the result of the
  // last check as soon as the list refetches.
  const panelConnection =
    connections.find((connection) => connection.id === panelConnectionId) ?? null;
  // Every connection carries the deployment's issuer, so one without it means
  // Azure cannot trust this deployment. With no connection yet, create says so.
  const isUnavailable = connections.some((connection) => connection.setup.issuer === null);

  const openPanel = useCallback((next: NonNullable<OpenPanel>) => {
    setPanelConnectionId(next.connectionId);
    setPanel(next);
  }, []);

  // The handlers are dependencies of the column memo. A new identity rebuilds
  // every column and remounts every cell, which closes an open row menu or
  // dialog, so they are kept stable.
  const handleOpen = useCallback(
    (connection: AzureConnection) => openPanel({ kind: "sheet", connectionId: connection.id }),
    [openPanel]
  );
  const handleEdit = useCallback(
    (connection: AzureConnection) => openPanel({ kind: "form", connectionId: connection.id }),
    [openPanel]
  );
  const handleVerify = useCallback(
    (connectionId: string) => verifyConnection(connectionId),
    [verifyConnection]
  );
  const handleDelete = useCallback(
    (connectionId: string) => deleteConnection(connectionId),
    [deleteConnection]
  );

  const columns = useAzureConnectionColumns({
    onOpen: handleOpen,
    onEdit: handleEdit,
    onVerify: handleVerify,
    onDelete: handleDelete,
    isVerifying,
    isDeleting,
  });

  const closePanel = (isOpen: boolean) => {
    if (!isOpen) setPanel(null);
  };

  return (
    <section className="flex flex-col gap-4" aria-labelledby="organization-azure-title">
      <div className="flex min-h-9 flex-wrap items-center justify-between gap-3">
        <div className="flex flex-col gap-1">
          <h2 id="organization-azure-title" className="text-xl">
            {t("title")}
          </h2>
          <p className="text-muted-foreground text-sm">{t("description")}</p>
        </div>
        <Button size="sm" onClick={() => openPanel({ kind: "form", connectionId: null })}>
          <Plus aria-hidden="true" />
          {t("newConnection")}
        </Button>
      </div>

      {isUnavailable && (
        <Alert>
          <Info aria-hidden="true" />
          <AlertTitle>{t("unavailableTitle")}</AlertTitle>
          <AlertDescription>{t("unavailableDescription")}</AlertDescription>
        </Alert>
      )}

      {isError && !connectionsResponse ? (
        <OrganizationLoadError onRetry={() => void refetch()} />
      ) : (
        <DataTable<AzureConnection>
          columns={columns}
          data={connections}
          isLoading={isLoading}
          loadingLabel={t("loading")}
          getRowId={(connection: AzureConnection) => connection.id}
          emptyMessage={<EmptyState title={t("emptyTitle")} description={t("emptyDescription")} />}
        />
      )}

      <AzureConnectionFormDialog
        organizationId={organizationId}
        connection={panel?.kind === "form" ? panelConnection : null}
        open={panel?.kind === "form"}
        onOpenChange={closePanel}
      />
      <AzureConnectionSheet
        organizationId={organizationId}
        connection={panelConnection}
        open={panel?.kind === "sheet"}
        onOpenChange={closePanel}
      />
    </section>
  );
}
