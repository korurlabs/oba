import * as React from "react";
import { BackHandler, Platform } from "react-native";

/**
 * Android's Back inside a sheet that shows a sub-view in place of its first
 * view: Back returns to the first view instead of closing the sheet, as the
 * sheet's own Back button does. `onBack` is null while the first view shows,
 * so Back closes the sheet as usual. While `blocked`, as during a request the
 * sub-view sent, Back does nothing, like the disabled Back button.
 */
export function useSubviewHardwareBack(onBack: (() => void) | null, blocked: boolean): void {
  React.useEffect(() => {
    if (Platform.OS !== "android" || !onBack) return;
    const subscription = BackHandler.addEventListener("hardwareBackPress", () => {
      if (!blocked) onBack();
      return true;
    });
    return () => subscription.remove();
  }, [onBack, blocked]);
}
