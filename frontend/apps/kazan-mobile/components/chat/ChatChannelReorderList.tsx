import { GripVertical, Hash, Lock } from "lucide-react-native";
import * as React from "react";
import { Platform, View, type AccessibilityActionEvent } from "react-native";
import { Gesture, GestureDetector } from "react-native-gesture-handler";
import Animated, {
  useAnimatedReaction,
  useAnimatedStyle,
  useSharedValue,
  withTiming,
  type SharedValue,
} from "react-native-reanimated";
import { scheduleOnRN } from "react-native-worklets";

import { hapticImpact, hapticSelection } from "@/components/boards/dragFeedback";
import {
  CHAT_REORDER_FALLBACK_ROW_PX,
  chatReorderShift,
  chatReorderTarget,
} from "@/components/chat/chatChannelReorder";
import { ListRow, ListSection } from "@/components/kazan/List";
import { Icon } from "@/components/ui/icon";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import { useTranslation } from "@/i18n";
import { ChatChannelVisibility, type ChatChannelResponse } from "@/lib/schemas/chat";

/** How long the other rows take to step aside. */
const SHIFT_MS = 150;

/** How long a drop waits for its new order before the rows go back regardless. */
const SETTLE_FALLBACK_MS = 300;

/** The lifted row shows above the others and a little apart from them. */
const LIFTED_OPACITY = 0.92;

interface DragState {
  /** The index of the row the finger holds, -1 while none. */
  from: SharedValue<number>;
  /** The index it would land on. */
  target: SharedValue<number>;
  /** How far the finger moved it. */
  dy: SharedValue<number>;
  /** The laid-out height of every row. */
  heights: SharedValue<number[]>;
  /** A drop waits for its new order; a new drag clears it. */
  dropped: SharedValue<boolean>;
}

type ReorderRowProps = {
  channel: ChatChannelResponse;
  index: number;
  count: number;
  drag: DragState;
  onMove: (channelId: string, steps: number) => void;
  /** Moves the channel a drag let go of; the list then puts the rows back in place. */
  onDrop: (channelId: string, steps: number) => void;
};

