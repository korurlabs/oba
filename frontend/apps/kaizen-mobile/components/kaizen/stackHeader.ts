import type { NativeStackNavigationOptions } from "@react-navigation/native-stack";
import { Platform } from "react-native";

import type { THEME } from "@/lib/theme";

type ThemeColors = (typeof THEME)[keyof typeof THEME];

/**
 * The bar of a pushed screen in every stack: on the screen colour without a
 * shadow, the title in the label colour, and on iOS the Back button and bar
 * buttons in the link tint. The title gets its own colour because iOS also
 * paints a plain string title with the tint.
 */
export function stackHeaderOptions(colors: ThemeColors): NativeStackNavigationOptions {
  return {
    headerStyle: { backgroundColor: colors.screen },
    headerTintColor: Platform.OS === "ios" ? colors.link : colors.foreground,
    headerTitleStyle: { color: colors.foreground },
    headerShadowVisible: false,
    contentStyle: { backgroundColor: colors.screen },
  };
}

/**
 * How far a custom bar title grows with the system text size. The bar keeps
 * its height, as it does for the system's own titles, so a title with a
 * second line stops growing before the two lines overflow it.
 */
export const BAR_TITLE_MAX_FONT_SCALE = 1.2;
