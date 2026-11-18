import { useColorScheme } from "nativewind";

import { THEME } from "@/lib/theme";

/**
 * The raw colours of the current scheme, for props that take a colour string
 * instead of a class: ripples, spinners, Switch tracks, sheet backgrounds.
 */
export function useThemeColors() {
  const { colorScheme } = useColorScheme();
  return THEME[colorScheme ?? "light"];
}
