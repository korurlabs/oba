import { useFocusEffect } from "expo-router";
import { CircleAlert, CircleCheck, Info, TriangleAlert } from "lucide-react-native";
import type { LucideIcon } from "lucide-react-native";
import * as React from "react";
import { AccessibilityInfo, Platform, Pressable, View } from "react-native";
import Animated, { FadeInDown, FadeInUp, FadeOutDown, FadeOutUp } from "react-native-reanimated";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { FullWindowOverlay } from "react-native-screens";

import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";
import {
  addToast,
  defaultToastDuration,
  type ToastAction,
  type ToastRecord,
  type ToastTone,
} from "@/components/ui/toast-stack";
import { useShadow } from "@/components/ui/use-shadow";
import { useTranslation } from "@/i18n";
import { hapticNotification } from "@/lib/haptics";
import { cn } from "@/lib/utils";

/*
 * Transient feedback after an action ("Ticket created", "Couldn't save"). On
 * iOS it is a pill at the top on `popover` with a tone icon; on Android it is
 * the Material snackbar at the bottom on `foreground`, inverted text and no
 * icon. Call `toast()` from anywhere, React or not; <ToastProvider> renders the
 * stack and must be mounted once in the root layout, after the navigator.
 */

type ToastOptions = {
  /** A second line under the message, such as the server's reason for a failure. */
  description?: string;
  action?: ToastAction;
  /** Auto-dismiss delay; 0 keeps it until tapped. Default 4 s, 6 s with an action. */
  durationMs?: number;
};

type ToastEvent = { kind: "add"; toast: ToastRecord } | { kind: "dismiss"; id: string };

const listeners = new Set<(event: ToastEvent) => void>();
let counter = 0;

function emit(event: ToastEvent): void {
  for (const listener of listeners) listener(event);
}

function show(tone: ToastTone, message: string, options?: ToastOptions): string {
  const id = `toast-${++counter}`;
  if (listeners.size === 0) {
    // Without a mounted provider the message would vanish unseen.
    console.warn("[toast] no ToastProvider mounted; message not shown:", message);
  }
  emit({
    kind: "add",
    toast: {
      id,
      message,
      description: options?.description,
      tone,
      action: options?.action,
      durationMs: options?.durationMs ?? defaultToastDuration(!!options?.action),
    },
  });
  return id;
}

/**
 * Show a toast and return its id. `toast(message)` is an info toast; the tone
 * methods set the icon on iOS. Android shows every tone the same way.
 */
const toast = Object.assign(
  (message: string, options?: ToastOptions) => show("info", message, options),
  {
    success: (message: string, options?: ToastOptions) => show("success", message, options),
    error: (message: string, options?: ToastOptions) => show("error", message, options),
    info: (message: string, options?: ToastOptions) => show("info", message, options),
    warning: (message: string, options?: ToastOptions) => show("warning", message, options),
  }
);

/** Remove a toast before its timer ends, such as when its screen closes. */
function dismissToast(id: string): void {
  emit({ kind: "dismiss", id });
}

/*
 * Extra lift for the Android snackbar, raised by screens that draw something
 * at the bottom edge (a FAB) while they are focused. Each focused screen
 * registers its lift under its own key; the snackbar rises by the largest one,
 * so a screen that loses focus while another gains it never leaves a stale value.
 */
const androidToastLifts = new Map<symbol, number>();
const androidToastLiftListeners = new Set<() => void>();

function setAndroidToastLift(key: symbol, lift: number | null): void {
  if (lift === null) androidToastLifts.delete(key);
  else androidToastLifts.set(key, lift);
  for (const listener of androidToastLiftListeners) listener();
}

function subscribeAndroidToastLift(listener: () => void): () => void {
  androidToastLiftListeners.add(listener);
  return () => {
    androidToastLiftListeners.delete(listener);
  };
}

function currentAndroidToastLift(): number {
  let highest = 0;
  for (const lift of androidToastLifts.values()) highest = Math.max(highest, lift);
  return highest;
}

/**
 * Raises the Android snackbar by `lift` while the calling screen is focused,
 * so it sits above that screen's FAB instead of covering it. iOS shows toasts
 * at the top, so the lift has no effect there.
 */
function useAndroidToastLift(lift: number): void {
  const key = React.useRef<symbol>(Symbol("androidToastLift")).current;
  useFocusEffect(
    React.useCallback(() => {
      if (Platform.OS !== "android") return undefined;
      setAndroidToastLift(key, lift);
      return () => setAndroidToastLift(key, null);
    }, [key, lift])
  );
}

const TONE_ICON: Record<ToastTone, { icon: LucideIcon; className: string }> = {
  success: { icon: CircleCheck, className: "text-success" },
  error: { icon: CircleAlert, className: "text-destructive" },
  info: { icon: Info, className: "text-link" },
  warning: { icon: TriangleAlert, className: "text-warning" },
};

type ToastViewProps = {
  toast: ToastRecord;
  onDismiss: (id: string) => void;
};

