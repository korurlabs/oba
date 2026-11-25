"use client";

import { LayoutList, LayoutGrid, LayoutPanelTop, type LucideIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn } from "@oba/ui/lib/utils";
import { useTranslations } from "next-intl";

export type ViewMode = "list" | "kanban";

/** Every view a page can offer; a page picks its own through `modes`. */
export type AnyViewMode = ViewMode | "cards";

/** The views of the ticket pages, which offer no cards view. */
export const DEFAULT_VIEW_MODES: readonly ViewMode[] = ["list", "kanban"];

const VIEW_MODE_META: Record<
  AnyViewMode,
  {
    icon: LucideIcon;
    labelKey: "views.cards" | "views.list" | "views.kanban";
    switchKey: "views.switchToCards" | "views.switchToList" | "views.switchToKanban";
  }
> = {
  cards: { icon: LayoutPanelTop, labelKey: "views.cards", switchKey: "views.switchToCards" },
  list: { icon: LayoutList, labelKey: "views.list", switchKey: "views.switchToList" },
  kanban: { icon: LayoutGrid, labelKey: "views.kanban", switchKey: "views.switchToKanban" },
};

interface ViewToggleProps<M extends AnyViewMode> {
  view: M;
  onViewChange: (view: M) => void;
  /** The views offered, in button order. Left out, the toggle offers list and kanban. */
  modes?: readonly M[];
  className?: string;
}

export function ViewToggle<M extends AnyViewMode = ViewMode>({
  view,
  onViewChange,
  modes,
  className,
}: ViewToggleProps<M>) {
  const t = useTranslations();
  const shownModes = modes ?? (DEFAULT_VIEW_MODES as readonly AnyViewMode[] as readonly M[]);

  return (
    <div
      className={cn(
        "border-border bg-card inline-flex h-8 items-center gap-0.5 border p-0.5",
        className
      )}
      role="group"
      aria-label={t("views.modeToggle")}
    >
      {shownModes.map((mode) => {
        const { icon: Icon, labelKey, switchKey } = VIEW_MODE_META[mode];
        const isActive = view === mode;
        return (
          <Button
            key={mode}
            variant="ghost"
            onClick={isActive ? undefined : () => onViewChange(mode)}
            className={cn(
              "h-6 min-w-0 gap-1.5 px-2 font-mono text-xs tracking-[0.08em] uppercase transition-colors",
              isActive
                ? "bg-primary/10 text-korur-blue-strong ring-primary/50 pointer-events-none ring-1 ring-inset"
                : "text-muted-foreground hover:bg-primary/5 hover:text-foreground"
            )}
            aria-label={t(switchKey)}
            aria-pressed={isActive}
          >
            <Icon className="h-4 w-4 !text-current" />
            <span className="hidden text-xs font-medium md:block">{t(labelKey)}</span>
          </Button>
        );
      })}
    </div>
  );
}
