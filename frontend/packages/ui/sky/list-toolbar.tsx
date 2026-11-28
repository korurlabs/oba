import * as React from "react";

import { cn } from "../src/lib/utils";

export type ListToolbarProps = {
  /**
   * The list's search, first on the left. The slot sets no width: a search
   * input takes `w-40 sm:w-56`, as on Kazan's lists.
   */
  search?: React.ReactNode;
  /**
   * The tabs of a page whose tabs switch to another list, after the search.
   * Tabs that only narrow the same list belong in the filter instead.
   */
  tabs?: React.ReactNode;
  /** Other controls on the right before the filter, such as a view toggle. */
  actions?: React.ReactNode;
  /** The filter on the right, usually a `FilterPopover`. */
  filters?: React.ReactNode;
  /** The create button, last on the right; left out on a read only list. */
  create?: React.ReactNode;
  className?: string;
};

/**
 * The one row above a list: the search first on the left, then the tabs,
 * then on the right the filter and the create button, in the order and
 * spacing of Kazan's list toolbars. The search leads on every list, so it
 * sits in the same place whether a page has tabs or not. On a narrow screen
 * the right group wraps below the search.
 */
export function ListToolbar({
  search,
  tabs,
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
      {search && <div className="min-w-0">{search}</div>}
      {tabs && <div className="min-w-0">{tabs}</div>}
      <div className="flex-1" aria-hidden />
      <div className="flex items-center gap-2">
        {actions}
        {filters}
        {create}
      </div>
    </div>
  );
}
