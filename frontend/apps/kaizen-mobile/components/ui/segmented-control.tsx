import NativeSegmentedControl from "@react-native-segmented-control/segmented-control";
import { Check } from "lucide-react-native";
import { useColorScheme } from "nativewind";
import * as React from "react";
import { Platform, Pressable, View } from "react-native";

import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import { hapticSelection } from "@/lib/haptics";
import { cn } from "@/lib/utils";

type SegmentedControlProps = {
  /** The segment labels, in order. */
  values: string[];
  selectedIndex: number;
  onChange: (index: number) => void;
};

/**
 * A switch between a few views of one list, such as Assigned and Reported.
 * iOS shows the native control; Android shows Material segmented buttons, the
 * selected one on `primary-light` and led by a check. Each Android segment
 * is drawn 40 dp high inside a 48 dp pressable row, so the whole touch
 * minimum receives the tap; a hit slop would not, because touches outside
 * a parent's bounds never reach the child.
 */
function SegmentedControl({ values, selectedIndex, onChange }: SegmentedControlProps) {
  const { colorScheme } = useColorScheme();

  if (Platform.OS === "ios") {
    return (
      <NativeSegmentedControl
        values={values}
        selectedIndex={selectedIndex}
        // Follows the in-app appearance setting, which may differ from the device's.
        appearance={colorScheme === "dark" ? "dark" : "light"}
        onChange={(event) => {
          hapticSelection();
          onChange(event.nativeEvent.selectedSegmentIndex);
        }}
      />
    );
  }

  return <SegmentedButtons values={values} selectedIndex={selectedIndex} onChange={onChange} />;
}

function SegmentedButtons({ values, selectedIndex, onChange }: SegmentedControlProps) {
  const colors = useThemeColors();

  const last: number = values.length - 1;

  return (
    <View accessibilityRole="tablist" className="min-h-12 flex-row">
      {values.map((label: string, index: number) => {
        const selected: boolean = index === selectedIndex;
        return (
          <Pressable
            key={label}
            accessibilityRole="tab"
            accessibilityLabel={label}
            accessibilityState={{ selected }}
            android_ripple={{ color: colors.accent, foreground: true }}
            onPress={() => {
              if (selected) return;
              hapticSelection();
              onChange(index);
            }}
            className="min-h-12 flex-1 justify-center"
          >
            <View
              className={cn(
                "min-h-10 flex-row items-center justify-center gap-2 border-y border-l border-border px-3 py-2",
                index === 0 && "rounded-l-full",
                index === last && "rounded-r-full border-r",
                selected && "bg-primary-light"
              )}
            >
              {selected ? <Icon as={Check} size={18} /> : null}
              <Text numberOfLines={1} className="font-sans-medium text-subhead text-foreground">
                {label}
              </Text>
            </View>
          </Pressable>
        );
      })}
    </View>
  );
}

export { SegmentedControl };
export type { SegmentedControlProps };
