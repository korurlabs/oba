import { useColorScheme } from "nativewind";
import type { ViewStyle } from "react-native";

import { shadowStyle, type ShadowToken } from "@/components/ui/shadow";

/**
 * The style that draws a design shadow in the current colour scheme. Dark mode
 * returns nothing: dark surfaces show depth by lightness (`card` is lighter
 * than `screen`), not by shadow. Put it on a view without `overflow-hidden`,
 * or the shadow is clipped.
 */
export function useShadow(token: ShadowToken | null | undefined): ViewStyle | undefined {
  const { colorScheme } = useColorScheme();
  return shadowStyle(token, colorScheme);
}
