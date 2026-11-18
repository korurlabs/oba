import * as Haptics from "expo-haptics";

/*
 * The app's haptic feedback. A haptic that fails is logged and the action goes
 * on, because every action works without it.
 */

function logFailure(error: unknown): void {
  console.warn("[haptics] feedback failed", error instanceof Error ? error.message : error);
}

/** The tick of a changed choice: a segment, a chip, a picked option. */
export function hapticSelection(): void {
  Haptics.selectionAsync().catch(logFailure);
}

/** The outcome of an action: success, a warning such as a failed move, or an error. */
export function hapticNotification(kind: "success" | "warning" | "error"): void {
  const type: Haptics.NotificationFeedbackType =
    kind === "success"
      ? Haptics.NotificationFeedbackType.Success
      : kind === "warning"
        ? Haptics.NotificationFeedbackType.Warning
        : Haptics.NotificationFeedbackType.Error;
  Haptics.notificationAsync(type).catch(logFailure);
}
