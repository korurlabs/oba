"use client";

import { useCallback, useMemo, useState } from "react";
import { useTranslations } from "next-intl";
import { Info, Plus, Siren, TriangleAlert } from "lucide-react";
import { Button } from "@/components/ui/button";
import { SenseyTable } from "@oba/shared/components/sensey-table";
import { useGetMe } from "@oba/shared/features/users/hooks/use-get-me";
import { adminOrganizations } from "@/features/updates/lib/organization-admin";
import { useAlertRoutes, useDeleteAlertRoute } from "../hooks/use-alert-routes";
import { useAlertRouteColumns } from "../hooks/use-alert-route-columns";
import { AlertRouteDialog } from "./alert-route-dialog";
import type { AlertRoute } from "../schemas";

/**
 * The alert map: which board receives the incidents raised by each system's
 * alerts. Every signed-in user may read it; only an OWNER or ADMIN of an
 * organization gets the manage controls, which the server enforces as well.
 */
export function AlertRoutesView() {
  const t = useTranslations("alertRoutes");
  const tErrors = useTranslations("errors");
  const { data: meResponse } = useGetMe();
  const { data: routesResponse, isPending, isError, isFetching, refetch } = useAlertRoutes();
  const deleteRoute = useDeleteAlertRoute();
  const [isDialogOpen, setIsDialogOpen] = useState(false);
  // Kept after the dialog closes, so its closing animation still shows the
  // mapping it was opened for.
  const [editingRoute, setEditingRoute] = useState<AlertRoute | undefined>(undefined);

  const canManage = adminOrganizations(meResponse?.data).length > 0;
  const routes = useMemo((): AlertRoute[] => routesResponse?.data ?? [], [routesResponse?.data]);
  const systemKeys = useMemo(() => routes.map((route) => route.systemKey), [routes]);
  const hasNoDefault = routes.length > 0 && !routes.some((route) => route.isDefault);

  const handleAdd = useCallback(() => {
    setEditingRoute(undefined);
    setIsDialogOpen(true);
  }, []);
  const handleEdit = useCallback((route: AlertRoute) => {
    setEditingRoute(route);
    setIsDialogOpen(true);
  }, []);
  const handleRemove = useCallback(
    (routeId: string) => deleteRoute.mutate(routeId),
    [deleteRoute],
  );

  const columns = useAlertRouteColumns({
    canManage,
    onEdit: handleEdit,
    onRemove: handleRemove,
    isRemoving: deleteRoute.isPending,
  });

  return (
    <div className="mx-auto max-w-4xl space-y-6 px-6 py-10">
      <div className="flex items-start justify-between gap-3">
        <div className="flex items-start gap-3">
          <Siren className="text-muted-foreground mt-1 h-6 w-6" />
          <div className="flex flex-col gap-1">
            <h1 className="text-foreground text-2xl font-bold">{t("title")}</h1>
            <p className="text-muted-foreground text-sm">{t("subtitle")}</p>
          </div>
        </div>
        {canManage && (
          <Button size="sm" className="shrink-0" onClick={handleAdd}>
            <Plus className="h-4 w-4" />
            {t("add")}
          </Button>
        )}
      </div>

      {!canManage && (
        <div className="text-muted-foreground flex items-center gap-2 text-sm">
          <Info className="h-4 w-4 shrink-0" aria-hidden="true" />
          {t("readOnly")}
        </div>
      )}

      {hasNoDefault && (
        <div
          role="status"
          className="border-warning/30 bg-warning/10 flex items-center gap-2 rounded-lg border p-3"
        >
          <TriangleAlert className="text-warning h-5 w-5 shrink-0" aria-hidden="true" />
          <p className="text-foreground text-sm">{t("noDefault")}</p>
        </div>
      )}

      {isError ? (
        <div className="bg-secondary rounded-xl p-4" role="alert">
          <p className="text-foreground text-sm font-medium">{tErrors("somethingWentWrong")}</p>
          <Button
            variant="outline"
            className="mt-3"
            disabled={isFetching}
            onClick={() => void refetch()}
          >
            {tErrors("tryAgain")}
          </Button>
        </div>
      ) : (
        <SenseyTable<AlertRoute>
          columns={columns}
          data={routes}
          isLoading={isPending}
          emptyMessage={t("empty")}
          getRowId={(route) => route.id}
        />
      )}

      {canManage && (
        <AlertRouteDialog
          route={editingRoute}
          existingSystemKeys={systemKeys}
          open={isDialogOpen}
          onOpenChange={setIsDialogOpen}
        />
      )}
    </div>
  );
}
