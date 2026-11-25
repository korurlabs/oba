"use client";

import { useCallback, useMemo } from "react";
import { useTranslations } from "next-intl";
import { SenseyTable } from "@oba/shared/components/sensey-table";
import { useCheckColumns, type CheckTableRow } from "../hooks/use-check-columns";
import type { Check, CheckOutcome, CheckStatus } from "../schemas";
import type { CheckGroup } from "./check-list";

const HEADER_ID_PREFIX = "category:";

/**
 * One heading row per category, holding that category's checks as its child
 * rows. The heading copies its first check's fields, so a column sort orders
 * the categories by the same value as their checks and never scatters a
 * heading away from its checks.
 */
export function toCheckTableRows(groups: CheckGroup[]): CheckTableRow[] {
  return groups.flatMap(({ category, checks }) => {
    const [first] = checks;
    if (!first) return [];
    return [{ ...first, id: `${HEADER_ID_PREFIX}${category ?? "NONE"}`, groupChecks: checks }];
  });
}

function isCategoryHeading(row: CheckTableRow): boolean {
  return row.groupChecks !== undefined;
}

interface CheckTableProps {
  groups: CheckGroup[];
  isLoading: boolean;
  canManage: boolean;
  savingCheckIds: ReadonlySet<string>;
  onEdit: (check: Check) => void;
  onDelete: (check: Check) => void;
  onOutcomeChange: (check: Check, outcome: CheckOutcome) => void;
  onStatusChange: (check: Check, status: CheckStatus) => void;
}

/**
 * The checks as one long table, a row per check under a heading row per
 * category, in the order of the cards view. The page scrolls rather than the
 * table, so every row is in the document and the browser's find works.
 */
export function CheckTable({
  groups,
  isLoading,
  canManage,
  savingCheckIds,
  onEdit,
  onDelete,
  onOutcomeChange,
  onStatusChange,
}: CheckTableProps) {
  const t = useTranslations("cyberscore");
  const columns = useCheckColumns({
    canManage,
    onEdit,
    onDelete,
    onOutcomeChange,
    onStatusChange,
    savingCheckIds,
  });
  const rows = useMemo(() => toCheckTableRows(groups), [groups]);
  const renderCategoryHeading = useCallback(
    (row: CheckTableRow) => (
      <span className="kazan-grid-heading">
        {t(`categories.${row.groupChecks?.[0]?.category ?? "NONE"}`)}
      </span>
    ),
    [t]
  );

  return (
    <SenseyTable<CheckTableRow>
      columns={columns}
      data={rows}
      hierarchyKey="groupChecks"
      isRowStatic={isCategoryHeading}
      renderStaticRow={renderCategoryHeading}
      isLoading={isLoading}
      virtualized={false}
      getRowId={(row) => row.id}
    />
  );
}
