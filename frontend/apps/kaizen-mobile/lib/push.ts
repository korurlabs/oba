import * as Application from "expo-application";
import Constants from "expo-constants";
import * as Notifications from "expo-notifications";
import * as SecureStore from "expo-secure-store";
import { Linking, Platform } from "react-native";

import { translate } from "@/i18n";
import {
  CHAT_MESSAGE_CATEGORY,
  foregroundDecision,
  parsePushData,
  pushInConversation,
  PUSH_ACTION,
  PUSH_CHANNEL,
  type ForegroundDecision,
  type OpenConversation,
} from "@/lib/pushModel";
import type { PushData } from "@/lib/schemas/push";

/*
 * Push notifications on the phone: the Android channels and the chat
 * category with its actions, what a push does while the app is open, the
 * permission, the Expo push token, and removing a conversation's
 * notifications once it is read. The server picks the channel, the category
 * and the text; the app only declares them and reacts.
 */

function messageOf(error: unknown): unknown {
  return error instanceof Error ? error.message : error;
}

// ── The open conversation ───────────────────────────────────────────

let openConversation: OpenConversation | null = null;

/**
 * Records the chat conversation on screen, so a push for it is not shown
 * again on top of the message the user is reading. The push host keeps it in
 * step with the route; null on any other screen.
 */
export function setOpenConversation(conversation: OpenConversation | null): void {
  openConversation = conversation;
}

export function getOpenConversation(): OpenConversation | null {
  return openConversation;
}

/** What a push that arrives now, with the app open, does. */
export function decideForeground(data: PushData): ForegroundDecision {
  return foregroundDecision(data, openConversation);
}

// ── Withdrawal ──────────────────────────────────────────────────────

/**
 * Removes the delivered notifications of a conversation, once it was read on
 * this phone or elsewhere. Best effort: a failure leaves them in the shade,
 * where the user can still clear them, and is logged.
 */
export async function withdrawConversationNotifications(conversation: {
  channelId: string | null;
  threadId: string | null;
}): Promise<void> {
  try {
    const presented: Notifications.Notification[] =
      await Notifications.getPresentedNotificationsAsync();
    const matching: Notifications.Notification[] = presented.filter((notification) => {
      const data: PushData | null = parsePushData(notification.request.content.data);
      return data !== null && pushInConversation(data, conversation);
    });
    await Promise.all(
      matching.map((notification) =>
        Notifications.dismissNotificationAsync(notification.request.identifier)
      )
    );
  } catch (error: unknown) {
    console.warn("[push] removing a read conversation's notifications failed", messageOf(error));
  }
}

/**
 * Removes every delivered notification and the badge count at sign-out:
 * they belong to the user who signed out, and with previews on they show
 * that user's messages to the next person on the phone. A failure is
 * rejected, so sign-out reports it.
 */
export async function clearDeliveredNotifications(): Promise<void> {
  if (Platform.OS === "web") return;
  await Notifications.dismissAllNotificationsAsync();
  const badgeReset: boolean = await Notifications.setBadgeCountAsync(0);
  if (!badgeReset) {
    // Many Android launchers show no count; there is nothing left to reset then.
    console.warn("[push] the app icon badge was not reset; the launcher may not support badges");
  }
}

// ── Setup ───────────────────────────────────────────────────────────

const SILENT_BEHAVIOR: Notifications.NotificationBehavior = {
  shouldShowBanner: false,
  shouldShowList: false,
  shouldPlaySound: false,
  shouldSetBadge: false,
};

/**
 * With the app open, the system banner never shows: the app draws its own
 * (PushBanner), which a tap routes the same way. The notification still goes
 * to the list and updates the badge, unless the user is reading that
 * conversation. A read signal shows nothing and removes the conversation's
 * notifications instead.
 */
