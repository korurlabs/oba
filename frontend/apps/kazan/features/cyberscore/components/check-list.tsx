"use client";

import {
  useCallback,
  useId,
  useMemo,
  useState,
  type Dispatch,
  type ReactNode,
  type SetStateAction,
} from "react";
import { useTranslations } from "next-intl";
import { ArrowLeft, Info, ListChecks, Plus } from "lucide-react";
import { SenseySearchInput } from "@oba/shared/components/sensey-search-input";
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
import {
  UNCATEGORIZED,
  useCheckFilterParams,
  type CheckCategoryFilter,
} from "../hooks/use-check-filter-params";
import {
  CHECK_CATEGORIES,
  CHECK_STATUSES,
  CheckCategorySchema,
  CheckStatusSchema,
  type Check,
  type CheckCategory,
  type CheckOutcome,
  type CheckStatus,
} from "../schemas";
import { CheckCard, CheckCardSkeleton } from "./check-card";
import { CheckDeleteDialog } from "./check-delete-dialog";
import { CheckDialog } from "./check-dialog";
import { CyberscoreLoadError } from "./cyberscore-load-error";

/** The filters' value for "no narrowing"; a Select item cannot have an empty value. */
const ALL = "ALL";

/**
 * The checks of one category (`NONE` for those without one) whose name holds
 * the search text, ignoring case; a missing filter keeps every check.
 */
export function filterChecks(
  checks: Check[],
  { category, search }: { category?: CheckCategoryFilter | null; search?: string | null }
): Check[] {
  const needle = search?.trim().toLocaleLowerCase() ?? "";
  return checks.filter(
    (check) =>
      (!category || (check.category ?? UNCATEGORIZED) === category) &&
      (needle === "" || check.name.toLocaleLowerCase().includes(needle))
  );
}

/** The checks of one category, or of no category when `category` is null. */
export interface CheckGroup {
  category: CheckCategory | null;
  checks: Check[];
}

/**
 * One group per category that has checks, in the category's declared order
 * and with uncategorized checks last, each keeping the server's order.
 */
export function groupChecksByCategory(checks: Check[]): CheckGroup[] {
  const order: (CheckCategory | null)[] = [...CHECK_CATEGORIES, null];
  return order.flatMap((category) => {
    const groupChecks = checks.filter((check) => (check.category ?? null) === category);
    return groupChecks.length > 0 ? [{ category, checks: groupChecks }] : [];
  });
}

/**
 * Marks a check as saving until `save` settles, so its card's selects stay
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

interface CheckGroupsProps {
  groups: CheckGroup[];
  isLoading: boolean;
  emptyMessage: ReactNode;
  renderCheck: (check: Check) => ReactNode;
}

/** One section per category: its heading, then a card per check. */
function CheckGroups({ groups, isLoading, emptyMessage, renderCheck }: CheckGroupsProps) {
  if (isLoading) {
    return (
      <div className="flex flex-col gap-3" aria-busy="true">
        <CheckCardSkeleton />
        <CheckCardSkeleton />
        <CheckCardSkeleton />
      </div>
    );
  }
  if (groups.length === 0) return <>{emptyMessage}</>;
  return (
    <div className="flex flex-col gap-8">
      {groups.map((group) => (
        <CheckGroupSection key={group.category ?? "NONE"} group={group} renderCheck={renderCheck} />
      ))}
    </div>
  );
}

function CheckGroupSection({
  group,
  renderCheck,
}: {
  group: CheckGroup;
  renderCheck: (check: Check) => ReactNode;
}) {
  const t = useTranslations("cyberscore");
  const headingId = useId();
  return (
    <section aria-labelledby={headingId} className="flex min-w-0 flex-col gap-3">
      <h2 id={headingId} className="kazan-grid-heading">
        {t(`categories.${group.category ?? "NONE"}`)}
      </h2>
      <div className="flex flex-col gap-3">{group.checks.map(renderCheck)}</div>
    </section>
  );
}

/**
 * The organization's checks as one card each, grouped by category under a
 * heading each, in the server's order (sort order, name) within a category.
 * Every member may read them; only an OWNER or ADMIN of the default
 * organization gets the add, edit, delete and outcome and status controls,
 * which the server enforces as well.
 */
