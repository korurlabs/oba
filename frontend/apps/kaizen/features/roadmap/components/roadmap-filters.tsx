"use client";

import { useTranslations } from "next-intl";
import { ChevronDown } from "lucide-react";
import { cn } from "@oba/ui/lib/utils";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuCheckboxItem,
  DropdownMenuContent,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuSeparator,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
  MilestoneHealthSchema,
  MilestoneStatusSchema,
  type MilestoneHealth,
  type MilestoneStatus,
} from "@/features/milestones/schemas";
import { useRoadmapParams } from "../hooks/use-roadmap-params";

const ALL = "ALL";

/** Status (open or closed) and health filters, kept in the URL. */
export function RoadmapFilters() {
  const t = useTranslations("roadmap.filters");
  const tMilestone = useTranslations("milestone");
  const [{ status, health }, setParams] = useRoadmapParams();
  const selectedHealth = health ?? [];

  const toggleHealth = (value: MilestoneHealth, checked: boolean) => {
    const next = checked
      ? [...selectedHealth, value]
      : selectedHealth.filter((item) => item !== value);
    setParams({ health: next.length > 0 ? next : null });
  };

  return (
    <div className="flex items-center gap-1">
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button
            variant="ghost"
            size="sm"
            className={cn("h-8 gap-1 text-xs", status && "text-primary")}
          >
            {status ? t("statusValue", { value: tMilestone(`status.${status}`) }) : t("status")}
            <ChevronDown className="h-3.5 w-3.5" aria-hidden />
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="start">
          <DropdownMenuRadioGroup
            value={status ?? ALL}
            onValueChange={(value) =>
              setParams({ status: value === ALL ? null : (value as MilestoneStatus) })
            }
          >
            <DropdownMenuRadioItem value={ALL}>{t("all")}</DropdownMenuRadioItem>
            {MilestoneStatusSchema.options.map((value) => (
              <DropdownMenuRadioItem key={value} value={value}>
                {tMilestone(`status.${value}`)}
              </DropdownMenuRadioItem>
            ))}
          </DropdownMenuRadioGroup>
        </DropdownMenuContent>
      </DropdownMenu>

      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button
            variant="ghost"
            size="sm"
            className={cn("h-8 gap-1 text-xs", selectedHealth.length > 0 && "text-primary")}
          >
            {selectedHealth.length > 0
              ? t("healthCount", { count: selectedHealth.length })
              : t("health")}
            <ChevronDown className="h-3.5 w-3.5" aria-hidden />
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="start">
          {MilestoneHealthSchema.options.map((value) => (
            <DropdownMenuCheckboxItem
              key={value}
              checked={selectedHealth.includes(value)}
              onCheckedChange={(checked) => toggleHealth(value, checked === true)}
              onSelect={(event) => event.preventDefault()}
            >
              {tMilestone(`health.${value}`)}
            </DropdownMenuCheckboxItem>
          ))}
          {selectedHealth.length > 0 && (
            <>
              <DropdownMenuSeparator />
              <DropdownMenuItem onSelect={() => setParams({ health: null })}>
                {t("clear")}
              </DropdownMenuItem>
            </>
          )}
        </DropdownMenuContent>
      </DropdownMenu>
    </div>
  );
}
