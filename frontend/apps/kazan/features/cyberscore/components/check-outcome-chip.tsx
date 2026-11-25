"use client";

import { useTranslations } from "next-intl";
import { cn } from "@/lib/utils";
import { CheckOutcome as CheckOutcomeValue, type CheckOutcome } from "../schemas";

/**
 * A failed check is the one value a reader must not miss, so its chip takes
 * the red outline Kazan gives a bug; every other outcome and status stays a
 * calm neutral chip.
 */
export const FAIL_CHIP_CLASS =
  "border-red-600/40 text-red-700 dark:border-red-400/50 dark:text-red-400";

/** A check's outcome as a read-only chip. */
export function CheckOutcomeChip({
  outcome,
  className,
}: {
  outcome: CheckOutcome;
  className?: string;
}) {
  const t = useTranslations("cyberscore");
  return (
    <span
      className={cn(
        "kazan-grid-chip",
        outcome === CheckOutcomeValue.FAIL && FAIL_CHIP_CLASS,
        className
      )}
    >
      {t(`outcomes.${outcome}`)}
    </span>
  );
}