export function CheckList() {
  const t = useTranslations("cyberscore");
  const tErrors = useTranslations("errors");
  const { data: meResponse } = useGetMe();
  const me = meResponse?.data;
  const canManage = isOrganizationAdmin(me, me?.defaultOrganizationId);

  const [filters, setFilters] = useCheckFilterParams();
  const status = filters.status ?? undefined;
  const { category, search } = filters;
  const isNarrowed = Boolean(status || category || search);
  const { data: checksResponse, isPending, isError, isFetching, refetch } = useChecks(status);
  const checks = useMemo((): Check[] => checksResponse?.data ?? [], [checksResponse?.data]);

  const groups = useMemo(
    () => groupChecksByCategory(filterChecks(checks, { category, search })),
    [checks, category, search]
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

  const handleStatusFilterChange = (value: string) => {
    const parsed = CheckStatusSchema.safeParse(value);
    void setFilters({ status: parsed.success ? parsed.data : null });
  };
  const handleCategoryFilterChange = (value: string) => {
    const parsed = CheckCategorySchema.safeParse(value);
    void setFilters({
      category: parsed.success ? parsed.data : value === UNCATEGORIZED ? UNCATEGORIZED : null,
    });
  };
  const handleSearchChange = (value: string | undefined) => {
    void setFilters({ search: value ?? null });
  };
  // Bumped to empty the search box at once; its typed text is held inside it
  // until the debounce runs, so a cleared URL value alone would not reset it.
  const [searchResetSignal, setSearchResetSignal] = useState(0);
  const handleClearFilters = () => {
    setSearchResetSignal((current) => current + 1);
    void setFilters({ status: null, category: null, search: null });
  };

  return (
    <div className="mx-auto flex w-full max-w-6xl flex-col gap-6 px-4 py-6 sm:px-6 sm:py-8">
      <Button variant="ghost" size="sm" className="-ml-2 gap-1.5 self-start" asChild>
        <Link href="/cyberscore">
          <ArrowLeft className="h-4 w-4" aria-hidden="true" />
          {t("checks.back")}
        </Link>
      </Button>

      <PageHeader title={t("checks.title")} description={t("checks.description")}>
        <div className="ml-auto flex flex-wrap items-center justify-end gap-2">
          <SenseySearchInput
            value={search}
            onSearchChange={handleSearchChange}
            placeholder={t("checks.filter.search")}
            resetSignal={searchResetSignal}
            className="w-full sm:w-52"
          />
          <Select value={category ?? ALL} onValueChange={handleCategoryFilterChange}>
            <SelectTrigger size="sm" className="h-9 w-52" aria-label={t("checks.filter.category")}>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL}>{t("checks.filter.allCategories")}</SelectItem>
              {[...CHECK_CATEGORIES, UNCATEGORIZED].map((value) => (
                <SelectItem key={value} value={value}>
                  {t(`categories.${value}`)}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Select value={status ?? ALL} onValueChange={handleStatusFilterChange}>
            <SelectTrigger size="sm" className="h-9 w-44" aria-label={t("checks.filter.status")}>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL}>{t("checks.filter.allStatuses")}</SelectItem>
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
        <CheckGroups
          groups={groups}
          isLoading={isPending}
          emptyMessage={
            <div className="border-border flex flex-col items-center gap-2 border border-dashed px-4 py-12 text-center">
              <span
                aria-hidden
                className="border-primary/40 text-primary mb-2 flex size-12 items-center justify-center border border-dashed"
              >
                <ListChecks className="size-6" />
              </span>
              <span className="text-foreground text-sm font-semibold">
                {isNarrowed ? t("checks.emptyFiltered") : t("checks.empty")}
              </span>
              {isNarrowed ? (
                <Button variant="outline" size="sm" className="mt-2" onClick={handleClearFilters}>
                  {t("checks.filter.clear")}
                </Button>
              ) : (
                canManage && (
                  <span className="text-muted-foreground text-sm">
                    {t("checks.emptyDescription")}
                  </span>
                )
              )}
            </div>
          }
          renderCheck={(check) => (
            <CheckCard
              key={check.id}
              check={check}
              canManage={canManage}
              isSaving={savingCheckIds.has(check.id)}
              onEdit={handleEdit}
              onDelete={handleDelete}
              onOutcomeChange={handleOutcomeChange}
              onStatusChange={handleStatusChange}
            />
          )}
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
