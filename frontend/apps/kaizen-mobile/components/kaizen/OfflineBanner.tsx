import type { NativeStackNavigationOptions } from "@react-navigation/native-stack";
import { WifiOff } from "lucide-react-native";
import * as React from "react";
import { View } from "react-native";

import { offlineBannerPlacement } from "@/components/kaizen/offlineBannerModel";
import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";
import { useIsOnline } from "@/hooks/useIsOnline";
import { useTranslation } from "@/i18n";

/**
 * The strip under the navigation bar while the device has no network: the
 * screen keeps showing what it loaded last, and the strip says so. It renders
 * nothing while online. Chat screens draw their own connection strip, which
 * also covers a missing network.
 */
export function OfflineBanner() {
  const { t } = useTranslation();
  const online: boolean = useIsOnline();
  if (online) return null;

  return (
    <View
      accessibilityRole="alert"
      accessibilityLiveRegion="polite"
      className="flex-row items-center gap-2 border-b border-border bg-muted px-4 py-2"
    >
      <Icon as={WifiOff} size={16} className="text-foreground" />
      <Text variant="footnote" className="flex-1 text-foreground">
        {t("offlineShowingCached")}
      </Text>
    </View>
  );
}

type OfflineScreenLayoutProps = {
  options: NativeStackNavigationOptions;
  children: React.ReactElement;
};

/**
 * Puts the offline strip above a pushed screen's content, under its native
 * bar. The strip is a sibling of the screen in a fragment, never a wrapper
 * around it, so going offline or back online does not remount the screen
 * and lose its scroll position or draft. Screens that draw their own title
 * row and sheets get no strip here: a tab root mounts the strip under its
 * title row itself, and a sheet stays the clean surface it was opened as.
 */
function OfflineScreenLayout({ options, children }: OfflineScreenLayoutProps) {
  if (offlineBannerPlacement(options) === "none") return children;
  return (
    <>
      <OfflineBanner />
      {children}
    </>
  );
}

/**
 * The `screenLayout` of a stack whose pushed screens show the offline strip.
 * React Navigation calls it as a function, so the hook lives in the
 * component it returns.
 */
export function offlineScreenLayout({
  options,
  children,
}: OfflineScreenLayoutProps): React.ReactElement {
  return <OfflineScreenLayout options={options}>{children}</OfflineScreenLayout>;
}