async function handleForegroundNotification(
  notification: Notifications.Notification
): Promise<Notifications.NotificationBehavior> {
  const data: PushData | null = parsePushData(notification.request.content.data);
  if (!data) {
    return {
      shouldShowBanner: true,
      shouldShowList: true,
      shouldPlaySound: true,
      shouldSetBadge: true,
    };
  }
  const decision: ForegroundDecision = decideForeground(data);
  if (decision === "withdraw") {
    await withdrawConversationNotifications(data);
    return SILENT_BEHAVIOR;
  }
  if (decision === "silent") return SILENT_BEHAVIOR;
  return {
    shouldShowBanner: false,
    shouldShowList: true,
    shouldPlaySound: false,
    shouldSetBadge: true,
  };
}

/**
 * The three Android channels the server sends to. A user tunes each one in
 * the system settings; urgent incidents use high importance, so they show as
 * a heads-up. Re-creating a channel only updates its name and description.
 */
async function createAndroidChannels(): Promise<void> {
  await Promise.all([
    Notifications.setNotificationChannelAsync(PUSH_CHANNEL.mentionsReplies, {
      name: translate("pushChannelMentionsReplies"),
      description: translate("pushChannelMentionsRepliesDescription"),
      importance: Notifications.AndroidImportance.DEFAULT,
    }),
    Notifications.setNotificationChannelAsync(PUSH_CHANNEL.everyoneHere, {
      name: translate("pushChannelEveryoneHere"),
      description: translate("pushChannelEveryoneHereDescription"),
      importance: Notifications.AndroidImportance.DEFAULT,
    }),
    Notifications.setNotificationChannelAsync(PUSH_CHANNEL.urgentIncidents, {
      name: translate("pushChannelUrgentIncidents"),
      description: translate("pushChannelUrgentIncidentsDescription"),
      importance: Notifications.AndroidImportance.HIGH,
    }),
  ]);
}

/**
 * The category of chat pushes: Reply with a text field and Mark as read, both
 * handled without opening the app.
 */
async function createChatCategory(): Promise<void> {
  await Notifications.setNotificationCategoryAsync(CHAT_MESSAGE_CATEGORY, [
    {
      identifier: PUSH_ACTION.reply,
      buttonTitle: translate("pushActionReply"),
      textInput: {
        submitButtonTitle: translate("pushActionReplySend"),
        placeholder: translate("pushActionReplyPlaceholder"),
      },
      options: { opensAppToForeground: false },
    },
    {
      identifier: PUSH_ACTION.markAsRead,
      buttonTitle: translate("pushActionMarkAsRead"),
      options: { opensAppToForeground: false },
    },
  ]);
}

let configured: Promise<void> | null = null;

/**
 * Sets the foreground handler, the Android channels and the chat category.
 * Called once at start, before the token is asked for: Android needs a
 * channel before it can ask for the permission. A failure is logged and
 * rejected; the next call tries again.
 */
export function configurePush(): Promise<void> {
  if (Platform.OS === "web") return Promise.resolve();
  if (configured) return configured;
  Notifications.setNotificationHandler({
    handleNotification: handleForegroundNotification,
    handleError: (notificationId: string, error: Error) => {
      console.warn(
        "[push] deciding how to show a notification failed",
        notificationId,
        error.message
      );
    },
  });
  configured = Promise.all([
    Platform.OS === "android" ? createAndroidChannels() : Promise.resolve(),
    createChatCategory(),
  ])
    .then(() => undefined)
    .catch((error: unknown) => {
      configured = null;
      console.warn("[push] setting up channels and categories failed", messageOf(error));
      throw error;
    });
  return configured;
}

// ── Permission ──────────────────────────────────────────────────────

export interface PushPermission {
  granted: boolean;
  /** Never asked: the system dialog can still be shown. */
  undetermined: boolean;
}

function toPermission(response: Notifications.NotificationPermissionsStatus): PushPermission {
  const iosStatus: Notifications.IosAuthorizationStatus | undefined = response.ios?.status;
  const granted: boolean =
    response.granted ||
    iosStatus === Notifications.IosAuthorizationStatus.PROVISIONAL ||
    iosStatus === Notifications.IosAuthorizationStatus.EPHEMERAL;
  return { granted, undetermined: !granted && response.status === "undetermined" };
}