/** One toast in the platform's form. Tapping it dismisses it. */
function ToastView({ toast: item, onDismiss }: ToastViewProps) {
  const { t } = useTranslation();
  const shadow = useShadow(Platform.OS === "ios" ? "dialog" : null);
  const isIos = Platform.OS === "ios";
  const tone = TONE_ICON[item.tone];

  const runAction = () => {
    onDismiss(item.id);
    item.action?.onPress();
  };

  return (
    <Animated.View
      entering={isIos ? FadeInUp : FadeInDown}
      exiting={isIos ? FadeOutUp : FadeOutDown}
      style={shadow}
      className={isIos ? "self-center rounded-full" : "self-stretch rounded-xs"}
    >
      <Pressable
        accessibilityRole="alert"
        accessibilityHint={t("kitToastDismissHint")}
        accessibilityLiveRegion="polite"
        onPress={() => onDismiss(item.id)}
        className={cn(
          "flex-row items-center",
          isIos
            ? "min-h-11 max-w-[360px] gap-2 rounded-full bg-popover px-4 py-2.5"
            : "min-h-12 gap-3 rounded-xs bg-foreground py-3.5 pl-4 pr-2"
        )}
      >
        {isIos ? <Icon as={tone.icon} size={20} className={tone.className} /> : null}
        <View className={cn("shrink gap-0.5", !isIos && "flex-1")}>
          <Text
            numberOfLines={isIos ? 2 : 3}
            className={cn(
              "font-sans-medium text-subhead",
              isIos ? "text-popover-foreground" : "font-sans text-background"
            )}
          >
            {item.message}
          </Text>
          {item.description ? (
            <Text
              numberOfLines={3}
              className={cn(
                "font-sans text-footnote",
                isIos ? "text-muted-foreground" : "text-background opacity-80"
              )}
            >
              {item.description}
            </Text>
          ) : null}
        </View>
        {item.action ? (
          <Pressable
            accessibilityRole="button"
            hitSlop={8}
            onPress={runAction}
            className="rounded-full px-2 py-1 active:opacity-60"
          >
            <Text
              className={cn(
                "font-sans-semibold text-subhead",
                isIos ? "text-link" : "text-primary-medium"
              )}
            >
              {item.action.label}
            </Text>
          </Pressable>
        ) : null}
      </Pressable>
    </Animated.View>
  );
}

type ToastProviderProps = {
  children?: React.ReactNode;
  /**
   * Extra space under the Android snackbar, on top of the safe-area inset.
   * The default 92 clears the 80 dp navigation bar plus a 12 dp gap. A screen
   * with a FAB adds to it with `useAndroidToastLift`.
   */
  androidBottomOffset?: number;
};

/** Renders the toast stack over every screen. Mount it once, after the navigator. */
function ToastProvider({ children, androidBottomOffset = 92 }: ToastProviderProps) {
  const insets = useSafeAreaInsets();
  const [toasts, setToasts] = React.useState<ToastRecord[]>([]);
  const androidLift: number = React.useSyncExternalStore(
    subscribeAndroidToastLift,
    currentAndroidToastLift
  );
  const timers = React.useRef(new Map<string, ReturnType<typeof setTimeout>>());

  const remove = React.useCallback((id: string) => {
    const timer = timers.current.get(id);
    if (timer) clearTimeout(timer);
    timers.current.delete(id);
    setToasts((prev) => prev.filter((item) => item.id !== id));
  }, []);

  const schedule = React.useCallback(
    (id: string, durationMs: number) => {
      const previous = timers.current.get(id);
      if (previous) clearTimeout(previous);
      timers.current.delete(id);
      if (durationMs > 0)
        timers.current.set(
          id,
          setTimeout(() => remove(id), durationMs)
        );
    },
    [remove]
  );

  React.useEffect(() => {
    const listener = (event: ToastEvent) => {
      if (event.kind === "dismiss") {
        remove(event.id);
        return;
      }
      if (Platform.OS === "ios") {
        // A view that appears outside the focused screen is not read out on
        // iOS; the announcement is what VoiceOver users hear.
        AccessibilityInfo.announceForAccessibility(
          [event.toast.message, event.toast.description].filter(Boolean).join(". ")
        );
      }
      // A success is felt as well as seen, so a confirmation lands even when
      // the reader has already looked away from the screen.
      if (event.toast.tone === "success") hapticNotification("success");
      setToasts((prev) => {
        const result = addToast(prev, event.toast);
        if (result.kind === "merged") {
          const merged = result.toasts.find((item) => item.id === result.into);
          schedule(result.into, merged?.durationMs ?? event.toast.durationMs);
        } else {
          for (const id of result.dropped) {
            const timer = timers.current.get(id);
            if (timer) clearTimeout(timer);
            timers.current.delete(id);
          }
          schedule(event.toast.id, event.toast.durationMs);
        }
        return result.toasts;
      });
    };
    listeners.add(listener);
    const activeTimers = timers.current;
    return () => {
      listeners.delete(listener);
      for (const timer of activeTimers.values()) clearTimeout(timer);
      activeTimers.clear();
    };
  }, [remove, schedule]);

  const stack =
    toasts.length === 0 ? null : (
      <View
        pointerEvents="box-none"
        className="absolute left-0 right-0 gap-2 px-4"
        style={
          Platform.OS === "ios"
            ? { top: insets.top + 8 }
            : { bottom: insets.bottom + androidBottomOffset + androidLift }
        }
      >
        {toasts.map((item) => (
          <ToastView key={item.id} toast={item} onDismiss={remove} />
        ))}
      </View>
    );

  return (
    <>
      {children}
      {stack && Platform.OS === "ios" ? (
        // A FullWindowOverlay draws above native formSheet presentations,
        // which an absolutely positioned view under the navigator cannot.
        <FullWindowOverlay>{stack}</FullWindowOverlay>
      ) : (
        stack
      )}
    </>
  );
}

export { dismissToast, toast, ToastProvider, ToastView, useAndroidToastLift };
export type { ToastOptions, ToastProviderProps };
