"use client";

import type { ReactNode } from "react";
import { useTranslations } from "next-intl";
import { Pencil, Trash2 } from "lucide-react";
import { useDateFormat } from "@oba/shared/i18n/use-date-format";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import {
  CheckOutcome as CheckOutcomeValue,
  type Check,
  type CheckOutcome,
  type CheckStatus,
} from "../schemas";
import { FAIL_CHIP_CLASS } from "./check-outcome-chip";
import { CheckOutcomeSelect, CheckStatusSelect } from "./check-row-selects";

interface CheckCardProps {
  check: Check;
  canManage: boolean;
  /** True while this check's outcome or status is being saved; its selects wait for the answer. */
  isSaving: boolean;
  onEdit: (check: Check) => void;
  onDelete: (check: Check) => void;
  onOutcomeChange: (check: Check, outcome: CheckOutcome) => void;
  onStatusChange: (check: Check, status: CheckStatus) => void;
}

/** One labelled value in the card's meta row. */
function CheckField({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex min-w-0 flex-col gap-1.5">
      <span className="kazan-grid-label flex items-center gap-1.5">{label}</span>
      <div className="flex min-h-8 items-center">{children}</div>
    </div>
  );
}

/**
 * A block of check text. The text keeps its own line breaks, so numbered fix
 * steps stay one per line, and long words wrap rather than widen the card.
 */
function CheckText({ label, text }: { label: string; text: string }) {
  return (
    <div className="flex min-w-0 flex-col gap-1.5">
      <span className="kazan-grid-label flex items-center gap-1.5">{label}</span>
      <p className="text-foreground/85 text-sm leading-relaxed break-words whitespace-pre-line">
        {text}
      </p>
    </div>
  );
}

/**
 * One check as a work card, with the surface and blue hover edge of Kazan's
 * ticket card. Everything about the check is readable on the card itself:
 * the text wraps in full and the meta row wraps on a narrow screen. Outcome
 * and status are selects for an organization admin and read-only chips for
 * every other reader, who may not change them.
 */
export function CheckCard({
  check,
  canManage,
  isSaving,
  onEdit,
  onDelete,
  onOutcomeChange,
  onStatusChange,
}: CheckCardProps) {
  const t = useTranslations("cyberscore");
  const dates = useDateFormat();

  return (
    <article
      className={cn(
        "kazan-card border-border relative flex min-w-0 flex-col gap-5 rounded-none border p-5 sm:p-6",
        "transition-[border-color,box-shadow,opacity] duration-150 ease-in-out",
        "hover:border-primary/50"
      )}
    >
      <header className="flex items-start justify-between gap-3">
        <h3 className="text-foreground min-w-0 text-base leading-snug font-semibold break-words">
          {check.name}
        </h3>
        {canManage && (
          <div className="-mt-1 -mr-2 flex shrink-0 items-center gap-1">
            <Button
              variant="ghost"
              size="icon-sm"
              aria-label={`${t("checks.edit")}: ${check.name}`}
              title={t("checks.edit")}
              onClick={() => onEdit(check)}
            >
              <Pencil className="h-4 w-4" />
            </Button>
            <Button
              variant="ghost"
              size="icon-sm"
              aria-label={`${t("checks.delete")}: ${check.name}`}
              title={t("checks.delete")}
              className="text-destructive hover:text-destructive hover:[&_svg]:text-destructive"
              onClick={() => onDelete(check)}
            >
              <Trash2 className="h-4 w-4" />
            </Button>
          </div>
        )}
      </header>

      <div className="flex flex-wrap items-start gap-x-8 gap-y-4">
        <CheckField label={t("checks.columns.weight")}>
          <span className="kazan-grid-number h-6 w-auto min-w-6 px-1.5">{check.weight}</span>
        </CheckField>
        <CheckField label={t("checks.columns.outcome")}>
          {canManage ? (
            <CheckOutcomeSelect
              value={check.outcome}
              checkName={check.name}
              disabled={isSaving}
              onChange={(outcome) => onOutcomeChange(check, outcome)}
            />
          ) : (
            <span
              className={cn(
                "kazan-grid-chip",
                check.outcome === CheckOutcomeValue.FAIL && FAIL_CHIP_CLASS
              )}
            >
              {t(`outcomes.${check.outcome}`)}
            </span>
          )}
        </CheckField>
        <CheckField label={t("checks.columns.status")}>
          {canManage ? (
            <CheckStatusSelect
              value={check.status}
              checkName={check.name}
              disabled={isSaving}
              onChange={(status) => onStatusChange(check, status)}
            />
          ) : (
            <span className="kazan-grid-chip">{t(`statuses.${check.status}`)}</span>
          )}
        </CheckField>
        <CheckField label={t("checks.columns.updatedAt")}>
          <span className="text-muted-foreground font-mono text-xs whitespace-nowrap tabular-nums">
            {dates.formatDate(check.updatedAt)}
          </span>
        </CheckField>
      </div>

      <div className="kazan-grid-divider flex flex-col gap-5 pt-5">
        {check.description && (
          <CheckText label={t("checks.fields.description")} text={check.description} />
        )}
        <CheckText label={t("checks.fields.expected")} text={check.expected} />
      </div>
    </article>
  );
}

/** A placeholder card in the shape of a check card while the list loads. */
export function CheckCardSkeleton() {
  return (
    <div className="border-border bg-card dark:bg-muted animate-pulse rounded-none border p-5 sm:p-6">
      <div className="bg-muted mb-5 h-5 w-2/3" />
      <div className="mb-5 flex flex-wrap gap-8">
        <div className="bg-muted h-12 w-16" />
        <div className="bg-muted h-12 w-36" />
        <div className="bg-muted h-12 w-36" />
      </div>
      <div className="space-y-2">
        <div className="bg-muted h-4 w-full" />
        <div className="bg-muted h-4 w-5/6" />
        <div className="bg-muted h-4 w-3/4" />
      </div>
    </div>
  );
}
