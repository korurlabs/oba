"use client";

import Link from "next/link";

import { Badge } from "@oba/ui/sky/badge";
import { FilterPopover } from "@oba/ui/sky/filter-popover";
import { ListToolbar } from "@oba/ui/sky/list-toolbar";
import { PageHeading } from "@oba/ui/sky/page-heading";
import { ListEmptyState, LoadError, TableLoading } from "@oba/ui/sky/page-states";
import { SearchInput } from "@oba/ui/sky/search-input";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@oba/ui/sky/table";
import {
  CLEAR_FILTERS_LABEL,
  CLEAR_SEARCH_LABEL,
  FILTER_LABELS,
  ListPagination,
} from "@/components/common/list-parts";
import { describeError } from "@/lib/api/error-text";
import { ageText } from "@/lib/format-time";
import { useNow } from "@/lib/use-now";

import { usePlanFilterParams, type PlanFilterParams } from "../hooks/use-plan-filter-params";
import { usePlanFilters, usePlans } from "../hooks/use-plans";
import { PLAN_LIMITS, type PlanListResponse } from "../schemas";
import {
  KIND_LABELS,
  REVIEW_BADGES,
  emptyPlansText,
  hasActiveFilter,
  referenceName,
  toPlanQuery,
} from "./plan-data";
import { NO_PLAN_FILTERS, planFilterCategories } from "./plan-filters";
import { PlanPack } from "./plan-pack";

export type PlanRowsProps = {
  plans: readonly PlanListResponse[];
  now: number;
};

/** The plans as table rows, in the order the API answers: newest change first. */
export function PlanRows({ plans, now }: PlanRowsProps) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Title</TableHead>
          <TableHead>Kind</TableHead>
          <TableHead>Repository</TableHead>
          <TableHead>Pack</TableHead>
          <TableHead>User</TableHead>
          <TableHead>Review</TableHead>
          <TableHead className="text-right">Versions</TableHead>
          <TableHead className="text-right">Open feedback</TableHead>
          <TableHead>Updated</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {plans.map((plan: PlanListResponse) => {
          const review = REVIEW_BADGES[plan.reviewState];
          return (
            <TableRow key={plan.id} className="align-top">
              <TableCell className="max-w-md font-medium break-words whitespace-normal">
                <Link href={`/plans/${encodeURIComponent(plan.id)}`} className="hover:underline">
                  {plan.title}
                </Link>
                {plan.path && (
                  <div className="text-muted-foreground font-mono text-xs break-all">
                    {plan.path}
                  </div>
                )}
              </TableCell>
              <TableCell className="whitespace-nowrap">{KIND_LABELS[plan.kind]}</TableCell>
              <TableCell className="whitespace-nowrap">{referenceName(plan.repository)}</TableCell>
              <TableCell className="whitespace-nowrap">
                <PlanPack pack={plan.pack} spec={plan.kind === "SPEC"} />
              </TableCell>
              <TableCell className="whitespace-nowrap">{referenceName(plan.user)}</TableCell>
              <TableCell>
                <Badge variant={review.variant}>{review.label}</Badge>
              </TableCell>
              <TableCell className="text-right tabular-nums">{plan.latestVersionNumber}</TableCell>
              <TableCell className="text-right tabular-nums">{plan.openFeedbackCount}</TableCell>
              <TableCell className="whitespace-nowrap">
                {ageText(plan.lastChangedAt, now)}
              </TableCell>
            </TableRow>
          );
        })}
      </TableBody>
    </Table>
  );
}

/**
 * The organization's plans: specs that sessions wrote and the plans of plan
 * mode, newest change first, opened on the plans that wait for review.
 */
export function PlansPage() {
  const [filters, setFilters] = usePlanFilterParams();
  const plans = usePlans(toPlanQuery(filters));
  const options = usePlanFilters();
  const now = useNow(60_000);
  const rows = plans.data?.data ?? [];
  const pagination = plans.data?.pagination ?? null;
  const optionsData = options.data?.data ?? undefined;
  const organizationHasPlans =
    optionsData === undefined ? undefined : optionsData.repositories.length > 0;

  const onChange = (change: Partial<PlanFilterParams>) => void setFilters({ ...change, page: 1 });

  return (
    <div className="flex flex-col gap-6">
      <PageHeading
        title="Plans"
        description="Specs and plan-mode plans written by sessions. Approve them, request changes or comment, and Kaya passes your feedback to the session."
      />
      <ListToolbar
        search={
          <SearchInput
            value={filters.search}
            onSearchChange={(search: string | undefined) =>
              // The API takes a search of at most this length.
              onChange({ search: search?.slice(0, PLAN_LIMITS.searchMax) ?? null })
            }
            label="Search plans"
            placeholder="Title or path"
            clearLabel={CLEAR_SEARCH_LABEL}
          />
        }
        filters={
          <FilterPopover
            categories={planFilterCategories(filters, onChange, optionsData)}
            onClear={() => onChange({ ...NO_PLAN_FILTERS, search: filters.search })}
            labels={FILTER_LABELS}
          />
        }
      />
      {options.error !== null && (
        <p className="text-warning-text text-sm" role="status">
          The filter options could not be loaded: {describeError(options.error)}
        </p>
      )}
      {plans.error !== null && plans.data === undefined ? (
        <LoadError
          title="Could not load the plans"
          message={describeError(plans.error)}
          onRetry={() => void plans.refetch()}
        />
      ) : plans.data === undefined ? (
        <TableLoading label="Loading the plans" />
      ) : rows.length === 0 ? (
        <ListEmptyState
          filtered={hasActiveFilter(filters) || filters.page > 1}
          onClearFilters={() => onChange(NO_PLAN_FILTERS)}
          labels={{
            emptyTitle: organizationHasPlans === false ? "No plans yet" : "Nothing to review",
            emptyDescription: emptyPlansText(filters, organizationHasPlans),
            filteredTitle: "No plan matches",
            filteredDescription: emptyPlansText(filters, organizationHasPlans),
            clearFilters: CLEAR_FILTERS_LABEL,
          }}
        />
      ) : (
        <PlanRows plans={rows} now={now} />
      )}
      <ListPagination
        pagination={pagination}
        noun="plans"
        onPageChange={(page: number) => void setFilters({ page })}
      />
    </div>
  );
}
