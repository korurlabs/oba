"use client";

import type { ComponentProps, ReactNode } from "react";
import { Plus } from "lucide-react";

import { Button } from "@oba/ui/sky/button";
import { CollapsibleSearch, type CollapsibleSearchProps } from "@oba/ui/sky/collapsible-search";
import {
  FilterPopover,
  type FilterCategory,
  type FilterPopoverLabels,
} from "@oba/ui/sky/filter-popover";
import { ListToolbar as SkyListToolbar } from "@oba/ui/sky/list-toolbar";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@oba/ui/sky/tooltip";
import { ChangeLockTip } from "@/features/versions/components/open-change";

/**
 * "Show inherited" of a pack's list, kept in the URL as `inherited`. A
 * repository's release has no parent packs, so its scope has no filter.
 */
export type InheritedFilter = {
  shown: boolean;
  onChange: (shown: boolean) => void;
};

/** The one option of the inherited category; chosen means parent items are listed too. */
export const INHERITED_SHOWN = "shown";

const FILTER_LABELS: FilterPopoverLabels = {
  trigger: "Filter",
  chooseCategory: "Select a field",
  searchOptions: "Search options",
  noOptions: "No options",
  loading: "Loading",
  clear: "Clear all",
};

/**
 * The inherited filter as a single select category: a pack inherits what its
 * parent packs deliver, so ticking it also lists the parent items, each with
 * its source pack, and clearing it lists only what this pack owns.
 */
export function inheritedCategory(filter: InheritedFilter): FilterCategory {
  return {
    key: "inherited",
    label: "Inherited",
    mode: "single",
    options: [{ value: INHERITED_SHOWN, label: "Show inherited", hint: "from parent packs" }],
    value: filter.shown ? INHERITED_SHOWN : null,
    onChange: (value: string | null) => filter.onChange(value === INHERITED_SHOWN),
  };
}

export type NewButtonProps = Omit<ComponentProps<typeof Button>, "children"> & {
  /** The button's text without the plus, such as "New skill". */
  label: string;
  /**
   * The pack of the member's open change when it is another pack than the
   * one this button adds to; the button is then disabled with the reason
   * beside it. Null or left out when creating is allowed.
   */
  lockedBy?: string | null;
  /** What the button creates, shown in its tooltip. */
  help?: ReactNode;
};

/**
 * The create action of a list, a filled Korur-blue button. It forwards every
 * other prop to the button, so it can be the child of a `DialogTrigger
 * asChild`, except while locked: then it renders the disabled button and
 * its lock tip, and is not wrapped in a trigger.
 */
export function NewButton({ label, lockedBy = null, help, ...props }: NewButtonProps) {
  if (lockedBy !== null) {
    return (
      <div className="flex items-center gap-1">
        <Button type="button" disabled>
          <Plus className="size-4" aria-hidden />
          {label}
        </Button>
        <ChangeLockTip packName={lockedBy} />
      </div>
    );
  }
  const button = (
    <Button type="button" {...props}>
      <Plus className="size-4" aria-hidden />
      {label}
    </Button>
  );
  if (help === undefined) return button;
  return (
    <TooltipProvider delayDuration={0}>
      <Tooltip>
        <TooltipTrigger asChild>{button}</TooltipTrigger>
        <TooltipContent className="max-w-xs">{help}</TooltipContent>
      </Tooltip>
    </TooltipProvider>
  );
}

export type ListToolbarProps = {
  /** The tabs of a page that has them, first on the left. */
  tabs?: ReactNode;
  /** The inherited filter; null or left out hides the Filter button, as on a repository. */
  filter?: InheritedFilter | null;
  /** The list's search, beside the tabs; left out when the list has none. */
  search?: CollapsibleSearchProps;
  /** Other actions before the filter, such as the Allowlist's Edit. */
  actions?: ReactNode;
  /** The create button, usually a `NewButton`; null or left out on a read only scope. */
  create?: ReactNode;
};

/**
 * The one row above a config list, laid out as every sky list: its tabs and
 * search on the left; its actions, the Filter button and the create button
 * on the right.
 */
export function ListToolbar({ tabs, filter, search, actions, create }: ListToolbarProps) {
  return (
    <SkyListToolbar
      tabs={tabs}
      search={search && <CollapsibleSearch {...search} />}
      actions={actions}
      filters={
        filter && (
          <FilterPopover
            categories={[inheritedCategory(filter)]}
            onClear={() => filter.onChange(false)}
            labels={FILTER_LABELS}
          />
        )
      }
      create={create}
    />
  );
}
