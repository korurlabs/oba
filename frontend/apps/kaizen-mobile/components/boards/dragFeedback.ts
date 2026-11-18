import * as Haptics from "expo-haptics";
import type { View } from "react-native";

import type { Rect } from "@/components/boards/ticketMoveModel";

/*
 * What the Kanban and list drags share: the window box of a view, and the
 * haptics of a lift and of a new drop target. A haptic that fails is logged
 * and the drag goes on, because the drag works without it.
 */

/** The view's box in window coordinates, or null when it is not mounted. */
export function measure(view: View | null): Promise<Rect | null> {
  return new Promise((resolve) => {
    if (!view) {
      resolve(null);
      return;
    }
    view.measureInWindow((x, y, width, height) => resolve({ x, y, width, height }));
  });
}

/** The bump of a card lifting under the finger. */
export function hapticImpact(): void {
  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium).catch((error: unknown) => {
    console.warn("[boards] haptic failed", error instanceof Error ? error.message : error);
  });
}

/** The tick of a dragged card entering a new drop target. */
export function hapticSelection(): void {
  Haptics.selectionAsync().catch((error: unknown) => {
    console.warn("[boards] haptic failed", error instanceof Error ? error.message : error);
  });
}
