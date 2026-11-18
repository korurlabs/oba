import * as React from "react";
import { Pressable, ScrollView, View } from "react-native";

import { BOARD_VIEWS, type BoardView } from "@/components/boards/boardModel";
import { hapticSelection } from "@/components/boards/dragFeedback";
import { Text } from "@/components/ui/text";
import { useTranslation, type TranslationKey } from "@/i18n";
import { cn } from "@/lib/utils";

const VIEW_LABEL: Record<BoardView, TranslationKey> = {
  all: "boardViewAll",
  sprint: "boardViewSprint",
  backlog: "boardViewBacklog",
  roadmap: "boardViewRoadmap",
};

type BoardViewTabsProps = {
  value: BoardView;
  onChange: (view: BoardView) => void;
};

/**
 * The board's views All, Sprint, Backlog and Roadmap as underline tabs under
 * the nav bar: each tab as wide as its label, the chosen one in the
 * foreground colour on a primary bar, with a selection tick on change.
 * The row scrolls sideways when a large system text size makes the four
 * labels wider than the screen; it does not grow in height (a ScrollView
 * grows by default).
 */
function BoardViewTabs({ value, onChange }: BoardViewTabsProps) {
  const { t } = useTranslation();
  return (
    <ScrollView
      horizontal
      showsHorizontalScrollIndicator={false}
      accessibilityRole="tablist"
      className="grow-0 border-b border-border"
      contentContainerClassName="gap-5 px-4"
    >
      {BOARD_VIEWS.map((view) => {
        const selected: boolean = view === value;
        return (
          <Pressable
            key={view}
            accessibilityRole="tab"
            accessibilityState={{ selected }}
            onPress={() => {
              if (selected) return;
              hapticSelection();
              onChange(view);
            }}
            className="min-h-11 items-center justify-center px-0.5 active:opacity-70"
          >
            <Text
              className={cn(
                "text-subhead",
                selected
                  ? "font-sans-semibold text-foreground"
                  : "font-sans-medium text-muted-foreground"
              )}
              numberOfLines={1}
            >
              {t(VIEW_LABEL[view])}
            </Text>
            <View
              className={cn(
                "absolute bottom-0 left-0 right-0 h-[3px] rounded-t",
                selected ? "bg-primary" : "bg-transparent"
              )}
            />
          </Pressable>
        );
      })}
    </ScrollView>
  );
}

export { BoardViewTabs };
export type { BoardViewTabsProps };
