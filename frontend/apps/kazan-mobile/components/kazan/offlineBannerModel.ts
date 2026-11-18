import type { NativeStackNavigationOptions } from "@react-navigation/native-stack";

/** Where a stack screen shows the offline strip. */
export type OfflineBannerPlacement = "underHeader" | "none";

/**
 * A pushed screen with the stack's native bar shows the strip under that
 * bar. A screen without the bar draws its own title row and mounts the strip
 * under it, and a sheet or a modal shows none.
 */
export function offlineBannerPlacement(
  options: Pick<NativeStackNavigationOptions, "headerShown" | "presentation">
): OfflineBannerPlacement {
  if (options.headerShown === false) return "none";
  if (options.presentation !== undefined && options.presentation !== "card") return "none";
  return "underHeader";
}
