import type { ReactNode } from "react";
import { AlertTriangle, X } from "lucide-react";

import { Button } from "./button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "./card";

/** A list's outline while its first answer loads. */
export function TableLoading({ label }: { label: string }) {
  return (
    <div className="flex flex-col gap-2" aria-busy="true" aria-label={label}>
      {Array.from({ length: 4 }, (_, index: number) => (
        <div key={index} className="bg-muted h-12 animate-pulse rounded-md" />
      ))}
    </div>
  );
}

/** A list or page whose data failed to load, with a way to ask again. */
export function LoadError({
  title,
  message,
  onRetry,
  retryLabel = "Try again",
}: {
  title: string;
  message: string;
  onRetry: () => void;
  retryLabel?: string;
}) {
  return (
    <Card role="alert" className="border-error/40 bg-error-light gap-4">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-lg">
          <AlertTriangle className="text-error size-5" aria-hidden />
          {title}
        </CardTitle>
        <CardDescription className="text-foreground">{message}</CardDescription>
      </CardHeader>
      <CardContent>
        <Button variant="outline" onClick={onRetry}>
          {retryLabel}
        </Button>
      </CardContent>
    </Card>
  );
}

/**
 * A list with nothing in it, and when given, the one thing to do about it,
 * such as adding the first record or clearing the filters, below the text.
 */
export function EmptyState({
  title,
  description,
  action,
}: {
  title: string;
  description: string;
  action?: ReactNode;
}) {
  return (
    <Card className="items-center py-10 text-center">
      <CardHeader className="w-full justify-items-center">
        <CardTitle className="text-lg">{title}</CardTitle>
        <CardDescription>{description}</CardDescription>
      </CardHeader>
      {action && <CardContent>{action}</CardContent>}
    </Card>
  );
}

/** The texts of an empty list, translated by the app. */
export type ListEmptyStateLabels = {
  /** Shown when the list holds nothing at all, such as "No campaigns yet". */
  emptyTitle: string;
  emptyDescription: string;
  /** Shown when the search or the filters hide every record. */
  filteredTitle: string;
  filteredDescription: string;
  /** The button that clears the search and the filters, such as "Clear filters". */
  clearFilters: string;
};

/**
 * A list that shows no row, in one of its two cases. When the search or the
 * filters hide every record, the way out is clearing them, so the create
 * button is left out there: it would suggest the record does not exist.
 * When the list is really empty, the way out is the create button, left out
 * on a read only list.
 */
export function ListEmptyState({
  filtered,
  onClearFilters,
  create,
  labels,
}: {
  /** Whether a search or a filter is set. */
  filtered: boolean;
  /** Clears the search and every filter; the app resets its paging here too. */
  onClearFilters: () => void;
  /** The list's create button, usually the same element as the toolbar's. */
  create?: ReactNode;
  labels: ListEmptyStateLabels;
}) {
  if (filtered) {
    return (
      <EmptyState
        title={labels.filteredTitle}
        description={labels.filteredDescription}
        action={
          <Button variant="outline" onClick={onClearFilters}>
            <X aria-hidden />
            {labels.clearFilters}
          </Button>
        }
      />
    );
  }
  return (
    <EmptyState title={labels.emptyTitle} description={labels.emptyDescription} action={create} />
  );
}
