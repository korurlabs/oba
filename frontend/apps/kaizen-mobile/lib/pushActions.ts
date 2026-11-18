import { randomUUID } from "expo-crypto";
import * as Notifications from "expo-notifications";
import { Platform } from "react-native";

import { chatKeys } from "@/hooks/queries/chatCache";
import { api, ORGANIZATION_HEADER } from "@/lib/api";
import { API_ROUTES } from "@/lib/apiRoutes";
import { notifyActionFailed } from "@/lib/push";
import { isChatPushKind, parsePushData, PUSH_ACTION } from "@/lib/pushModel";
import { queryClient } from "@/lib/queryClient";
import {
  CHAT_MESSAGE_MAX_LENGTH,
  type ChatMessageCreateRequest,
  type ChatReadStateRequest,
} from "@/lib/schemas/chat";
import type { PushData } from "@/lib/schemas/push";

/*
 * Reply and Mark as read, run from the notification without opening the app.
 * Each request names the push's organization itself, because the push may
 * belong to another organization than the one the app has open. A failure
 * leaves a local notification that opens the conversation, so the user learns
 * that the reply did not go out.
 */

function messageOf(error: unknown): unknown {
  return error instanceof Error ? error.message : error;
}

/**
 * Posts the reply into the push's conversation as the composer does: the
 * multipart request with the JSON `message` part and no files.
 */
async function postReply(data: PushData, text: string): Promise<void> {
  if (!data.channelId) throw new Error("The push names no channel to reply in");
  const message: ChatMessageCreateRequest = {
    channelId: data.channelId,
    threadId: data.threadId,
    content: text.slice(0, CHAT_MESSAGE_MAX_LENGTH),
    clientNonce: randomUUID(),
  };
  const formData = new FormData();
  formData.append("message", {
    string: JSON.stringify(message),
    type: "application/json",
  } as unknown as Blob);
  await api.post(API_ROUTES.chat.messages, formData, {
    headers: { "Content-Type": "multipart/form-data", [ORGANIZATION_HEADER]: data.organizationId },
    _silentErrors: true,
  });
}

/** Moves the read position of the push's conversation to its message. */
async function markRead(data: PushData): Promise<void> {
  if (!data.channelId || !data.messageId) {
    throw new Error("The push names no message to mark as read");
  }
  const request: ChatReadStateRequest = {
    channelId: data.channelId,
    threadId: data.threadId,
    lastReadMessageId: data.messageId,
  };
  await api.put(API_ROUTES.chat.readState, request, {
    headers: { [ORGANIZATION_HEADER]: data.organizationId },
    _silentErrors: true,
  });
}

/** Lets an open app show the reply and the new unread counts without waiting for realtime. */
function refreshChat(organizationId: string): void {
  queryClient
    .invalidateQueries({ queryKey: chatKeys.all(organizationId) })
    .catch((error: unknown) => {
      console.warn("[push] refreshing chat after a notification action failed", messageOf(error));
    });
}

/** Removes the answered notification; Android keeps it after an action otherwise. */
async function dismiss(identifier: string): Promise<void> {
  try {
    await Notifications.dismissNotificationAsync(identifier);
  } catch (error: unknown) {
    // The action already reached the server; the notification stays until cleared.
    console.warn("[push] removing an answered notification failed", messageOf(error));
  }
}

/** Tells the user a notification action failed; a failure to tell them is logged. */
async function reportFailure(
  data: PushData,
  titleKey: "pushReplyNotSent" | "pushMarkReadFailed"
): Promise<void> {
  try {
    await notifyActionFailed(data, titleKey);
  } catch (error: unknown) {
    console.warn("[push] showing the failed action notice failed", messageOf(error));
  }
}

/**
 * Runs Reply or Mark as read. Returns false for any other response (a tap),
 * which the push host routes instead.
 */
export async function handlePushAction(
  response: Notifications.NotificationResponse
): Promise<boolean> {
  const action: string = response.actionIdentifier;
  if (action !== PUSH_ACTION.reply && action !== PUSH_ACTION.markAsRead) return false;
  const data: PushData | null = parsePushData(response.notification.request.content.data);
  if (!data || !isChatPushKind(data.type)) {
    console.warn("[push] a notification action came without a chat push", action);
    return true;
  }
  const identifier: string = response.notification.request.identifier;

  if (action === PUSH_ACTION.reply) {
    const text: string = (response.userText ?? "").trim();
    if (!text) return true;
    try {
      await postReply(data, text);
    } catch (error: unknown) {
      console.warn("[push] sending a reply from the notification failed", messageOf(error));
      await dismiss(identifier);
      await reportFailure(data, "pushReplyNotSent");
      return true;
    }
    await dismiss(identifier);
    refreshChat(data.organizationId);
    return true;
  }

  try {
    await markRead(data);
  } catch (error: unknown) {
    console.warn(
      "[push] marking a conversation read from the notification failed",
      messageOf(error)
    );
    await reportFailure(data, "pushMarkReadFailed");
    return true;
  }
  await dismiss(identifier);
  refreshChat(data.organizationId);
  return true;
}

const handledResponses = new Set<string>();

function responseKey(response: Notifications.NotificationResponse): string {
  return `${response.notification.request.identifier}:${response.actionIdentifier}`;
}

/**
 * Runs an action once, however many ways it arrives: on Android the same
 * Reply reaches both the response listener and the background task.
 */
export async function runPushActionOnce(
  response: Notifications.NotificationResponse
): Promise<void> {
  const key: string = responseKey(response);
  if (handledResponses.has(key)) return;
  handledResponses.add(key);
  await handlePushAction(response);
}

function runOnce(response: Notifications.NotificationResponse): void {
  runPushActionOnce(response).catch((error: unknown) => {
    console.warn("[push] a notification action failed", messageOf(error));
  });
}

let listening = false;

/**
 * Listens for Reply and Mark as read for the life of the app, outside any
 * screen, so an action works while the app is in the background and before
 * the signed-in screens mount. An action that launched the app is read once
 * at start.
 */
export function listenForPushActions(): void {
  if (listening || Platform.OS === "web") return;
  listening = true;
  Notifications.addNotificationResponseReceivedListener(runOnce);
  const launch: Notifications.NotificationResponse | null =
    Notifications.getLastNotificationResponse();
  if (launch) runOnce(launch);
}
