/**
 * DueDateBadge Component
 *
 * Displays a due date with color-coded urgency:
 * - Red: overdue (past due date)
 * - Amber/orange: due soon (within 2 days)
 * - Muted: normal (more than 2 days away)
 *
 * Used in table columns, ticket cards, and detail views.
 */

"use client";

import { useMemo } from "react";
import { format, isPast, differenceInDays, parseISO, startOfDay } from "date-fns";
import { enUS, nl, tr } from "date-fns/locale";
import { useLocale, useTranslations } from "next-intl";
import { CalendarClock, AlertTriangle } from "lucide-react";
import { cn } from "@oba/ui/lib/utils";

interface DueDateBadgeProps {
  dueDate?: string | null;
  /** Show icon alongside the date text. Defaults to true. */
  showIcon?: boolean;
  /** Additional CSS classes */
  className?: string;
}

function getDateLocale(locale: string) {
  switch (locale) {
    case "nl":
      return nl;
    case "tr":
      return tr;
    default:
      return enUS;
  }
}

type DueDateUrgency = "overdue" | "due-soon" | "normal";

function getDueDateUrgency(dueDate: Date): DueDateUrgency {
  const today = startOfDay(new Date());
  const due = startOfDay(dueDate);

  if (isPast(due) && due < today) {
    return "overdue";
  }

  const daysUntilDue = differenceInDays(due, today);
  if (daysUntilDue <= 2) {
    return "due-soon";
  }

  return "normal";
}

export function DueDateBadge({ dueDate, showIcon = true, className }: DueDateBadgeProps) {
  const locale = useLocale();
  const t = useTranslations("ticket.fields");

  const { formattedDate, urgency } = useMemo(() => {
    if (!dueDate) return { formattedDate: null, urgency: "normal" as DueDateUrgency };

    const date = parseISO(dueDate);
    const dateLocale = getDateLocale(locale);
    const formatted = format(date, "MMM d, yyyy", { locale: dateLocale });
    const urg = getDueDateUrgency(date);

    return { formattedDate: formatted, urgency: urg };
  }, [dueDate, locale]);

  if (!formattedDate) return null;

  const urgencyStyles: Record<DueDateUrgency, string> = {
    // Red is kept for a date already missed; a near date is told in the primary ink.
    overdue: "text-red-600 dark:text-red-400",
    "due-soon": "text-korur-blue-strong font-medium dark:text-korur-blue",
    normal: "text-muted-foreground",
  };

  const Icon = urgency === "overdue" ? AlertTriangle : CalendarClock;

  return (
    <div
      className={cn(
        "flex items-center gap-1 whitespace-nowrap text-xs",
        urgencyStyles[urgency],
        className,
      )}
      title={
        urgency === "overdue"
          ? t("overdue")
          : urgency === "due-soon"
            ? t("dueSoon")
            : undefined
      }
    >
      {showIcon && <Icon className="h-3.5 w-3.5 shrink-0" />}
      <span>{formattedDate}</span>
    </div>
  );
}
