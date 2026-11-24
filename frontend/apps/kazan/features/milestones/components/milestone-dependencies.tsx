"use client";

import { useMemo } from "react";
import { useLocale, useTranslations } from "next-intl";
import { AlertTriangle, X } from "lucide-react";
import { SelectFieldStandalone } from "@oba/shared/features/form/components";
import { Button } from "@/components/ui/button";
import type { MilestoneDetailResponse } from "../schemas";
import {
  useAddMilestoneDependency,
  useRemoveMilestoneDependency,
} from "../hooks/use-milestones";
import { useMilestoneOptions } from "../hooks/use-milestone-options";
import { formatDateOnly } from "@oba/shared/lib/date-only";

interface MilestoneDependenciesProps {
  milestone: MilestoneDetailResponse;
  /** OWNER or ADMIN on the milestone's board; only they may change dependencies. */
  canManage: boolean;
}

/**
 * The milestones this one depends on. A conflict means this milestone starts
 * (or, without a start date, is due) before the dependency's target date; it
 * is a warning only and no date is moved.
 */
export function MilestoneDependencies({ milestone, canManage }: MilestoneDependenciesProps) {
  const t = useTranslations("milestone");
  const locale = useLocale();
  const addDependency = useAddMilestoneDependency();
  const removeDependency = useRemoveMilestoneDependency();
  const { options, isLoading } = useMilestoneOptions(milestone.boardId, { enabled: canManage });

  // A milestone cannot depend on itself, and an existing pair needs no second entry.
  const candidates = useMemo(() => {
    const taken = new Set([milestone.id, ...milestone.dependsOn.map((d) => d.milestoneId)]);
    return options.filter((option) => !taken.has(option.value));
  }, [options, milestone.id, milestone.dependsOn]);

  const isPending = addDependency.isPending || removeDependency.isPending;

  return (
    <section className="space-y-2">
      <h3 className="text-foreground border-border border-b pb-1 text-xs font-semibold">
        {t("dependsOn.title", { count: milestone.dependsOn.length })}
      </h3>

      {milestone.dependsOn.length === 0 ? (
        <p className="text-muted-foreground text-sm">{t("dependsOn.none")}</p>
      ) : (
        <ul className="divide-border divide-y">
          {milestone.dependsOn.map((dependency) => (
            <li key={dependency.milestoneId} className="flex items-center gap-2 py-2">
              <div className="min-w-0 flex-1">
                <div className="text-foreground truncate text-sm">{dependency.name}</div>
                <div className="text-muted-foreground text-xs">
                  {t("sheet.targetOnly", {
                    target: formatDateOnly(dependency.targetDate, locale),
                  })}
                </div>
              </div>
              {dependency.conflict && (
                <span
                  className="border-destructive/50 text-destructive inline-flex shrink-0 items-center gap-1 border px-1.5 py-0.5 text-xs"
                  title={t("dependsOn.conflictHint")}
                >
                  <AlertTriangle className="h-3 w-3" aria-hidden />
                  {t("dependsOn.conflict")}
                </span>
              )}
              {canManage && (
                <Button
                  size="icon"
                  variant="ghost"
                  className="h-7 w-7 shrink-0"
                  aria-label={t("dependsOn.remove", { name: dependency.name })}
                  disabled={isPending}
                  onClick={() =>
                    removeDependency.mutate({
                      boardId: milestone.boardId,
                      milestoneId: milestone.id,
                      dependsOnId: dependency.milestoneId,
                    })
                  }
                >
                  <X className="h-3.5 w-3.5" />
                </Button>
              )}
            </li>
          ))}
        </ul>
      )}

      {canManage && (
        <SelectFieldStandalone
          value=""
          onValueChange={(dependsOnId) => {
            if (!dependsOnId) return;
            addDependency.mutate({
              boardId: milestone.boardId,
              milestoneId: milestone.id,
              dependsOnId,
            });
          }}
          options={candidates}
          placeholder={t("dependsOn.add")}
          searchPlaceholder={t("dependsOn.search")}
          isLoading={isLoading}
          disabled={isPending}
        />
      )}
    </section>
  );
}
