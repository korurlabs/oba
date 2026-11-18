import {
  Inter_400Regular,
  Inter_500Medium,
  Inter_600SemiBold,
  Inter_700Bold,
} from "@expo-google-fonts/inter";
import {
  JetBrainsMono_400Regular,
  JetBrainsMono_500Medium,
  JetBrainsMono_600SemiBold,
} from "@expo-google-fonts/jetbrains-mono";

/**
 * Font families for `useFonts` in app/_layout.tsx.
 *
 * The Google font packages ship static faces, one per weight, and a static face
 * registered under one family name does not respond to `fontWeight` reliably on
 * iOS and Android. So each weight is its own family: "Inter" and
 * "JetBrainsMono" are Regular and back `font-sans` and `font-mono`; the others
 * back `font-sans-medium`, `font-sans-semibold`, `font-sans-bold`,
 * `font-mono-medium` and `font-mono-semibold` (tailwind.config.js fontFamily).
 *
 * The type scale uses Inter 400 to 700. Ticket keys and code use JetBrains Mono,
 * bundled so that they look the same on every Android vendor, whose system
 * monospace faces differ.
 */
export const FONT_MAP = {
  Inter: Inter_400Regular,
  "Inter-Medium": Inter_500Medium,
  "Inter-SemiBold": Inter_600SemiBold,
  "Inter-Bold": Inter_700Bold,
  JetBrainsMono: JetBrainsMono_400Regular,
  "JetBrainsMono-Medium": JetBrainsMono_500Medium,
  "JetBrainsMono-SemiBold": JetBrainsMono_600SemiBold,
} as const;
