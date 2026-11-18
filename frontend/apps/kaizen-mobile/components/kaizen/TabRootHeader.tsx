import type { LucideIcon } from "lucide-react-native";
import * as React from "react";
import { Platform, View } from "react-native";

import { IconButton } from "@/components/ui/icon-button";
import { Text } from "@/components/ui/text";
import { cn } from "@/lib/utils";

const IS_ANDROID = Platform.OS === "android";

/**
 * The gap between the header and the first thing of the screen's scroll
 * content. Every tab root starts its content with it, so the first control
 * sits the same distance under the title on every tab.
 */
export const TAB_ROOT_CONTENT_TOP = "pt-2";

/** An icon-only action on the title row, such as Links or New board. */
export type TabRootHeaderAction = {
  icon: LucideIcon;
  /** Read by VoiceOver and TalkBack; the icon has no text. */
  label: string;
  onPress: () => void;
};

type TabRootHeaderProps = {
  title: string;
  /** Icon actions at the end of the title row, in order. */
  actions?: TabRootHeaderAction[];
  /** A labelled action at the end of the title row, after the icon actions. */
  trailing?: React.ReactNode;
};

/** The large title stops growing here, so the longest tab name still fits on one line. */
const LARGE_TITLE_MAX_FONT_SCALE = 1.5;

/**
 * The title row of every tab root, on both platforms: the tab's name as a
 * large title (34 pt bold on iOS, Material's 28 dp regular on Android) at the
 * leading edge and its actions at the trailing edge. It
 * sits under the status bar, above the screen's scroll view, and stays put
 * while the content scrolls, so each tab's title starts at the same place.
 * The screen gives it the top safe area (a SafeAreaView with the top edge)
 * and hides the stack's own header.
 *
 * With a large system text size the title grows to half again its size, as
 * far as a tab name still fits on one line, and the actions wrap below it
 * when both no longer fit side by side.
 *
 * An icon button's touch box is wider than its glyph, so the icon actions
 * pull into the row's end padding by that difference; the glyph then lines
 * up with the edge of a labelled action such as Report incident.
 */
export function TabRootHeader({ title, actions, trailing }: TabRootHeaderProps) {
  const hasActions: boolean = !!actions && actions.length > 0;
  return (
    <View className="min-h-16 flex-row flex-wrap items-center justify-between gap-3 px-4 pt-2">
      <Text
        variant={IS_ANDROID ? undefined : "large-title"}
        accessibilityRole="header"
        numberOfLines={1}
        maxFontSizeMultiplier={LARGE_TITLE_MAX_FONT_SCALE}
        className={cn("shrink", IS_ANDROID && "font-sans text-[28px] leading-9")}
      >
        {title}
      </Text>
      {hasActions || trailing ? (
        <View className="ml-auto flex-row items-center gap-1">
          {hasActions ? (
            <View className={trailing ? "flex-row items-center" : "-mr-3 flex-row items-center"}>
              {actions?.map((action: TabRootHeaderAction) => (
                <IconButton
                  key={action.label}
                  icon={action.icon}
                  label={action.label}
                  onPress={action.onPress}
                />
              ))}
            </View>
          ) : null}
          {trailing}
        </View>
      ) : null}
    </View>
  );
}
