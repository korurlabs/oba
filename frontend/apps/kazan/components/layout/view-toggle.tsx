"use client";

import { LayoutList, LayoutGrid } from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn } from "@oba/ui/lib/utils";
import { useTranslations } from "next-intl";

export type ViewMode = "list" | "kanban";

interface ViewToggleProps {
  view: ViewMode;
  onViewChange: (view: ViewMode) => void;
  className?: string;
}

export function ViewToggle({ view, onViewChange, className }: ViewToggleProps) {
  const t = useTranslations();

  return (
    <div
      className={cn("inline-flex h-8 items-center gap-1 rounded-lg bg-muted p-1", className)}
      role="group"
      aria-label={t("views.modeToggle")}
    >
      {/* List View Button */}
      <Button
        variant="ghost"
        onClick={view !== "list" ? () => onViewChange("list") : undefined}
        className={cn(
          "h-6 min-w-0 gap-1.5 rounded-md px-2 text-xs transition-all",
          view === "list"
            ? "bg-card text-foreground shadow-sm pointer-events-none"
            : "text-muted-foreground hover:bg-accent hover:text-foreground"
        )}
        aria-label={t("views.switchToList")}
        aria-pressed={view === "list"}
      >
        <LayoutList className="h-4 w-4 !text-current" />
        <span className="hidden text-xs font-medium md:block">{t("views.list")}</span>
      </Button>

      {/* Kanban View Button */}
      <Button
        variant="ghost"
        onClick={view !== "kanban" ? () => onViewChange("kanban") : undefined}
        className={cn(
          "h-6 min-w-0 gap-1.5 rounded-md px-2 text-xs transition-all",
          view === "kanban"
            ? "bg-card text-foreground shadow-sm pointer-events-none"
            : "text-muted-foreground hover:bg-accent hover:text-foreground"
        )}
        aria-label={t("views.switchToKanban")}
        aria-pressed={view === "kanban"}
      >
        <LayoutGrid className="h-4 w-4 !text-current" />
        <span className="hidden text-xs font-medium md:block">{t("views.kanban")}</span>
      </Button>
    </div>
  );
}
