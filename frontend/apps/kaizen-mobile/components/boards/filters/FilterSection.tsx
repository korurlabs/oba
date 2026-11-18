import * as React from "react";
import { View } from "react-native";

import { Text } from "@/components/ui/text";

type FilterSectionProps = {
  title: string;
  /** How many options of this section are chosen; shown after the title. */
  selectedCount?: number;
  /** Shown between the title and the chips, such as a member search. */
  accessory?: React.ReactNode;
  /** Shown instead of the chips, such as "None on this board". */
  emptyText?: string | null;
  children?: React.ReactNode;
};

/** A titled group of multi-select chips that wrap onto more lines. */
function FilterSection({
  title,
  selectedCount = 0,
  accessory,
  emptyText,
  children,
}: FilterSectionProps) {
  return (
    <View className="gap-2 pt-5">
      <View className="flex-row items-center gap-1.5 px-4">
        <Text
          accessibilityRole="header"
          className="font-sans text-footnote uppercase text-muted-foreground"
        >
          {title}
        </Text>
        {selectedCount > 0 ? (
          <View className="min-h-[18px] min-w-[18px] items-center justify-center rounded-full bg-primary-fill px-1">
            <Text className="font-sans-semibold text-caption-2 text-primary-foreground">
              {selectedCount}
            </Text>
          </View>
        ) : null}
      </View>
      {accessory}
      {emptyText ? (
        <Text variant="subhead" className="px-4 text-muted-foreground">
          {emptyText}
        </Text>
      ) : (
        <View className="flex-row flex-wrap gap-2 px-4">{children}</View>
      )}
    </View>
  );
}

export { FilterSection };
export type { FilterSectionProps };
