"use client";

import { useId, useState } from "react";
import { useTranslations } from "next-intl";
import { SlidersHorizontal } from "lucide-react";
import {
  ColumnVisibilityContent,
  type ColumnVisibilityContentProps,
} from "@oba/shared/components/sensey-table";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Separator } from "@/components/ui/separator";
import { Switch } from "@/components/ui/switch";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  TicketFilterPanelContent,
  type TicketFilterPanelProps,
} from "./ticket-filter-panel";

type ViewOptionsTab = "columns" | "filters";

interface ViewOptionsPopoverProps {
  /** Whether tickets from this board's child boards are included in the list. */
  includeAllBoards: boolean;
  onIncludeAllBoardsChange: (checked: boolean) => void;
  /**
   * Column visibility and ordering. Omitted by views that keep a fixed column
   * set, which drops the section rather than showing an inert one.
   */
  columns?: ColumnVisibilityContentProps;
  /** Everything the filter browser needs, passed through verbatim. */
  filters: TicketFilterPanelProps;
}

/**
 * The board list toolbars' single "View" control: one trigger holding the
 * three things that shape what the table shows -- child-board inclusion,
 * which columns are visible, and the ticket filters.
 *
 * Columns and filters sit behind two tabs because stacking both sections
 * made the popover taller than the viewport. The "include all boards" switch
 * stays above the tabs: it selects the dataset itself, so it applies to
 * whichever tab is open and belongs to neither.
 *
 * The trigger carries the active filter count, because a filter the user
 * cannot see is a filter they will not think to clear. The Filters tab
 * trigger repeats that count for the same reason -- with filters one tab
 * away, the badge on the trigger alone would not say which tab to open.
 */
export function ViewOptionsPopover({
  includeAllBoards,
  onIncludeAllBoardsChange,
  columns,
  filters,
}: ViewOptionsPopoverProps) {
  const t = useTranslations();
  const includeAllBoardsId = useId();
  const activeCount = filters.activeCount;

  /**
   * Transient popover state, deliberately not in the URL: the tab is a
   * property of this open popover, not of the page the user could share or
   * reload into. Columns is the default -- the filter count on the Filters
   * trigger surfaces active filters without the opening tab moving around
   * under the user depending on state.
   */
  const [tab, setTab] = useState<ViewOptionsTab>("columns");

  const includeAllBoardsRow = (
    <div className="flex shrink-0 items-center justify-between gap-4 px-4 py-3">
      <Label htmlFor={includeAllBoardsId} className="cursor-pointer text-sm font-normal">
        {t("board.includeAllBoards")}
      </Label>
      <Switch
        id={includeAllBoardsId}
        checked={includeAllBoards}
        onCheckedChange={onIncludeAllBoardsChange}
        className="h-4 w-8"
      />
    </div>
  );

  const filtersBadge = activeCount > 0 && (
    <Badge
      variant="secondary"
      className="bg-primary/15 hover:bg-primary/15 text-primary ml-1.5 px-1.5 py-0 text-[10px]"
    >
      {activeCount}
    </Badge>
  );

  return (
    <Popover>
      <PopoverTrigger asChild>
        <Button
          variant="outline"
          size="sm"
          className="border-border h-8 gap-2 px-3"
          aria-label={t("views.options")}
        >
          <SlidersHorizontal className="h-4 w-4" />
          <span className="hidden text-xs font-medium md:inline">{t("views.options")}</span>
          {activeCount > 0 && (
            <Badge
              variant="secondary"
              className="bg-primary/15 hover:bg-primary/15 text-primary ml-1"
            >
              {activeCount}
            </Badge>
          )}
        </Button>
      </PopoverTrigger>
      <PopoverContent
        align="end"
        collisionPadding={16}
        className="flex max-h-[var(--radix-popover-content-available-height)] w-[min(520px,calc(100vw-2rem))] flex-col overflow-hidden p-0"
      >
        {/* Scope -- which boards the list draws from. Governs both tabs. */}
        {includeAllBoardsRow}
        <Separator />

        {columns ? (
          <Tabs
            value={tab}
            onValueChange={(value) => setTab(value as ViewOptionsTab)}
            className="flex min-h-0 flex-col"
          >
            <div className="shrink-0 px-4 pt-3">
              <TabsList className="grid w-full grid-cols-2">
                <TabsTrigger value="columns">{t("columns")}</TabsTrigger>
                <TabsTrigger value="filters">
                  {t("filters.title")}
                  {filtersBadge}
                </TabsTrigger>
              </TabsList>
            </div>

            {/*
              Both panels are force-mounted so that opening the popover mounts
              exactly what it mounted before the tabs existed -- the filter
              browser's lookup queries still fire on open rather than on first
              visit to the Filters tab -- and so switching tab does not throw
              away the browser's selected category or the column list's drag
              context. Radix does NOT hide a force-mounted panel for you: it
              renders hidden={!present} and `present` is unconditionally true
              under forceMount, so both would be visible at once without the
              explicit data-[state=inactive]:hidden on each panel.

              The tab body is pinned to one height rather than left to its
              content: a twelve-row column list and the filter browser have
              very different natural heights, and without this the popover
              jumped and resized every time the user switched tab. It is a
              flex child with min-h-0 and the default shrink factor, so the
              popover's own max-height -- the Radix available height -- still
              shrinks it on a short viewport instead of overflowing it. Neither
              panel scrolls as a whole: the column list scrolls above its
              footer, so the save buttons stay visible, and the filter browser
              keeps its own inner scroll.
            */}
            <div className="flex h-[420px] min-h-0 flex-col">
              <TabsContent
                value="columns"
                forceMount
                className="mt-0 flex min-h-0 flex-1 flex-col px-4 py-3 data-[state=inactive]:hidden"
              >
                {/* The tab is already labelled "Columns"; hideLabel drops the
                    duplicate heading but keeps its info tooltip. */}
                <ColumnVisibilityContent {...columns} hideLabel className="flex-1" />
              </TabsContent>

              <TabsContent
                value="filters"
                forceMount
                className="mt-0 flex min-h-0 flex-1 flex-col data-[state=inactive]:hidden"
              >
                <TicketFilterPanelContent {...filters} className="min-h-0 flex-1" />
              </TabsContent>
            </div>
          </Tabs>
        ) : (
          /*
            Views with a fixed column set have nothing to put in a Columns tab,
            so a single-tab strip would be noise: they keep the plain labelled
            filter section they had before, which is also the only thing in the
            popover and so has nothing to jump against.
          */
          <div className="flex min-h-0 flex-1 flex-col">
            <p className="text-muted-foreground shrink-0 px-4 pt-3 text-xs font-medium">
              {t("filters.title")}
            </p>
            <TicketFilterPanelContent {...filters} className="min-h-0 flex-1" />
          </div>
        )}
      </PopoverContent>
    </Popover>
  );
}
