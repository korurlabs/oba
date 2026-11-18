import { Stack } from "expo-router";

import { offlineScreenLayout } from "@/components/kazan/OfflineBanner";
import { stackHeaderOptions } from "@/components/kazan/stackHeader";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import { useTranslation } from "@/i18n";

// A link straight into Profile, Settings or Notification settings still has
// Account under it, so Back leads somewhere.
export const unstable_settings = { initialRouteName: "index" };

/**
 * The Account tab: Account at the root, Profile and Settings pushed over it,
 * and Notification settings pushed over Settings. Account draws its own
 * title row (TabRootHeader), like every tab root, so it hides the native bar;
 * the screens pushed over it name their Back button themselves. iOS reads
 * a Back title from the screen below only when it is pushed, so a title
 * taken from the hidden root kept the old language after a switch in
 * Settings; a screen's own options follow the language at once.
 */
export default function AccountLayout() {
  const { t } = useTranslation();
  const colors = useThemeColors();

  return (
    <Stack screenLayout={offlineScreenLayout} screenOptions={stackHeaderOptions(colors)}>
      <Stack.Screen
        name="index"
        options={{
          title: t("tabAccount"),
          headerShown: false,
        }}
      />
      <Stack.Screen
        name="profile"
        options={{ title: t("profileTitle"), headerBackTitle: t("tabAccount") }}
      />
      <Stack.Screen
        name="settings"
        options={{ title: t("settingsTitle"), headerBackTitle: t("tabAccount") }}
      />
      <Stack.Screen
        name="notification-settings"
        options={{ title: t("notificationSettingsTitle"), headerBackTitle: t("settingsTitle") }}
      />
    </Stack>
  );
}
