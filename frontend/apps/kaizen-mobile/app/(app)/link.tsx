import { useLocalSearchParams, useRouter } from "expo-router";
import * as React from "react";
import { ActivityIndicator, View } from "react-native";

import { toast } from "@/components/ui/toast";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import { hostOf } from "@/components/ticket/ticketModel";
import { useOpenKaizenLink } from "@/hooks/useOpenKaizenLink";
import { useTranslation } from "@/i18n";
import { ENV } from "@/lib/env";
import { parseKaizenLink, type KaizenLinkTarget } from "@/lib/kaizenLinks";

/**
 * Opens a Kaizen web link the system handed to the app whose screen needs a
 * lookup first: a board by its slug, a channel by its slug or purpose, a
 * message or a thread. It leaves itself at once and the lookup pushes the
 * screen over the tabs, as a tapped notification does. A link
 * that cannot be opened is toasted, and the tabs stay.
 */
export default function LinkScreen() {
  const { t } = useTranslation();
  const router = useRouter();
  const colors = useThemeColors();
  const { url } = useLocalSearchParams<{ url?: string }>();
  const { openTarget } = useOpenKaizenLink();
  const handled = React.useRef<string | null>(null);

  React.useEffect(() => {
    const href: string = url ?? "";
    if (handled.current === href) return;
    handled.current = href;
    const leave = (): void => {
      if (router.canGoBack()) router.back();
      else router.replace("/home");
    };
    const target: KaizenLinkTarget | null = parseKaizenLink(href, hostOf(ENV.kaizenWebUrl));
    if (!target) {
      console.warn("[links] the link screen got a link it cannot open", href);
      toast.error(t("chatOpenLinkFailed"));
      leave();
      return;
    }
    // This screen leaves at once, so the target the lookup pushes lands on the tabs, not on it.
    leave();
    openTarget(target).catch((error: unknown) => {
      console.warn(
        "[links] opening a system link failed",
        href,
        error instanceof Error ? error.message : error
      );
      toast.error(t("chatOpenLinkFailed"));
    });
  }, [url, router, openTarget, t]);

  return (
    <View className="flex-1 items-center justify-center bg-screen">
      <ActivityIndicator color={colors.mutedForeground} />
    </View>
  );
}
