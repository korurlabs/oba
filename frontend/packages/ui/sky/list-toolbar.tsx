import * as React from "react";

import { cn } from "../src/lib/utils";

export type ListToolbarProps = {
  /** The tabs of a page that has them, first on the left. */
  tabs?: React.ReactNode;
  /**
   * The list's search, on the left. The slot sets no width: a search input
   * takes `w-40 sm:w-56`, as on Kazan's lists.
   */
  search?: React.ReactNode;
  /** Other controls on the right before the filter, such as a view toggle. */
  actions?: React.ReactNode;
  /** The filter on the right, usually a `FilterPopover`. */
  filters?: React.ReactNode;
  /** The create button, last on the right; left out on a read only list. */
  create?: React.ReactNode;
  className?: string;
};

/**
 * The one row above a list: the search on the left, then on the right the
 * filter and the create button, in the order and spacing of Kazan's list
 * toolbars. On a narrow screen the right group wraps below the search.
 */
export function ListToolbar({
  tabs,
  search,
  actions,
  filters,
  create,
  className,
}: ListToolbarProps) {
  return (
    <div
      data-slot="list-toolbar"
      className={cn("flex min-h-9 flex-wrap items-center gap-2 lg:gap-3", className)}
    >
      {tabs && <div className="min-w-0">{tabs}</div>}
      {search && <div className="min-w-0">{search}</div>}
      <div className="flex-1" aria-hidden />
      <div className="flex items-center gap-2">
        {actions}
        {filters}
        {create}
      </div>
    </div>
  );
}
