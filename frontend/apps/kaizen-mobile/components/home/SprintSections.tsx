import { useRouter } from "expo-router";
import * as React from "react";

import { BurndownChart } from "@/components/home/BurndownChart";
import {
  DashSection,
  SectionEmpty,
  SectionError,
  SectionSkeleton,
} from "@/components/home/DashSection";
import { HomeSprintCard } from "@/components/home/HomeSprintCard";
import { useHomeLinks } from "@/components/home/homeNavigation";
import { ActionSheet, type ActionSheetAction } from "@/components/kaizen/ActionSheet";
import { Button } from "@/components/ui/button";
import type { useHomeSprint } from "@/hooks/queries/home";
import { useTranslation } from "@/i18n";

type SprintSectionsProps = {
  query: ReturnType<typeof useHomeSprint>;
  /** The slug of a board to open when no sprint is active, so one can be started. */
  fallbackBoardSlug: string | null;
  onChooseBoard: (boardId: string) => void;
};

/**
 * The "Active sprint" and "Burndown" sections, which share one read: the
 * active sprint of the focus board and its burndown. Each section still shows
 * its own error. With no active sprint the burndown section is left out.
 */
function SprintSections({ query, fallbackBoardSlug, onChooseBoard }: SprintSectionsProps) {
  const { t } = useTranslation();
  const router = useRouter();
  const { openBoardPage } = useHomeLinks();
  const [choosing, setChoosing] = React.useState<boolean>(false);
  const sprint = query.data;
  const retry = () => void query.refetch();

  if (query.isPending) {
    return (
      <DashSection title={t("homeSprintTitle")}>
        <SectionSkeleton rows={2} />
      </DashSection>
    );
  }

  if (query.isError && !sprint) {
    return (
      <>
        <DashSection title={t("homeSprintTitle")}>
          <SectionError message={t("homeSprintError")} onRetry={retry} />
        </DashSection>
        <DashSection title={t("homeBurndownTitle")}>
          <SectionError message={t("homeBurndownError")} onRetry={retry} />
        </DashSection>
      </>
    );
  }

  if (!sprint || !sprint.sprintId) {
    const slug: string | null = sprint?.boardSlug ?? fallbackBoardSlug;
    return (
      <DashSection title={t("homeSprintTitle")}>
        <SectionEmpty message={t("homeSprintEmpty")} />
        {slug ? (
          <Button
            variant="ghost"
            size="sm"
            className="self-center"
            onPress={() => openBoardPage(slug, "sprint")}
          >
            {t("homeSprintOpenPage")}
          </Button>
        ) : null}
      </DashSection>
    );
  }

  const boardActions: ActionSheetAction[] = sprint.boards.map((board) => ({
    label: board.name,
    selected: board.id === sprint.boardId,
    onPress: () => {
      if (board.id !== sprint.boardId) onChooseBoard(board.id);
    },
  }));

  return (
    <>
      <DashSection title={t("homeSprintTitle")}>
        <HomeSprintCard
          sprint={sprint}
          onChooseBoard={sprint.boards.length > 1 ? () => setChoosing(true) : undefined}
        />
        <ActionSheet
          visible={choosing}
          onClose={() => setChoosing(false)}
          title={t("homeSprintChooseBoard")}
          actions={boardActions}
        />
      </DashSection>
      <DashSection
        title={t("homeBurndownTitle")}
        onInfo={() =>
          router.push({ pathname: "/home/info/[topic]", params: { topic: "burndown" } })
        }
        infoLabel={t("homeBurndownInfo")}
      >
        {sprint.burndown.length > 0 ? (
          <BurndownChart
            points={sprint.burndown}
            totalTickets={sprint.totalTickets}
            openTickets={sprint.openTickets}
          />
        ) : (
          <SectionEmpty message={t("homeBurndownNoDates")} />
        )}
      </DashSection>
    </>
  );
}

export { SprintSections };
export type { SprintSectionsProps };
