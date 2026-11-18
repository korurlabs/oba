import { Redirect, Stack } from "expo-router";
import { useColorScheme } from "nativewind";
import type * as React from "react";

import { OrganizationGate } from "@/components/auth/OrganizationGate";
import { SessionLoading } from "@/components/auth/SessionLoading";
import { sheetScreenOptions } from "@/components/kaizen/BottomSheet";
import { offlineScreenLayout } from "@/components/kaizen/OfflineBanner";
import { stackHeaderOptions } from "@/components/kaizen/stackHeader";
import { PushHost } from "@/components/push/PushHost";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import { useAuth } from "@/contexts/AuthContext";
import { IMAGE_VIEWER_STATUS_BAR_STYLE, statusBarStyleFor } from "@/lib/statusBar";

// The tabs sit under every screen of this stack, also when a link or a
// restored URL opens a ticket, a sheet or Share straight away. Without them
// that screen would be the first of the stack, which iOS cannot present as a
// sheet or modal: it would show as a bare page whose header runs under the
// status bar, and Back would lead nowhere.
export const unstable_settings = { initialRouteName: "(tabs)" };

type AppScreenLayoutArgs = Parameters<typeof offlineScreenLayout>[0] & {
  route: { name: string };
};

/**
 * The ticket screens show the offline strip under their bar. The tabs host
 * their own stacks, and the shared chat channel draws its own connection
 * strip, so neither gets a second one here.
 */
function appScreenLayout(args: AppScreenLayoutArgs): React.ReactElement {
  return args.route.name.startsWith("ticket/") ? offlineScreenLayout(args) : args.children;
}

/**
 * The signed-in app. Without a session every route here sends the user to Sign
 * in, also when Keycloak ends the session mid-use; with one, the organization
 * gate makes sure an organization is active before any screen loads its data.
 *
 * The ticket screen sits in this stack, above the tabs, so a ticket opens the
 * same way from every tab and from a link, and covers the tab bar so its
 * comment composer stays pinned to the bottom. New ticket is the same screen
 * without a key, pushed the same way from a board, My Tickets and a parent
 * ticket. The field pickers of both are sheets of this stack, and a ticket's
 * images open over it in the full-screen viewer the chat files use.
 *
 * Share to chat sits here too: its channel picker is a sheet over the
 * ticket, and the chosen channel replaces it, so Back from the channel
 * returns to the ticket. That channel goes back only from the screen's edge,
 * because a swipe to the right in its content answers a message.
 *
 * Push lives inside the gate, because a tapped notification opens a screen
 * of an organization and registration needs the signed-in user. The
 * notification pre-prompt covers the whole app once, after the first sign-in.
 *
 * Every screen here names the status bar of the current scheme, because iOS
 * asks a full-screen modal such as the pre-prompt for its own; the image
 * viewer is black in both themes and keeps a light one.
 */
export default function AppLayout() {
  const { status } = useAuth();
  const colors = useThemeColors();
  const { colorScheme } = useColorScheme();
  const ticketScreenOptions = {
    headerShown: true,
    title: "",
    // The screen under a ticket is the whole tab bar, whose route has no title
    // of its own, so the Back button shows the chevron alone instead of
    // "(tabs)".
    headerBackButtonDisplayMode: "minimal" as const,
    ...stackHeaderOptions(colors),
  };

  if (status === "loading") return <SessionLoading />;
  if (status === "signed-out") return <Redirect href="/sign-in" />;
  return (
    <OrganizationGate>
      <Stack
        screenLayout={appScreenLayout}
        screenOptions={{ headerShown: false, statusBarStyle: statusBarStyleFor(colorScheme) }}
      >
        <Stack.Screen name="ticket/[ticketKey]/index" options={ticketScreenOptions} />
        <Stack.Screen name="ticket/[ticketKey]/assignees" options={sheetScreenOptions("medium")} />
        <Stack.Screen name="ticket/[ticketKey]/labels" options={sheetScreenOptions("medium")} />
        <Stack.Screen name="ticket/[ticketKey]/description" options={sheetScreenOptions("large")} />
        <Stack.Screen name="ticket/[ticketKey]/move-board" options={sheetScreenOptions("large")} />
        <Stack.Screen
          name="ticket/[ticketKey]/move-to-sprint"
          options={sheetScreenOptions("large")}
        />
        <Stack.Screen
          name="ticket/[ticketKey]/image"
          options={{
            headerShown: false,
            presentation: "fullScreenModal",
            animation: "fade",
            contentStyle: { backgroundColor: "black" },
            statusBarStyle: IMAGE_VIEWER_STATUS_BAR_STYLE,
          }}
        />
        <Stack.Screen name="ticket/choose" options={sheetScreenOptions("medium")} />
        <Stack.Screen name="ticket/close-incident" options={sheetScreenOptions("large")} />
        <Stack.Screen name="ticket/new/index" options={ticketScreenOptions} />
        <Stack.Screen name="ticket/new/assignees" options={sheetScreenOptions("medium")} />
        <Stack.Screen name="ticket/new/labels" options={sheetScreenOptions("medium")} />
        <Stack.Screen name="ticket/new/description" options={sheetScreenOptions("large")} />
        <Stack.Screen name="share/ticket" options={sheetScreenOptions("large")} />
        <Stack.Screen
          name="share/channel/[channelId]"
          options={{ ...ticketScreenOptions, fullScreenGestureEnabled: false }}
        />
        <Stack.Screen name="link" options={{ animation: "none" }} />
        <Stack.Screen
          name="notification-permission"
          options={{ presentation: "fullScreenModal", gestureEnabled: false }}
        />
      </Stack>
      <PushHost />
    </OrganizationGate>
  );
}
