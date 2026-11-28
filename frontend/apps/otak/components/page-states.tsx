import type { ReactNode } from "react";
import { useTranslations } from "next-intl";
import type { FilterPopoverLabels } from "@oba/ui/sky/filter-popover";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@oba/ui/sky/card";
import { cn } from "@/lib/utils";

/**
 * A list with nothing in it, and the one thing to do about it, such as
 * adding the first record or clearing the filters. It is the sky empty state
 * with an action below its text.
 */
export function ActionEmptyState({
  title,
  description,
  action,
}: {
  title: string;
  description: string;
  action: ReactNode;
}) {
  return (
    <Card className="items-center py-10 text-center">
      <CardHeader className="w-full justify-items-center">
        <CardTitle className="text-lg">{title}</CardTitle>
        <CardDescription>{description}</CardDescription>
      </CardHeader>
      <CardContent>{action}</CardContent>
    </Card>
  );
}

/**
 * A page heading as Kaya sets it: the title, a line on what the page holds,
 * and the page's main actions at the right.
 */
export function PageHeading({
  title,
  description,
  actions,
}: {
  title: string;
  description?: string;
  actions?: ReactNode;
}) {
  return (
    <div className="flex flex-wrap items-end justify-between gap-4">
      <div className="flex min-w-0 flex-col gap-1">
        <h1 className="break-words">{title}</h1>
        {description && <p className="text-muted-foreground">{description}</p>}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
    </div>
  );
}

/** The texts of every Otak list's filter popover, the same on each list. */
export function useFilterPopoverLabels(): FilterPopoverLabels {
  const t = useTranslations("filterPopover");
  return {
    trigger: t("trigger"),
    chooseCategory: t("chooseCategory"),
    searchOptions: t("searchOptions"),
    noOptions: t("noOptions"),
    loading: t("loading"),
    clear: t("clear"),
  };
}

/** A labelled control of a filter row, with its label above it in Kaya's small caption. */
export function FilterLabel({
  label,
  htmlFor,
  children,
  className,
}: {
  label: string;
  htmlFor?: string;
  children: ReactNode;
  className?: string;
}) {
  return (
    <div className={cn("flex min-w-0 flex-col gap-1 text-xs font-medium", className)}>
      <label htmlFor={htmlFor} className="text-muted-foreground">
        {label}
      </label>
      {children}
    </div>
  );
}
