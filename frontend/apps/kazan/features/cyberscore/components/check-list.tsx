"use client";

import { useCallback, useMemo, useState, type Dispatch, type SetStateAction } from "react";
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
  CHECK_CATEGORIES,
  CHECK_STATUSES,
  CheckStatusSchema,
  type Check,
  type CheckCategory,
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
 * A row of the check table: a check, or the header row of one category,
 * which carries the checks it groups in `groupChecks`.
 */
export type CheckListRow = Check & { groupChecks?: Check[] };

function isCategoryHeader(row: CheckListRow): boolean {
  return row.groupChecks !== undefined;
}

/**
 * Table rows for the checks: one header row per category that has checks, in
 * the category's declared order and with uncategorized checks last, each with
 * its checks nested in the server's order. A header takes the fields a column
 * reads from its first check, so a column sort moves the group where its
 * first check would go; it renders only the category name.
 */
export function groupChecksByCategory(checks: Check[]): CheckListRow[] {
  const order: (CheckCategory | null)[] = [...CHECK_CATEGORIES, null];
  return order.flatMap((category) => {
    const groupChecks = checks.filter((check) => (check.category ?? null) === category);
    const first = groupChecks[0];
    if (!first) return [];
    return [{ ...first, id: `category:${category ?? "NONE"}`, groupChecks }];
  });
}

/**
 * Marks a check as saving until `save` settles, so its row selects stay
 * disabled for exactly that save. A mutation reports only its latest call,
 * which would release an earlier row while its save still runs, so every save
 * is tracked by its own check id.
 */
export function trackCheckSave(
  setSavingCheckIds: Dispatch<SetStateAction<ReadonlySet<string>>>,
  checkId: string,
  save: Promise<unknown>
): void {
  setSavingCheckIds((current) => new Set(current).add(checkId));
  void save
    .then(
      () => undefined,
      () => {
        // rule-10-no-op -- reason: the mutation's onError already toasts the
        // server's message; this handler only keeps the rejection from going
        // unhandled, so the row is released either way.
      }
    )
    .finally(() =>
      setSavingCheckIds((current) => {
        const next = new Set(current);
        next.delete(checkId);
        return next;
      })
    );
}

/**
 * The organization's checks, grouped by category under a header row each,
 * in the server's order (sort order, name) within a category. Every member may read them; only an OWNER or ADMIN of the default
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

  const rows = useMemo(() => groupChecksByCategory(checks), [checks]);
  const renderCategoryHeader = useCallback(
    (row: CheckListRow) => (
      <span className="kazan-grid-heading">{t(`categories.${row.category ?? "NONE"}`)}</span>
    ),
    [t]
  );

  const setOutcome = useSetCheckOutcome();
  const setStatus = useSetCheckStatus();
  const [savingCheckIds, setSavingCheckIds] = useState<ReadonlySet<string>>(() => new Set());
  const trackSave = useCallback(
    (checkId: string, save: Promise<unknown>) => trackCheckSave(setSavingCheckIds, checkId, save),
    []
  );

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
  const { mutateAsync: saveOutcome } = setOutcome;
  const { mutateAsync: saveStatus } = setStatus;
  const handleOutcomeChange = useCallback(
    (check: Check, outcome: CheckOutcome) =>
      trackSave(check.id, saveOutcome({ checkId: check.id, outcome })),
    [trackSave, saveOutcome]
  );
  const handleStatusChange = useCallback(
    (check: Check, nextStatus: CheckStatus) =>
      trackSave(check.id, saveStatus({ checkId: check.id, status: nextStatus })),
    [trackSave, saveStatus]
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
    savingCheckIds,
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
        <SenseyTable<CheckListRow>
          columns={columns}
          data={rows}
          hierarchyKey="groupChecks"
          isRowStatic={isCategoryHeader}
          renderStaticRow={renderCategoryHeader}
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
