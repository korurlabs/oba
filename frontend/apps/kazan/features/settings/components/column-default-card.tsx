"use client";

import { useEffect, useState } from "react";
import { useTranslations } from "next-intl";
import type { VisibilityState } from "@tanstack/react-table";
import { Columns3, Loader2, RotateCcw, Save, Undo2 } from "lucide-react";
import { ColumnVisibilityContent } from "@oba/shared/components/sensey-table";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { useColumnDefault } from "@/hooks/use-board-preferences";
import { useTicketColumnToggleItems } from "@/features/tickets/hooks/use-ticket-column-toggle-items";

/** Two visibility maps match when they hide the same columns. */
function hiddenColumnIds(visibility: VisibilityState): string[] {
  return Object.keys(visibility)
    .filter((id) => visibility[id] === false)
    .sort();
}

function sameLayout(
  a: { visibility: VisibilityState; order: string[] },
  b: { visibility: VisibilityState; order: string[] },
): boolean {
  const hiddenA = hiddenColumnIds(a.visibility);
  const hiddenB = hiddenColumnIds(b.visibility);
  return (
    hiddenA.length === hiddenB.length &&
    hiddenA.every((id, i) => id === hiddenB[i]) &&
    a.order.length === b.order.length &&
    a.order.every((id, i) => id === b.order[i])
  );
}

/**
 * Edits the user's default column layout: the layout of every ticket table
 * that has none of its own. Edits stay local until Save; Reset deletes the
 * default, so those tables fall back to the built-in layout.
 */
export function ColumnDefaultCard() {
  const t = useTranslations();
  const columns = useTicketColumnToggleItems();
  const {
    columnDefault,
    hasColumnDefault,
    saveColumnDefault,
    clearColumnDefault,
    isSaving,
    isMeFetched,
  } = useColumnDefault();

  const savedVisibility = columnDefault?.columnVisibility ?? {};
  const savedOrder = columnDefault?.columnOrder ?? [];

  const [columnVisibility, setColumnVisibility] = useState<VisibilityState>({});
  const [columnOrder, setColumnOrder] = useState<string[]>([]);

  // Follow the stored default whenever it loads or changes. `columnDefault`
  // keeps its identity across saves of unrelated preferences, so this does
  // not discard local edits when, say, the theme is saved.
  useEffect(() => {
    if (!isMeFetched) return;
    setColumnVisibility(columnDefault?.columnVisibility ?? {});
    setColumnOrder(columnDefault?.columnOrder ?? []);
  }, [isMeFetched, columnDefault]);

  const isDirty = !sameLayout(
    { visibility: columnVisibility, order: columnOrder },
    { visibility: savedVisibility, order: savedOrder },
  );

  const discardChanges = () => {
    setColumnVisibility(savedVisibility);
    setColumnOrder(savedOrder);
  };

  return (
    <div className="bg-card border-border rounded-2xl border p-6">
      <div className="mb-4 flex items-center gap-4">
        <div className="bg-primary/15 flex h-10 w-10 items-center justify-center rounded-xl">
          <Columns3 className="text-primary h-5 w-5" />
        </div>
        <div>
          <h3 className="text-foreground text-lg font-semibold">
            {t("columnSettings.cardTitle")}
          </h3>
          <p className="text-muted-foreground text-sm">{t("columnSettings.cardSubtitle")}</p>
        </div>
      </div>

      {isMeFetched ? (
        <>
          <p className="text-muted-foreground mb-3 text-sm" aria-live="polite">
            {hasColumnDefault
              ? t("columnSettings.usingDefault")
              : t("columnSettings.usingBuiltIn")}
          </p>
          <div className="border-border max-w-sm rounded-xl border p-3">
            <ColumnVisibilityContent
              columns={columns}
              columnVisibility={columnVisibility}
              onColumnVisibilityChange={setColumnVisibility}
              columnOrder={columnOrder}
              onColumnOrderChange={setColumnOrder}
            />
          </div>
          <div className="mt-4 flex flex-wrap gap-2">
            <Button
              onClick={() => saveColumnDefault(columnVisibility, columnOrder)}
              disabled={!isDirty || isSaving}
            >
              {isSaving ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : (
                <Save className="h-4 w-4" />
              )}
              {t("columnSettings.save")}
            </Button>
            {isDirty && (
              <Button variant="ghost" onClick={discardChanges} disabled={isSaving}>
                <Undo2 className="h-4 w-4" />
                {t("columnSettings.discard")}
              </Button>
            )}
            {hasColumnDefault && (
              <Button variant="outline" onClick={clearColumnDefault} disabled={isSaving}>
                <RotateCcw className="h-4 w-4" />
                {t("columnSettings.reset")}
              </Button>
            )}
          </div>
        </>
      ) : (
        <Skeleton className="h-64 max-w-sm rounded-xl" />
      )}
    </div>
  );
}
