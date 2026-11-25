"use client";

import { useCallback, useMemo, useState } from "react";
import { useTranslations } from "next-intl";
import { parseAsStringEnum, useQueryStates } from "nuqs";
import { ArrowLeft, Info, ListChecks, Plus } from "lucide-react";
import { SenseyTable } from "@oba/shared/components/sensey-table";
import { useGetMe } from "@oba/shared/features/users/hooks/use-get-me";
import { Link } from "@oba/shared/libs/i18n/navigation";
import { Button } from "@/components/ui/button";
import { InlineAlert } from "@/components/ui/inline-alert";
import { PageHeader } from "@/components/ui/page-header";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { isOrganizationAdmin } from "@/features/updates/lib/organization-admin";
import { useChecks, useSetCheckOutcome, useSetCheckStatus } from "../hooks/use-checks";
import { useCheckColumns } from "../hooks/use-check-columns";
import {
  CHECK_STATUSES,
  CheckStatusSchema,
  type Check,
  type CheckOutcome,
  type CheckStatus,
} from "../schemas";
import { CheckDeleteDialog } from "./check-delete-dialog";
import { CheckDialog } from "./check-dialog";
import { CyberscoreLoadError } from "./cyberscore-load-error";

/**
 * URL key of the status filter; no value lists every check. The checks page
 * parses the same `status` key on the server to prefetch the filtered list.
 */
export const checkListFilterParams = {
  status: parseAsStringEnum<CheckStatus>([...CHECK_STATUSES]),
};

/** The status filter's value for "every status"; a Select item cannot have an empty value. */
const ALL_STATUSES = "ALL";

/**
 * The organization's checks, in the server's order (category, sort order,
 * name). Every member may read them; only an OWNER or ADMIN of the default
 * organization gets the add, edit, delete and row select controls, which the
 * server enforces as well.
 */
export function CheckList() {
  const t = useTranslations("cyberscore");
  const tErrors = useTranslations("errors");
  const { data: meResponse } = useGetMe();
  const me = meResponse?.data;
  const canManage = isOrganizationAdmin(me, me?.defaultOrganizationId);

  const [filters, setFilters] = useQueryStates(checkListFilterParams);
  const status = filters.status ?? undefined;
  const { data: checksResponse, isPending, isError, isFetching, refetch } = useChecks(status);
  const checks = useMemo((): Check[] => checksResponse?.data ?? [], [checksResponse?.data]);

  const setOutcome = useSetCheckOutcome();
  const setStatus = useSetCheckStatus();
  const savingCheckId =
    (setOutcome.isPending ? setOutcome.variables?.checkId : undefined) ??
    (setStatus.isPending ? setStatus.variables?.checkId : undefined) ??
    null;

  const [isDialogOpen, setIsDialogOpen] = useState(false);
  const [isDeleteOpen, setIsDeleteOpen] = useState(false);
  // Kept after a dialog closes, so its closing animation still shows the
  // check it was opened for.
  const [editingCheck, setEditingCheck] = useState<Check | undefined>(undefined);
  const [deletingCheck, setDeletingCheck] = useState<Check | undefined>(undefined);

  const handleAdd = useCallback(() => {
    setEditingCheck(undefined);
    setIsDialogOpen(true);
  }, []);
  const handleEdit = useCallback((check: Check) => {
    setEditingCheck(check);
    setIsDialogOpen(true);
  }, []);
  const handleDelete = useCallback((check: Check) => {
    setDeletingCheck(check);
    setIsDeleteOpen(true);
  }, []);
  const { mutate: mutateOutcome } = setOutcome;
  const { mutate: mutateStatus } = setStatus;
  const handleOutcomeChange = useCallback(
    (check: Check, outcome: CheckOutcome) => mutateOutcome({ checkId: check.id, outcome }),
    [mutateOutcome]
  );
  const handleStatusChange = useCallback(
    (check: Check, nextStatus: CheckStatus) =>
      mutateStatus({ checkId: check.id, status: nextStatus }),
    [mutateStatus]
  );

  const handleFilterChange = (value: string) => {
    const parsed = CheckStatusSchema.safeParse(value);
    void setFilters({ status: parsed.success ? parsed.data : null });
  };

  const columns = useCheckColumns({
    canManage,
    onEdit: handleEdit,
    onDelete: handleDelete,
    onOutcomeChange: handleOutcomeChange,
    onStatusChange: handleStatusChange,
    savingCheckId,
  });

  return (
    <div className="mx-auto flex w-full max-w-6xl flex-col gap-6 px-4 py-6 sm:px-6 sm:py-8">
      <Button variant="ghost" size="sm" className="-ml-2 gap-1.5 self-start" asChild>
        <Link href="/cyberscore">
          <ArrowLeft className="h-4 w-4" aria-hidden="true" />
          {t("checks.back")}
        </Link>
      </Button>

      <PageHeader title={t("checks.title")} description={t("checks.description")}>
        <div className="flex items-center gap-2">
          <Select value={status ?? ALL_STATUSES} onValueChange={handleFilterChange}>
            <SelectTrigger size="sm" className="h-9 w-44" aria-label={t("checks.filter.status")}>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL_STATUSES}>{t("checks.filter.allStatuses")}</SelectItem>
              {CHECK_STATUSES.map((value) => (
                <SelectItem key={value} value={value}>
                  {t(`statuses.${value}`)}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          {canManage && (
            <Button size="sm" className="shrink-0" onClick={handleAdd}>
              <Plus className="h-4 w-4" />
              {t("checks.add")}
            </Button>
          )}
        </div>
      </PageHeader>

      {!canManage && (
        <InlineAlert
          variant="info"
          icon={<Info aria-hidden="true" />}
          description={t("checks.readOnly")}
        />
      )}

      {isError ? (
        <CyberscoreLoadError
          message={tErrors("somethingWentWrong")}
          isRetrying={isFetching}
          onRetry={() => void refetch()}
        />
      ) : (
        <SenseyTable<Check>
          columns={columns}
          data={checks}
          isLoading={isPending}
          emptyMessage={
            <div className="flex flex-col items-center gap-2 py-8 text-center">
              <span
                aria-hidden
                className="border-primary/40 text-primary mb-2 flex size-12 items-center justify-center border border-dashed"
              >
                <ListChecks className="size-6" />
              </span>
              <span className="text-foreground text-sm font-semibold">{t("checks.empty")}</span>
              {canManage && !status && (
                <span className="text-muted-foreground text-sm">
                  {t("checks.emptyDescription")}
                </span>
              )}
            </div>
          }
          getRowId={(check) => check.id}
        />
      )}

      {canManage && (
        <>
          <CheckDialog check={editingCheck} open={isDialogOpen} onOpenChange={setIsDialogOpen} />
          <CheckDeleteDialog
            check={deletingCheck}
            open={isDeleteOpen}
            onOpenChange={setIsDeleteOpen}
          />
        </>
      )}
    </div>
  );
}
