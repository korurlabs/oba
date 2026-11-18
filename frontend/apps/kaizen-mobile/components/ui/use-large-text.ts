import { useWindowDimensions } from "react-native";

/**
 * The text scale above which the system is at an accessibility size: iOS's
 * largest standard size, xxxLarge, is 135 % of the default, and its
 * accessibility sizes start above that.
 */
const ACCESSIBILITY_FONT_SCALE = 1.4;

/**
 * True at an accessibility text size. There a line cut to one or two lines
 * would show a word or two, so titles and rows wrap instead of truncating.
 */
export function useLargeText(): boolean {
  return useWindowDimensions().fontScale > ACCESSIBILITY_FONT_SCALE;
}
