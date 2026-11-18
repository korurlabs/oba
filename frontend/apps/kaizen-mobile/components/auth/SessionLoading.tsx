import { ActivityIndicator, View } from "react-native";

import { useThemeColors } from "@/components/ui/use-theme-colors";
import { useTranslation } from "@/i18n";

/**
 * The quiet screen while the stored session or the organization is read at
 * start, so neither Sign in nor an empty app flashes before the answer is known.
 */
export function SessionLoading() {
  const { t } = useTranslation();
  const colors = useThemeColors();
  return (
    <View
      accessible
      accessibilityLabel={t("kitLoading")}
      className="flex-1 items-center justify-center bg-screen"
    >
      <ActivityIndicator size="large" color={colors.primary} />
    </View>
  );
}