function ReorderRow({ channel, index, count, drag, onMove, onDrop }: ReorderRowProps) {
  const { t } = useTranslation();
  const colors = useThemeColors();
  const { from, target, dy, heights, dropped } = drag;
  const isPrivate: boolean = channel.visibility === ChatChannelVisibility.PRIVATE;
  const channelId: string = channel.id;

  const gesture = React.useMemo(
    () =>
      // Only the grip takes the touch, so the rest of the row still scrolls the sheet.
      Gesture.Pan()
        .minDistance(0)
        .onStart(() => {
          dropped.value = false;
          from.value = index;
          target.value = index;
          dy.value = 0;
          scheduleOnRN(hapticImpact);
        })
        .onUpdate((event) => {
          const rows: number[] = heights.value;
          let above = 0;
          let below = 0;
          for (let row = 0; row < count; row += 1) {
            const height: number = rows[row] ?? CHAT_REORDER_FALLBACK_ROW_PX;
            if (row < index) above += height;
            if (row > index) below += height;
          }
          dy.value = Math.max(-above, Math.min(below, event.translationY));
          target.value = chatReorderTarget(index, dy.value, rows.slice(0, count));
        })
        .onFinalize(() => {
          const steps: number = target.value - index;
          if (from.value === index && steps !== 0) {
            // The rows stay where the drag left them until the new order is laid out.
            dropped.value = true;
            scheduleOnRN(onDrop, channelId, steps);
            return;
          }
          from.value = -1;
          target.value = -1;
          dy.value = 0;
        }),
    [channelId, count, dropped, dy, from, heights, index, onDrop, target]
  );

  // A tick each time the lifted row points at another place.
  useAnimatedReaction(
    () => (from.value === index ? target.value : -1),
    (next: number, previous: number | null) => {
      if (next >= 0 && previous !== null && previous >= 0 && next !== previous) {
        scheduleOnRN(hapticSelection);
      }
    }
  );

  // The ground the section gives the rows, so the lifted row hides the ones it passes.
  const rowGround: string = Platform.OS === "ios" ? colors.card : colors.screen;
  const style = useAnimatedStyle(() => {
    const lifted: boolean = from.value === index;
    const draggedHeight: number = heights.value[from.value] ?? CHAT_REORDER_FALLBACK_ROW_PX;
    const shift: number = chatReorderShift(index, from.value, target.value, draggedHeight);
    return {
      zIndex: lifted ? 1 : 0,
      opacity: lifted ? LIFTED_OPACITY : 1,
      backgroundColor: rowGround,
      transform: [
        {
          translateY: lifted
            ? dy.value
            : from.value >= 0
              ? withTiming(shift, { duration: SHIFT_MS })
              : 0,
        },
      ],
    };
  });

  const onAccessibilityAction = (event: AccessibilityActionEvent): void => {
    if (event.nativeEvent.actionName === "moveUp") onMove(channelId, -1);
    if (event.nativeEvent.actionName === "moveDown") onMove(channelId, 1);
  };

  return (
    <Animated.View
      style={style}
      onLayout={(event) => {
        const height: number = event.nativeEvent.layout.height;
        const next: number[] = [...heights.value];
        next[index] = height;
        heights.value = next;
      }}
    >
      <ListRow
        icon={isPrivate ? Lock : Hash}
        title={channel.name}
        subtitle={channel.member ? undefined : t("chatPrivateNotMember")}
        accessory="none"
        trailing={
          <GestureDetector gesture={gesture}>
            <View
              accessible
              accessibilityRole="adjustable"
              accessibilityLabel={t("chatReorderGrip", { channel: channel.name })}
              accessibilityActions={[
                ...(index > 0
                  ? [{ name: "moveUp", label: t("chatMoveUp", { channel: channel.name }) }]
                  : []),
                ...(index < count - 1
                  ? [{ name: "moveDown", label: t("chatMoveDown", { channel: channel.name }) }]
                  : []),
              ]}
              onAccessibilityAction={onAccessibilityAction}
              className="size-11 items-center justify-center"
            >
              <Icon as={GripVertical} size={20} className="text-muted-foreground" />
            </View>
          </GestureDetector>
        }
      />
    </Animated.View>
  );
}

/**
 * The general channels in the reader's order, each with a grip: holding the
 * grip lifts the row, the others step aside while it moves, and letting go
 * moves the channel there. A screen reader offers Move up and Move down on
 * the grip instead.
 */
export function ChatChannelReorderList({
  header,
  channels,
  onMove,
}: {
  header: string;
  channels: readonly ChatChannelResponse[];
  onMove: (channelId: string, steps: number) => void;
}) {
  const from = useSharedValue<number>(-1);
  const target = useSharedValue<number>(-1);
  const dy = useSharedValue<number>(0);
  const heights = useSharedValue<number[]>([]);
  const dropped = useSharedValue<boolean>(false);
  const drag: DragState = React.useMemo(
    () => ({ from, target, dy, heights, dropped }),
    [from, target, dy, heights, dropped]
  );

  const settle = React.useCallback((): void => {
    dropped.value = false;
    from.value = -1;
    target.value = -1;
    dy.value = 0;
  }, [dropped, from, target, dy]);

  // A new order is laid out: the rows drop their drag offsets in the same frame.
  React.useLayoutEffect(() => {
    if (dropped.value) settle();
  }, [channels, dropped, settle]);

  const drop = React.useCallback(
    (channelId: string, steps: number): void => {
      onMove(channelId, steps);
      // Settles a drop that changed no order too, which lays nothing out.
      setTimeout(() => {
        if (dropped.value) settle();
      }, SETTLE_FALLBACK_MS);
    },
    [dropped, onMove, settle]
  );

  return (
    <ListSection header={header}>
      {channels.map((channel, index) => (
        <ReorderRow
          key={channel.id}
          channel={channel}
          index={index}
          count={channels.length}
          drag={drag}
          onMove={onMove}
          onDrop={drop}
        />
      ))}
    </ListSection>
  );
}
