"use client";

import type { ComponentProps, ReactNode } from "react";
import { ListFilter, Plus } from "lucide-react";

import { Button } from "@oba/ui/sky/button";
import { CollapsibleSearch, type CollapsibleSearchProps } from "@oba/ui/sky/collapsible-search";
import { InfoTip } from "@oba/ui/sky/info-tip";
import { Popover, PopoverContent, PopoverTrigger } from "@oba/ui/sky/popover";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@oba/ui/sky/tooltip";
import { ChangeLockTip } from "@/features/versions/components/open-change";
import { cn } from "@/lib/utils";

/**
 * "Show inherited" of a pack's list, kept in the URL as `inherited`. A
 * repository's release has no parent packs, so its scope has no filter.
 */
export type InheritedFilter = {
  shown: boolean;
  onChange: (shown: boolean) => void;
};

const INHERITED_HELP =
  "A pack inherits what its parent packs deliver. Unchecked lists only what this pack owns; checked also lists the parent items, each with its source pack.";

/** The filter icon and its menu; the icon is highlighted while inherited items are listed. */
function FilterMenu({ filter }: { filter: InheritedFilter }) {
  return (
    <Popover>
      <PopoverTrigger asChild>
        <Button
          type="button"
          variant="ghost"
          size="icon"
          aria-label={filter.shown ? "Filter, inherited items shown" : "Filter"}
          title="Filter"
          data-active={filter.shown || undefined}
          className={cn(filter.shown && "bg-korur-blue/10 text-korur-blue-hover")}
        >
          <ListFilter aria-hidden />
        </Button>
      </PopoverTrigger>
      <PopoverContent align="end" className="w-auto p-3">
        <div className="flex items-center gap-1.5">
          <label className="flex items-center gap-2 text-sm whitespace-nowrap">
            <input
              type="checkbox"
              checked={filter.shown}
              onChange={(event) => filter.onChange(event.target.checked)}
            />
            Show inherited
          </label>
          <InfoTip label="What is inherited?">{INHERITED_HELP}</InfoTip>
        </div>
      </PopoverContent>
    </Popover>
  );
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
  /** The tabs of a page that has them, on the left. */
  tabs?: ReactNode;
  /** The inherited filter; null or left out hides the filter icon, as on a repository. */
  filter?: InheritedFilter | null;
  /** The list's search; left out when the list has none. */
  search?: CollapsibleSearchProps;
  /** Other actions before the create button, such as the Allowlist's Edit. */
  actions?: ReactNode;
  /** The create button, usually a `NewButton`; null or left out on a read only scope. */
  create?: ReactNode;
};

/**
 * The one row above a config list: its tabs on the left; the filter, the
 * search and the create button on the right.
 */
export function ListToolbar({ tabs, filter, search, actions, create }: ListToolbarProps) {
  return (
    <div className="flex min-h-9 flex-wrap items-center justify-between gap-2">
      <div className="min-w-0">{tabs}</div>
      <div className="ml-auto flex min-w-0 items-center justify-end gap-1">
        {filter && <FilterMenu filter={filter} />}
        {search && <CollapsibleSearch {...search} />}
        {actions}
        {create && <div className="ml-1">{create}</div>}
      </div>
    </div>
  );
}
