import { Stack } from "expo-router";
import { Platform } from "react-native";

import { sheetScreenOptions } from "@/components/kazan/BottomSheet";
import { stackHeaderOptions } from "@/components/kazan/stackHeader";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import { useTranslation } from "@/i18n";

// A link straight into a thread or a channel still has the Chat list under
// it, so Back leads somewhere.
export const unstable_settings = { initialRouteName: "index" };

/**
 * The Chat tab: ChatHome with the channel, daily updates, pull requests,
 * threads list, thread and search screens pushed over it, the channel switcher, message
 * actions, members, pins, new thread, new channel, change channel, manage members,
 * edit thread, daily update filter, update form and Not posted sheets, the pull request filter sheet, the
 * full-screen Share pull request form with its reviewer picker, and the
 * full-screen file viewer. The realtime session and the
 * channel list signals live in the tabs layout above it, which also hides
 * the tab bar on the thread and search screens. ChatHome draws its own title
 * row (TabRootHeader), like every tab root, so it hides the native bar; its
 * title still names the Back button of the screens pushed over it.
 *
 * A channel and a thread go back only from the screen's edge: on iOS 26 a
 * Back swipe would otherwise start anywhere in the content, where a swipe
 * to the right answers a message.
 */
export default function ChatLayout() {
  const { t } = useTranslation();
  const colors = useThemeColors();

  return (
    <Stack screenOptions={stackHeaderOptions(colors)}>
      <Stack.Screen
        name="index"
        options={{
          title: t("tabChat"),
          headerShown: false,
        }}
      />
      <Stack.Screen
        name="channel/[channelId]"
        options={{ title: "", fullScreenGestureEnabled: false }}
      />
      <Stack.Screen name="updates" options={{ title: t("chatDailyUpdates") }} />
      <Stack.Screen name="updates-filter" options={sheetScreenOptions("medium")} />
      <Stack.Screen name="new-update" options={sheetScreenOptions("large")} />
      <Stack.Screen name="not-posted" options={sheetScreenOptions("medium")} />
      <Stack.Screen name="pull-requests" options={{ title: t("chatPullRequests") }} />
      <Stack.Screen name="pr-filter" options={sheetScreenOptions("medium")} />
      <Stack.Screen
        name="share-pr"
        options={{
          title: t("prFormCreateTitle"),
          presentation: "fullScreenModal",
          // Closing goes through Cancel, which asks before unsaved input is lost.
          gestureEnabled: false,
        }}
      />
      <Stack.Screen name="share-pr-reviewers" options={sheetScreenOptions("medium")} />
      <Stack.Screen name="threads" options={{ title: t("chatThreadsTitle") }} />
      <Stack.Screen
        name="thread/[threadId]"
        options={{ title: "", fullScreenGestureEnabled: false }}
      />
      <Stack.Screen
        name="search"
        options={{ title: t("chatSearchTitle"), headerShown: Platform.OS === "ios" }}
      />
      <Stack.Screen name="channels" options={sheetScreenOptions("large")} />
      <Stack.Screen name="message-actions" options={sheetScreenOptions("large")} />
      <Stack.Screen name="members" options={sheetScreenOptions("large")} />
      <Stack.Screen name="pins" options={sheetScreenOptions("medium")} />
      <Stack.Screen name="new-thread" options={sheetScreenOptions("medium")} />
      <Stack.Screen name="new-channel" options={sheetScreenOptions("large")} />
      <Stack.Screen name="edit-channel" options={sheetScreenOptions("large")} />
      <Stack.Screen name="channel-members" options={sheetScreenOptions("large")} />
      <Stack.Screen name="edit-thread" options={sheetScreenOptions("medium")} />
      <Stack.Screen
        name="image/[attachmentId]"
        options={{
          headerShown: false,
          presentation: "fullScreenModal",
          animation: "fade",
          contentStyle: { backgroundColor: "black" },
        }}
      />
    </Stack>
  );
}