/** The notification permission as it is now. */
export async function getPushPermission(): Promise<PushPermission> {
  return toPermission(await Notifications.getPermissionsAsync());
}

/**
 * Shows the system dialog (iOS, Android 13 and later). Below Android 13
 * notifications are on by default and this answers granted at once.
 */
export async function requestPushPermission(): Promise<PushPermission> {
  await configurePush();
  return toPermission(
    await Notifications.requestPermissionsAsync({
      ios: { allowAlert: true, allowBadge: true, allowSound: true },
    })
  );
}

/** The Android screen of one app's notification settings, with its channels. */
const ANDROID_APP_NOTIFICATION_SETTINGS = "android.settings.APP_NOTIFICATION_SETTINGS";
const ANDROID_EXTRA_APP_PACKAGE = "android.provider.extra.APP_PACKAGE";

/**
 * Opens the system settings where the user turns Kaizen's notifications on
 * or off: the app's page in iOS Settings; on Android the app's notification
 * settings with the three channels, or the app's info page when that screen
 * cannot be opened. Rejects when neither opens.
 */
export async function openNotificationSettings(): Promise<void> {
  const packageName: string | null = Application.applicationId;
  if (Platform.OS === "android" && packageName) {
    try {
      await Linking.sendIntent(ANDROID_APP_NOTIFICATION_SETTINGS, [
        { key: ANDROID_EXTRA_APP_PACKAGE, value: packageName },
      ]);
      return;
    } catch (error: unknown) {
      // Some Android builds lack the screen; the app info page links to it.
      console.warn("[push] opening the notification settings failed", messageOf(error));
    }
  }
  await Linking.openSettings();
}

/** SecureStore key of "the pre-prompt was answered on this phone". Not secret; SecureStore is the app's only store. */
export const PUSH_PRE_PROMPT_KEY = "kaizen_push_pre_prompt_seen";

/** Whether the pre-prompt was answered on this phone. A failed read counts as answered, so it never loops. */
export function readPrePromptSeen(): boolean {
  if (Platform.OS === "web") return true;
  try {
    return SecureStore.getItem(PUSH_PRE_PROMPT_KEY) === "1";
  } catch (error: unknown) {
    console.warn("[push] reading the pre-prompt state failed", messageOf(error));
    return true;
  }
}

/** Remembers that the pre-prompt was answered, so "Not now" holds until Settings. */
export async function markPrePromptSeen(): Promise<void> {
  await SecureStore.setItemAsync(PUSH_PRE_PROMPT_KEY, "1");
}

// ── Token ───────────────────────────────────────────────────────────

/** The EAS project of this build; the Expo push service issues tokens per project. */
function easProjectId(): string {
  const extra = Constants.expoConfig?.extra as { eas?: { projectId?: string } } | undefined;
  const projectId: string | undefined = Constants.easConfig?.projectId ?? extra?.eas?.projectId;
  if (!projectId) {
    throw new Error(
      "[push] No EAS project id: link the app with `eas init`, which sets extra.eas.projectId"
    );
  }
  return projectId;
}

/** This phone's Expo push token; rejects without permission or an EAS project. */
export async function getExpoPushToken(): Promise<string> {
  await configurePush();
  const token: Notifications.ExpoPushToken = await Notifications.getExpoPushTokenAsync({
    projectId: easProjectId(),
  });
  return token.data;
}

// ── Local notices ───────────────────────────────────────────────────

/**
 * Tells the user that a notification action did not reach the server. It
 * carries the push's data, so a tap opens the conversation to try again.
 */
export async function notifyActionFailed(
  data: PushData,
  titleKey: "pushReplyNotSent" | "pushMarkReadFailed"
): Promise<void> {
  await Notifications.scheduleNotificationAsync({
    content: {
      title: translate(titleKey),
      body: data.conversation ?? translate("pushActionFailedBody"),
      data: { ...data },
    },
    trigger: Platform.OS === "android" ? { channelId: PUSH_CHANNEL.mentionsReplies } : null,
  });
}
