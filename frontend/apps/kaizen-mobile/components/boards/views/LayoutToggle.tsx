import { Kanban, List } from "lucide-react-native";
import type { LucideIcon } from "lucide-react-native";
import * as React from "react";
import { Platform, Pressable, View } from "react-native";

import type { SprintLayout } from "@/components/boards/boardModel";
import { Icon } from "@/components/ui/icon";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import { useTranslation } from "@/i18n";
import { cn } from "@/lib/utils";

const SEGMENTS: readonly { layout: SprintLayout; icon: LucideIcon }[] = [
  { layout: "list", icon: List },
  { layout: "kanban", icon: Kanban },
];

type LayoutToggleProps = {
  value: SprintLayout;
  onChange: (layout: SprintLayout) => void;
};

/**
 * The Sprint view's list | Kanban switch, at the head of its chip row: two
 * icon segments in a chip-high outline, the chosen one on the selected chip
 * ground.
 */
function LayoutToggle({ value, onChange }: LayoutToggleProps) {
  const { t } = useTranslation();
  const colors = useThemeColors();
  return (
    <View
      accessibilityRole="radiogroup"
      className="h-8 flex-row items-center self-start rounded-full border border-border bg-card p-0.5"
    >
      {SEGMENTS.map(({ layout, icon }) => {
        const selected: boolean = layout === value;
        return (
          <Pressable
            key={layout}
            accessibilityRole="radio"
            accessibilityLabel={layout === "list" ? t("boardLayoutList") : t("boardLayoutKanban")}
            accessibilityState={{ checked: selected }}
            hitSlop={{ top: 6, bottom: 6 }}
            onPress={() => {
              if (!selected) onChange(layout);
            }}
            android_ripple={{ color: colors.accent, borderless: false, foreground: true }}
            className={cn(
              "h-full w-9 items-center justify-center overflow-hidden rounded-full",
              selected && "bg-primary-light",
              Platform.select({ ios: "active:opacity-60" })
            )}
          >
            <Icon
              as={icon}
              size={16}
              className={selected ? "text-link" : "text-muted-foreground"}
            />
          </Pressable>
        );
      })}
    </View>
  );
}

export { LayoutToggle };
