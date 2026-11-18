/**
 * Status bar styles of the screens. iOS reads the status bar from the screen
 * on top (Info.plist `UIViewControllerBasedStatusBarAppearance`), so each
 * stack names the style as a screen option instead of one global setting.
 * "light" is the light clock and icons for dark grounds, "dark" the dark ones
 * for light grounds.
 */
export type ScreenStatusBarStyle = "light" | "dark";

/**
 * The style that reads on the app's ground in `colorScheme`: light content on
 * the dark theme, dark content on the light one. The scheme is NativeWind's,
 * the one the tokens use, so the bar flips together with the screen when the
 * Appearance setting or the device changes.
 */
export function statusBarStyleFor(colorScheme: "light" | "dark" | undefined): ScreenStatusBarStyle {
  return colorScheme === "dark" ? "light" : "dark";
}

/** The full-screen image viewers are black in both themes. */
export const IMAGE_VIEWER_STATUS_BAR_STYLE: ScreenStatusBarStyle = "light";
