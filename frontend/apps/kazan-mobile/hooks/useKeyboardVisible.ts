import * as React from "react";
import { Keyboard, Platform } from "react-native";

/**
 * Whether the software keyboard is open. A bar pinned above the keyboard uses
 * it to drop the home-indicator inset, which the keyboard already covers.
 * iOS reports the change as the keyboard starts to move, Android once it has.
 */
export function useKeyboardVisible(): boolean {
  const [visible, setVisible] = React.useState<boolean>(() => Keyboard.isVisible());

  React.useEffect(() => {
    const showEvent = Platform.OS === "ios" ? "keyboardWillShow" : "keyboardDidShow";
    const hideEvent = Platform.OS === "ios" ? "keyboardWillHide" : "keyboardDidHide";
    const subscriptions = [
      Keyboard.addListener(showEvent, () => setVisible(true)),
      Keyboard.addListener(hideEvent, () => setVisible(false)),
    ];
    return () => subscriptions.forEach((subscription) => subscription.remove());
  }, []);

  return visible;
}
