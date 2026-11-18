import "../global.css";

import { useEffect } from "react";
import { GestureHandlerRootView } from "react-native-gesture-handler";
import { SafeAreaProvider } from "react-native-safe-area-context";
import { Stack } from "expo-router";
import { ThemeProvider } from "@react-navigation/native";
import { PortalHost } from "@rn-primitives/portal";
import { useColorScheme } from "nativewind";
import { I18nextProvider } from "react-i18next";
import { QueryClientProvider } from "@tanstack/react-query";
import { useFonts } from "expo-font";
import * as SplashScreen from "expo-splash-screen";

import { AppNotices } from "@/components/auth/AppNotices";
import { ToastProvider } from "@/components/ui/toast";
import { AuthProvider } from "@/contexts/AuthContext";
import { applyStoredAppearance } from "@/hooks/useAppearance";
import i18n from "@/i18n";
import { FONT_MAP } from "@/lib/fonts";
import { listenForPushActions } from "@/lib/pushActions";
import { installNativeQueryManagers, queryClient } from "@/lib/queryClient";
import { statusBarStyleFor } from "@/lib/statusBar";
import { NAV_THEME } from "@/lib/theme";

// A render error below shows Expo Router's recovery screen instead of a blank app.
export { ErrorBoundary } from "expo-router";

// Reply and Mark as read run from a notification without a signed-in screen,
// so their listener starts with the app, before any screen mounts.
listenForPushActions();

// The stored light, dark or system choice is set before the first frame, so
// the app never starts in the device scheme and then flips.
applyStoredAppearance();

// Hold the native splash until the fonts are ready, so the first frame is set
// in Inter. A failure to hold is logged; the app still starts.
SplashScreen.preventAutoHideAsync().catch((error: unknown) => {
  console.warn(
    "[layout] preventAutoHideAsync failed",
    error instanceof Error ? error.message : error
  );
});

/**
 * Root providers, outermost first. The order matters: a provider that reads
 * another's context sits inside it.
 *
 * - GestureHandlerRootView and SafeAreaProvider give every screen its gesture
 *   root and insets.
 * - ThemeProvider gives the navigators the colours of the current scheme.
 *   The stack sets the status bar of the same scheme as a screen option:
 *   iOS takes the status bar from the screen on top, so a nested screen
 *   without its own style shows this one, and a black image viewer sets its
 *   own light one.
 * - I18nextProvider sits above data, so loading and error copy is translated.
 * - QueryClientProvider wraps everything that fetches, including AuthProvider,
 *   which goes directly inside it because sign-in and sign-out are mutations
 *   and both clear the query cache.
 * - AuthProvider gives every screen the session, sign-in and sign-out. The
 *   `(auth)` and `(app)` group layouts redirect on its status.
 * - ToastProvider follows the Stack so toasts draw above every screen;
 *   AppNotices feeds it failed requests.
 * - PortalHost is the last child, so sheets and menus that portal into it draw
 *   above the stack.
 */
export default function RootLayout() {
  const { colorScheme } = useColorScheme();
  const [fontsLoaded, fontError] = useFonts(FONT_MAP);

  // Refetch on foreground and on reconnect; React Native has neither a window
  // focus event nor navigator.onLine.
  useEffect(() => installNativeQueryManagers(), []);

  // A font that fails to load is not fatal (the system font is used), but it is
  // logged.
  useEffect(() => {
    if (fontError) {
      console.warn("[layout] font load failed", fontError.message);
    }
  }, [fontError]);

  useEffect(() => {
    if (fontsLoaded || fontError) {
      SplashScreen.hideAsync().catch((error: unknown) => {
        console.warn("[layout] hideAsync failed", error instanceof Error ? error.message : error);
      });
    }
  }, [fontsLoaded, fontError]);

  if (!fontsLoaded && !fontError) return null;

  return (
    <GestureHandlerRootView style={{ flex: 1 }}>
      <SafeAreaProvider>
        <ThemeProvider value={NAV_THEME[colorScheme ?? "light"]}>
          <I18nextProvider i18n={i18n}>
            <QueryClientProvider client={queryClient}>
              <AuthProvider>
                <Stack
                  screenOptions={{
                    headerShown: false,
                    statusBarStyle: statusBarStyleFor(colorScheme),
                  }}
                />
                <ToastProvider />
                <AppNotices />
                <PortalHost />
              </AuthProvider>
            </QueryClientProvider>
          </I18nextProvider>
        </ThemeProvider>
      </SafeAreaProvider>
    </GestureHandlerRootView>
  );
}
