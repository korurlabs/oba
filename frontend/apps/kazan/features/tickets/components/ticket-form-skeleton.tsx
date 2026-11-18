/**
 * TicketFormSkeleton Component
 *
 * Renders skeleton placeholders that mirror the TicketFormFields layout
 * while the ticket detail query is in flight. Prevents the empty-input
 * flash users see when opening the form via deep link, sub-ticket
 * navigation, or any path where the detail isn't cached yet.
 *
 * Layout intentionally tracks `ticket-form-fields.tsx` -- the same two panes
 * in both shells: content on the left, the properties panel of label-left
 * rows on the right. `withTabs` adds the page's tab bar. Swapping in the real
 * form on resolve therefore causes minimal layout shift.
 */

"use client";

import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";

/**
 * Row counts per panel section, in the order the real panel renders them in
 * edit mode (the only mode that shows this skeleton): Workflow (Status,
 * Placement), People, Details (Type, Priority, Project, Labels, Parent),
 * Planning (Due date, Weight, Mission Rank, Progress).
 */
const PANEL_GROUP_ROWS = [2, 2, 5, 4];

function PanelRowSkeleton() {
  return (
    <div className="grid grid-cols-[7rem_minmax(0,1fr)] items-center gap-x-3">
      <Skeleton className="h-3 w-16" />
      <Skeleton className="h-9 w-full" />
    </div>
  );
}

function PanelSkeleton({ className }: { className?: string }) {
  return (
    <div
      className={cn(
        "@container flex min-w-0 flex-col gap-3 lg:border-border lg:border-l lg:pl-6",
        className,
      )}
    >
      {PANEL_GROUP_ROWS.map((rows, group) => (
        <div
          key={group}
          className="grid grid-cols-1 gap-x-6 gap-y-2 @xl:grid-cols-2"
        >
          {/* Section heading over its divider, like the real one: `h-4` is the
              `text-xs` line height, then `pb-1` and the bottom border. */}
          <div className="border-border col-span-full border-b pb-1">
            <Skeleton className="h-4 w-16" />
          </div>
          {Array.from({ length: rows }).map((_, i) => (
            <PanelRowSkeleton key={i} />
          ))}
        </div>
      ))}
    </div>
  );
}

function TitleSkeleton() {
  return (
    <div className="space-y-2">
      <Skeleton className="h-3 w-12" />
      <Skeleton className="h-11 w-full" />
    </div>
  );
}

function DescriptionSkeleton({ height }: { height: string }) {
  return (
    <div className="space-y-2">
      <Skeleton className="h-3 w-20" />
      <Skeleton className={cn("w-full", height)} />
    </div>
  );
}

function SubTicketsSkeleton() {
  return (
    <div className="border-border space-y-2 border-t pt-3">
      <Skeleton className="h-3 w-24" />
      <div className="space-y-1.5">
        {Array.from({ length: 3 }).map((_, i) => (
          <Skeleton key={i} className="h-8 w-full" />
        ))}
      </div>
    </div>
  );
}

function FooterSkeleton() {
  return (
    <div className="border-border bg-muted flex shrink-0 justify-end gap-2 border-t px-4 py-3 md:px-6">
      <Skeleton className="h-8 w-20" />
      <Skeleton className="h-8 w-20" />
    </div>
  );
}

const PANES_CLASS =
  "mx-auto grid w-full max-w-6xl grid-cols-1 gap-x-6 gap-y-4 lg:grid-cols-[minmax(0,1fr)_20rem]";

export function TicketFormSkeleton({ withTabs = false }: { withTabs?: boolean }) {
  if (withTabs) {
    return (
      <>
        <div className="min-h-0 flex-1 overflow-y-auto px-4 py-4 md:px-6">
          <div className={PANES_CLASS}>
            <div className="min-w-0 space-y-4">
              {/* Tab bar */}
              <Skeleton className="h-9 w-48" />
              <TitleSkeleton />
              <DescriptionSkeleton height="h-[32rem]" />
              <SubTicketsSkeleton />
            </div>
            <PanelSkeleton />
          </div>
        </div>

        <FooterSkeleton />
      </>
    );
  }

  return (
    <>
      <div className="min-h-0 flex-1 overflow-y-auto px-4 py-4 md:px-6">
        <div className={cn(PANES_CLASS, "lg:grid-rows-[auto_1fr]")}>
          <div className="min-w-0 lg:col-start-1 lg:row-start-1">
            <TitleSkeleton />
          </div>
          <PanelSkeleton className="lg:col-start-2 lg:row-span-2 lg:row-start-1" />
          <div className="flex min-w-0 flex-col gap-4 lg:col-start-1 lg:row-start-2">
            <DescriptionSkeleton height="h-[200px] lg:h-[18rem]" />
            <SubTicketsSkeleton />
          </div>
        </div>
      </div>

      {/* Footer placeholder — keeps action buttons in their final position */}
      <FooterSkeleton />
    </>
  );
}
