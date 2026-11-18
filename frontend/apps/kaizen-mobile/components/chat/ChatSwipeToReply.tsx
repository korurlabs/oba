import * as Haptics from "expo-haptics";
import { Reply } from "lucide-react-native";
import * as React from "react";
import { View } from "react-native";
import { Gesture, GestureDetector } from "react-native-gesture-handler";
import Animated, { useAnimatedStyle, useSharedValue, withTiming } from "react-native-reanimated";
import { scheduleOnRN } from "react-native-worklets";

import {
  CHAT_SWIPE_REPLY_ACTIVATE_PX,
  CHAT_SWIPE_REPLY_EDGE_GUARD_PX,
  CHAT_SWIPE_REPLY_FAIL_Y_PX,
  chatSwipeReplyArmed,
  chatSwipeReplyCrossed,
  chatSwipeReplyOffset,
  chatSwipeReplyProgress,
} from "@/components/chat/chatSwipeReply";
import { Icon } from "@/components/ui/icon";

/** How long the message takes to slide back after a release. */
const RETURN_MS = 180;

function tickHaptic(): void {
  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch((error: unknown) => {
    // Haptics are a courtesy: the swipe works without them, so the failure is only logged.
    console.warn("[chat] haptics failed", error instanceof Error ? error.message : error);
  });
}

type ChatSwipeToReplyProps = {
  /** Starts the reply; called on a release past the threshold. */
  onReply: () => void;
  children: React.ReactNode;
};

/**
 * A message that a swipe to the right answers: the message follows the
 * finger, the reply icon fades in behind it, a light haptic marks the
 * threshold, and a release past it starts the reply. Only a rightward move
 * that starts away from the left edge takes the touch, so the list's
 * vertical scroll and the Back swipe from the edge keep working; the
 * gesture cancels the message's own long press once it takes over.
 */
export function ChatSwipeToReply({ onReply, children }: ChatSwipeToReplyProps) {
  const offset = useSharedValue<number>(0);
  const armed = useSharedValue<boolean>(false);

  const gesture = React.useMemo(
    () =>
      Gesture.Pan()
        .activeOffsetX(CHAT_SWIPE_REPLY_ACTIVATE_PX)
        .failOffsetY([-CHAT_SWIPE_REPLY_FAIL_Y_PX, CHAT_SWIPE_REPLY_FAIL_Y_PX])
        .hitSlop({ left: -CHAT_SWIPE_REPLY_EDGE_GUARD_PX })
        .onUpdate((event) => {
          const next: number = chatSwipeReplyOffset(event.translationX);
          if (chatSwipeReplyCrossed(armed.value, next)) scheduleOnRN(tickHaptic);
          armed.value = chatSwipeReplyArmed(next);
          offset.value = next;
        })
        .onEnd(() => {
          if (armed.value) scheduleOnRN(onReply);
        })
        .onFinalize(() => {
          armed.value = false;
          offset.value = withTiming(0, { duration: RETURN_MS });
        }),
    [armed, offset, onReply]
  );

  const messageStyle = useAnimatedStyle(() => ({
    transform: [{ translateX: offset.value }],
  }));

  const iconStyle = useAnimatedStyle(() => {
    const progress: number = chatSwipeReplyProgress(offset.value);
    return { opacity: progress, transform: [{ scale: 0.6 + 0.4 * progress }] };
  });

  return (
    <GestureDetector gesture={gesture}>
      <View>
        <Animated.View
          pointerEvents="none"
          importantForAccessibility="no-hide-descendants"
          accessibilityElementsHidden
          style={iconStyle}
          className="absolute bottom-0 left-4 top-0 justify-center"
        >
          <View className="size-8 items-center justify-center rounded-full bg-muted">
            <Icon as={Reply} size={16} className="text-foreground" />
          </View>
        </Animated.View>
        <Animated.View style={messageStyle}>{children}</Animated.View>
      </View>
    </GestureDetector>
  );
}
