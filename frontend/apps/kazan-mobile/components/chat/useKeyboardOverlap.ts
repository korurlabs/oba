import * as React from "react";
import {
  Keyboard,
  LayoutAnimation,
  Platform,
  type KeyboardEvent,
  type LayoutChangeEvent,
  type View,
} from "react-native";

/**
 * How far the keyboard covers a view, from the view's bottom edge in window
 * coordinates and the keyboard's top. A chat screen sits inside a tab and
 * under a header, so a fixed offset would be wrong on one platform or the
 * other; measuring the view keeps the composer just above the keyboard on
 * both. Returns the overlap, a ref and the `onLayout` for the measured view.
 */
export function useKeyboardOverlap() {
  const viewRef = React.useRef<View>(null);
  const bottomRef = React.useRef<number | null>(null);
  const keyboardTopRef = React.useRef<number | null>(null);
  const [overlap, setOverlap] = React.useState<number>(0);

  const update = React.useCallback(() => {
    const bottom: number | null = bottomRef.current;
    const keyboardTop: number | null = keyboardTopRef.current;
    setOverlap(bottom !== null && keyboardTop !== null ? Math.max(0, bottom - keyboardTop) : 0);
  }, []);

  const onLayout = React.useCallback(
    (_event: LayoutChangeEvent) => {
      viewRef.current?.measureInWindow((_x: number, y: number, _width: number, height: number) => {
        bottomRef.current = y + height;
        update();
      });
    },
    [update]
  );

  React.useEffect(() => {
    const onShow = (event: KeyboardEvent): void => {
      if (Platform.OS === "ios" && event.duration > 0) {
        LayoutAnimation.configureNext({
          duration: event.duration,
          update: { duration: event.duration, type: LayoutAnimation.Types.keyboard },
        });
      }
      keyboardTopRef.current = event.endCoordinates.screenY;
      update();
    };
    const onHide = (event: KeyboardEvent): void => {
      if (Platform.OS === "ios" && event.duration > 0) {
        LayoutAnimation.configureNext({
          duration: event.duration,
          update: { duration: event.duration, type: LayoutAnimation.Types.keyboard },
        });
      }
      keyboardTopRef.current = null;
      update();
    };
    const showEvent = Platform.OS === "ios" ? "keyboardWillChangeFrame" : "keyboardDidShow";
    const hideEvent = Platform.OS === "ios" ? "keyboardWillHide" : "keyboardDidHide";
    const subscriptions = [
      Keyboard.addListener(showEvent, onShow),
      Keyboard.addListener(hideEvent, onHide),
    ];
    return () => subscriptions.forEach((subscription) => subscription.remove());
  }, [update]);

  return { overlap, viewRef, onLayout };
}
