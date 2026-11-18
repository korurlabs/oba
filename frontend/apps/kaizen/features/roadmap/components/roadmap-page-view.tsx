"use client";

import { useMemo, useRef, useState } from "react";
import { useTranslations } from "next-intl";
import { Flag, Plus } from "lucide-react";
import { cn } from "@oba/ui/lib/utils";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/ui/empty-state";
import { useManageableBoardIds } from "@/features/boards/hooks/use-board-role";
import { useBoardViewTitle } from "@/features/boards/hooks/use-board-view-title";
import { useRoadmap } from "@/features/milestones/hooks/use-milestones";
import { ROADMAP_PAGE_PARAMS } from "@/features/milestones/hooks/query-keys";
import { MilestoneFormDialog } from "@/features/milestones/components/milestone-form-dialog";
import { MilestoneDetailDialog } from "@/features/milestones/components/milestone-detail-dialog";
import type { MilestoneListResponse } from "@/features/milestones/schemas";
import { ROADMAP_ZOOMS, useRoadmapParams } from "../hooks/use-roadmap-params";
import { filterRoadmapBoards } from "../lib/roadmap-layout";
import { RoadmapFilters } from "./roadmap-filters";
import { RoadmapTimeline, type RoadmapTimelineHandle } from "./roadmap-timeline";

interface RoadmapPageViewProps {
  boardId: string;
}

export function RoadmapPageView({ boardId }: RoadmapPageViewProps) {
  const t = useTranslations("roadmap");
  useBoardViewTitle(boardId, t("title"));
  const [{ zoom, status, health }, setParams] = useRoadmapParams();
  const { data: roadmapResponse, isLoading, isError } = useRoadmap(boardId, ROADMAP_PAGE_PARAMS);
  const [showCreate, setShowCreate] = useState(false);
  const timelineRef = useRef<RoadmapTimelineHandle>(null);
  const [selected, setSelected] = useState<Pick<MilestoneListResponse, "id" | "boardId"> | null>(
    null,
  );

  const boards = useMemo(() => roadmapResponse?.data?.boards ?? [], [roadmapResponse]);
  const filteredBoards = useMemo(
    () => filterRoadmapBoards(boards, { status, health }),
    [boards, status, health],
  );
  const boardIds = useMemo(() => boards.map((board) => board.id), [boards]);
  const manageableBoardIds = useManageableBoardIds(boardIds);

  const hasMilestones = boards.some((board) => board.milestones.length > 0);
  const hasMatches = filteredBoards.some((board) => board.milestones.length > 0);
  const isFiltered = !!status || (health?.length ?? 0) > 0;
  const showTimeline = !isLoading && !isError && hasMilestones && (hasMatches || boards.length > 1);

  return (
    <div className="flex h-full flex-col overflow-hidden">
      <div className="border-border bg-card flex flex-wrap items-center gap-2 border-b px-4 py-2 lg:px-6">
        <h1 className="text-foreground text-sm font-semibold">{t("title")}</h1>
        <RoadmapFilters />
        <div className="flex-1" />
        <Button
          variant="outline"
          size="sm"
          className="h-8"
          disabled={!showTimeline}
          onClick={() => timelineRef.current?.scrollToToday()}
        >
          {t("today")}
        </Button>
        <div
          className="border-border inline-flex rounded-md border p-0.5"
          role="group"
          aria-label={t("zoom.label")}
        >
          {ROADMAP_ZOOMS.map((value) => (
            <button
              key={value}
              type="button"
              aria-pressed={zoom === value}
              onClick={() => setParams({ zoom: value })}
              className={cn(
                "rounded px-2.5 py-1 text-xs font-medium transition-colors",
                "focus-visible:ring-ring focus-visible:ring-2 focus-visible:outline-none",
                zoom === value
                  ? "bg-primary text-primary-foreground"
                  : "text-muted-foreground hover:text-foreground",
              )}
            >
              {t(`zoom.${value}`)}
            </button>
          ))}
        </div>
        <Button size="sm" className="h-8 gap-1.5" onClick={() => setShowCreate(true)}>
          <Plus className="h-3.5 w-3.5" />
          {t("newMilestone")}
        </Button>
      </div>

      <div
        className={cn(
          "min-h-0 flex-1 p-4 lg:p-6",
          showTimeline ? "overflow-hidden" : "overflow-auto",
        )}
      >
        {isLoading ? (
          <div className="space-y-3">
            <Skeleton className="h-10 w-full" />
            <Skeleton className="h-24 w-full" />
            <Skeleton className="h-24 w-full" />
          </div>
        ) : isError ? (
          <p className="text-destructive text-sm">{t("loadError")}</p>
        ) : !hasMilestones ? (
          <EmptyState
            Icon={Flag}
            title={t("empty.title")}
            description={t("empty.description")}
            action={{ label: t("newMilestone"), onClick: () => setShowCreate(true) }}
          />
        ) : !showTimeline ? (
          <EmptyState
            Icon={Flag}
            title={t("filters.noMatches")}
            description={t("filters.noMatchesDescription")}
            action={
              isFiltered
                ? { label: t("filters.clear"), onClick: () => setParams({ status: null, health: null }) }
                : undefined
            }
          />
        ) : (
          <RoadmapTimeline
            ref={timelineRef}
            boards={filteredBoards}
            grouped={boards.length > 1}
            zoom={zoom}
            pageBoardId={boardId}
            manageableBoardIds={manageableBoardIds}
            onSelectMilestone={(milestone) =>
              setSelected({ id: milestone.id, boardId: milestone.boardId })
            }
          />
        )}
      </div>

      <MilestoneFormDialog open={showCreate} onOpenChange={setShowCreate} boardId={boardId} />
      <MilestoneDetailDialog
        boardId={selected?.boardId}
        milestoneId={selected?.id}
        open={selected !== null}
        onOpenChange={(open) => {
          if (!open) setSelected(null);
        }}
      />
    </div>
  );
}
