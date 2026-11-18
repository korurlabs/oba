"use client";

import { useBoards } from "@/features/boards/hooks/use-boards";
import { CreateBoardDialog } from "@/features/boards/components/create-board-dialog";
import { HomeDeadlines } from "@/features/home/components/home-deadlines";
import { HomeLeaderboard } from "@/features/home/components/home-leaderboard";
import { HomeLinksMenu } from "@/features/home/components/home-links-menu";
import { HomeSprintStrip } from "@/features/home/components/home-sprint-strip";
import { HomeStaleBacklog } from "@/features/home/components/home-stale-backlog";
import { useTranslations } from "next-intl";
import { AlertCircle, LayoutGrid, Plus, RotateCw } from "lucide-react";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

/**
 * The home page, a sprint dashboard: the Korur sprint countdown and burndown,
 * the leaderboard beside the upcoming deadlines, and the stale backlog. The
 * links to Kaizen lists and the Korur apps sit behind one icon in the header,
 * so the page body is about the work. Each section fetches and fails on its
 * own. A user without boards also gets a card that creates the first one,
 * because every section stays empty until a board exists.
 *
 * A failed boards fetch shows an error with a retry instead of that card: the
 * user may well have boards, and offering to create the first one would be
 * wrong. The create dialog is mounted only while it is open, because its own
 * boards query shares this one's key; mounted while closed, it refetches a
 * failed query on every mount, which flips this view back to loading, unmounts
 * the dialog and repeats the request without end.
 */
export function DashboardView() {
  const t = useTranslations("home");
  const {
    data: boardsResponse,
    isPending: boardsPending,
    isError: boardsError,
    refetch: refetchBoards,
    isRefetching: boardsRefetching,
  } = useBoards();
  const hasNoBoards =
    !boardsPending && !boardsError && (boardsResponse?.data?.length ?? 0) === 0;
  const [createDialogOpen, setCreateDialogOpen] = useState(false);

  return (
    <div className="bg-background h-full overflow-auto">
      <div className="mx-auto flex w-full max-w-6xl flex-col gap-6 px-4 py-6 sm:px-6 sm:py-8">
        <header className="flex items-center justify-between gap-4">
          <h1 className="text-foreground text-2xl font-semibold">{t("title")}</h1>
          <HomeLinksMenu />
        </header>

        {boardsError && (
          <div
            role="alert"
            className="border-destructive/30 bg-destructive/10 text-card-foreground flex items-center gap-4 rounded-2xl border p-4 sm:p-5"
          >
            <AlertCircle aria-hidden className="text-destructive size-5 shrink-0" />
            <p className="text-muted-foreground flex-1 text-sm">{t("boardsError")}</p>
            <Button
              type="button"
              variant="outline"
              size="sm"
              className="gap-2"
              disabled={boardsRefetching}
              onClick={() => void refetchBoards()}
            >
              <RotateCw aria-hidden className={cn("size-4", boardsRefetching && "animate-spin")} />
              {t("retry")}
            </Button>
          </div>
        )}

        {hasNoBoards && (
          <div className="bg-card border-border flex flex-col gap-4 rounded-2xl border p-4 sm:flex-row sm:items-center sm:p-5">
            <span className="bg-korur-blue-light text-korur-blue-strong dark:bg-korur-slate dark:text-korur-blue flex size-12 shrink-0 items-center justify-center rounded-xl">
              <LayoutGrid aria-hidden className="size-6" />
            </span>
            <div className="flex flex-1 flex-col gap-1">
              <h2 className="text-foreground text-base font-semibold">{t("noBoardsTitle")}</h2>
              <p className="text-muted-foreground text-sm">{t("noBoardsDescription")}</p>
            </div>
            <Button type="button" className="gap-2" onClick={() => setCreateDialogOpen(true)}>
              <Plus className="h-4 w-4" />
              <span>{t("createBoard")}</span>
            </Button>
          </div>
        )}

        <HomeSprintStrip />

        <div className="grid grid-cols-1 items-start gap-6 lg:grid-cols-2">
          <HomeLeaderboard />
          <HomeDeadlines />
        </div>

        <HomeStaleBacklog />
      </div>

      {hasNoBoards && createDialogOpen && (
        <CreateBoardDialog open={createDialogOpen} onOpenChange={setCreateDialogOpen} />
      )}
    </div>
  );
}
