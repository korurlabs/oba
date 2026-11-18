import * as Notifications from "expo-notifications";
import * as TaskManager from "expo-task-manager";
import { Platform } from "react-native";

import { withdrawConversationNotifications } from "@/lib/push";
import { runPushActionOnce } from "@/lib/pushActions";
import { backgroundPushStep, type BackgroundPushStep } from "@/lib/pushModel";

/*
 * The background notification task. When a conversation is read on the web
 * or on another phone, the server sends this phone a silent read signal; the
 * task removes that conversation's notifications from the shade even while
 * the app is in the background. On Android the task also receives Reply and
 * Mark as read chosen while the app was not open.
 *
 * Best effort by nature: iOS throttles silent pushes and may not wake the
 * app, so a read notification can stay until the user clears it. Unread
 * counts in the app stay right either way.
 */

/** The task name the system wakes the app with; it must not change between releases. */
export const PUSH_BACKGROUND_TASK = "kaizen-push-background";

function messageOf(error: unknown): unknown {
  return error instanceof Error ? error.message : error;
}

async function runBackgroundPush(
  payload: Notifications.NotificationTaskPayload
): Promise<Notifications.BackgroundNotificationTaskResult> {
  const step: BackgroundPushStep = backgroundPushStep(payload);
  if (step.step === "action") {
    await runPushActionOnce(payload as Notifications.NotificationResponse);
    return Notifications.BackgroundNotificationTaskResult.NewData;
  }
  if (step.step === "withdraw") {
    await withdrawConversationNotifications(step.conversation);
    return Notifications.BackgroundNotificationTaskResult.NewData;
  }
  return Notifications.BackgroundNotificationTaskResult.NoData;
}

/**
 * Defines the task and asks the system to run it for remote notifications.
 * Runs when this module loads, which the app entry (`index.ts`) makes happen
 * before expo-router: Android may start the JavaScript headless only to run
 * the task, without rendering any layout. A refused registration (a build
 * without the remote-notification background mode, such as Expo Go) is
 * logged; pushes still arrive, read ones are only not withdrawn in the
 * background.
 */
function registerPushBackgroundTask(): void {
  if (Platform.OS === "web") return;
  if (!TaskManager.isTaskDefined(PUSH_BACKGROUND_TASK)) {
    TaskManager.defineTask<Notifications.NotificationTaskPayload>(
      PUSH_BACKGROUND_TASK,
      async ({ data, error }) => {
        if (error) {
          console.warn("[push] the background notification task failed", error.message);
          return Notifications.BackgroundNotificationTaskResult.Failed;
        }
        try {
          return await runBackgroundPush(data);
        } catch (taskError: unknown) {
          console.warn("[push] handling a background notification failed", messageOf(taskError));
          return Notifications.BackgroundNotificationTaskResult.Failed;
        }
      }
    );
  }
  Notifications.registerTaskAsync(PUSH_BACKGROUND_TASK).catch((error: unknown) => {
    console.warn("[push] registering the background notification task failed", messageOf(error));
  });
}

registerPushBackgroundTask();
