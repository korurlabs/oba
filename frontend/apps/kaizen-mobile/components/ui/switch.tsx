import * as React from "react";
import { Platform, Switch as RNSwitch, type SwitchProps as RNSwitchProps } from "react-native";

import { useThemeColors } from "@/components/ui/use-theme-colors";

type SwitchProps = Omit<RNSwitchProps, "trackColor" | "thumbColor" | "ios_backgroundColor">;

/**
 * The native switch, for a setting that applies at once (never for something
 * that needs Save). iOS keeps the system green; Android takes the Material 3
 * colours, `primary` track when on. Name it with `accessibilityLabel` unless a
 * row title already does.
 */
function Switch({ value, ...props }: SwitchProps) {
  const colors = useThemeColors();
  const androidColors =
    Platform.OS === "android"
      ? {
          trackColor: { false: colors.muted, true: colors.primary },
          thumbColor: value ? colors.primaryForeground : colors.mutedForeground,
        }
      : {};
  return <RNSwitch value={value} {...androidColors} {...props} />;
}

export { Switch };
export type { SwitchProps };
