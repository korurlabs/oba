import { Stack } from "expo-router";

import { sheetScreenOptions } from "@/components/kaizen/BottomSheet";
import { offlineScreenLayout } from "@/components/kaizen/OfflineBanner";
import { stackHeaderOptions } from "@/components/kaizen/stackHeader";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import { useTranslation } from "@/i18n";

// A link straight into Links or an info sheet still has the dashboard under
// it, so the sheet is presented as a sheet and closing it leads somewhere.
export const unstable_settings = { initialRouteName: "index" };

/**
 * The Home tab: the dashboard, My Tickets pushed over it, and the Links and
 * info sheets. The dashboard draws its own title row (TabRootHeader), like
 * every tab root, so it hides the native bar; its title still names the Back
 * button of the screens pushed over it.
 */
export default function HomeLayout() {
  const { t } = useTranslation();
  const colors = useThemeColors();

  return (
    <Stack screenLayout={offlineScreenLayout} screenOptions={stackHeaderOptions(colors)}>
      <Stack.Screen
        name="index"
        options={{
          title: t("tabHome"),
          headerShown: false,
        }}
      />
      <Stack.Screen name="my-tickets" options={{ title: t("homeMyTickets") }} />
      <Stack.Screen name="links" options={sheetScreenOptions("large")} />
      <Stack.Screen name="info/[topic]" options={sheetScreenOptions("large")} />
    </Stack>
  );
}
