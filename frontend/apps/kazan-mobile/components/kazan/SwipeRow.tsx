import type { LucideIcon } from "lucide-react-native";
import * as React from "react";
import { Pressable, View } from "react-native";
import ReanimatedSwipeable, {
  SwipeDirection,
  type SwipeableMethods,
} from "react-native-gesture-handler/ReanimatedSwipeable";

import { Icon } from "@/components/ui/icon";
import { Text } from "@/components/ui/text";
import { cn } from "@/lib/utils";

/** The width of each revealed action. */
const ACTION_WIDTH = 88;

/** The width of the leading action; a swipe past it runs the action. */
const LEADING_WIDTH = 104;

type SwipeTone = "success" | "info" | "destructive";

const TONE: Record<SwipeTone, { ground: string; text: string }> = {
  success: { ground: "bg-success-fill", text: "text-success-foreground" },
  info: { ground: "bg-primary-fill", text: "text-primary-foreground" },
  destructive: { ground: "bg-destructive-fill", text: "text-destructive-foreground" },
};

type SwipeRowAction = {
  label: string;
  icon: LucideIcon;
  tone: SwipeTone;
  onPress: () => void;
};

type SwipeRowProps = {
  children: React.ReactNode;
  /** Runs when the row is swiped right past the action; the row closes again. */
  leading?: SwipeRowAction | null;
  /** Shown when the row is swiped left; a tap runs one and closes the row. */
  trailing?: readonly SwipeRowAction[];
  enabled?: boolean;
  className?: string;
};

/**
 * A list row with swipe actions: a swipe right runs the leading action at
 * once (as Mail's archive does), a swipe left reveals the trailing buttons.
 * The actions are also reachable without a swipe from the row's own menu and
 * screen-reader actions, which the caller provides.
 */
function SwipeRow({ children, leading, trailing = [], enabled = true, className }: SwipeRowProps) {
  const ref = React.useRef<SwipeableMethods>(null);
  const active: boolean = enabled && (!!leading || trailing.length > 0);
  if (!active) return <View className={className}>{children}</View>;

  const onOpen = (direction: SwipeDirection.LEFT | SwipeDirection.RIGHT) => {
    if (direction !== SwipeDirection.RIGHT || !leading) return;
    ref.current?.close();
    leading.onPress();
  };

  return (
    <ReanimatedSwipeable
      ref={ref}
      friction={1.5}
      leftThreshold={LEADING_WIDTH * 0.8}
      rightThreshold={ACTION_WIDTH * 0.5}
      overshootLeft={false}
      overshootRight={false}
      onSwipeableOpen={onOpen}
      renderLeftActions={
        leading
          ? () => (
              <View
                style={{ width: LEADING_WIDTH }}
                className={cn(
                  "items-start justify-center rounded-lg px-4",
                  TONE[leading.tone].ground
                )}
              >
                <ActionFace action={leading} />
              </View>
            )
          : undefined
      }
      renderRightActions={
        trailing.length > 0
          ? () => (
              <View className="flex-row">
                {trailing.map((action) => (
                  <Pressable
                    key={action.label}
                    accessibilityRole="button"
                    accessibilityLabel={action.label}
                    onPress={() => {
                      ref.current?.close();
                      action.onPress();
                    }}
                    style={{ width: ACTION_WIDTH }}
                    className={cn(
                      "items-center justify-center rounded-lg active:opacity-80",
                      TONE[action.tone].ground
                    )}
                  >
                    <ActionFace action={action} />
                  </Pressable>
                ))}
              </View>
            )
          : undefined
      }
      containerStyle={{ overflow: "hidden" }}
    >
      <View className={className}>{children}</View>
    </ReanimatedSwipeable>
  );
}

function ActionFace({ action }: { action: SwipeRowAction }) {
  const tone = TONE[action.tone];
  return (
    <View className="items-center gap-1">
      <Icon as={action.icon} size={20} className={tone.text} />
      <Text variant="caption" className={tone.text} numberOfLines={1}>
        {action.label}
      </Text>
    </View>
  );
}

export { SwipeRow };
export type { SwipeRowAction, SwipeRowProps, SwipeTone };
