import { ChatChannelPurpose, type ChatChannelResponse } from "@/lib/schemas/chat";

/*
 * Reordering the channel list. Where a channel dragged by its grip lands is
 * worked out on the UI thread while the finger moves, so those functions
 * are worklets and take plain numbers.
 */

/** The height a row has before it is laid out. */
export const CHAT_REORDER_FALLBACK_ROW_PX = 52;

/**
 * The index a row dragged from `from` by `dy` points lands on: it passes a
 * neighbour once it has covered half of that neighbour's height.
 */
export function chatReorderTarget(from: number, dy: number, heights: readonly number[]): number {
  "worklet";
  const height = (index: number): number => heights[index] ?? CHAT_REORDER_FALLBACK_ROW_PX;
  const count: number = heights.length;
  let target: number = from;
  let left: number = Math.abs(dy);
  const step: number = dy > 0 ? 1 : -1;
  while (target + step >= 0 && target + step < count && left > height(target + step) / 2) {
    left -= height(target + step);
    target += step;
  }
  return target;
}

/**
 * How far a row that is not dragged moves aside while a row dragged from
 * `from` points at `target`: by the dragged row's height, towards the
 * place it left.
 */
export function chatReorderShift(
  index: number,
  from: number,
  target: number,
  draggedHeight: number
): number {
  "worklet";
  if (from < 0 || index === from) return 0;
  if (from < target && index > from && index <= target) return -draggedHeight;
  if (target < from && index >= target && index < from) return draggedHeight;
  return 0;
}

/**
 * The whole order after moving a listed channel `steps` places up (negative)
 * or down among the listed ones, as far as the list goes; null when it does
 * not move. Channels of other purposes keep their place relative to the
 * channels around them.
 */
export function moveChatChannel(
  ordered: readonly ChatChannelResponse[],
  channelId: string,
  steps: number
): string[] | null {
  const listed: string[] = ordered
    .filter((channel) => channel.purpose === ChatChannelPurpose.GENERAL)
    .map((channel) => channel.id);
  const position: number = listed.indexOf(channelId);
  if (position < 0) return null;
  const target: number = Math.max(0, Math.min(listed.length - 1, position + steps));
  const neighbour: string | undefined = listed[target];
  if (target === position || !neighbour) return null;
  const ids: string[] = ordered.map((channel) => channel.id).filter((id) => id !== channelId);
  ids.splice(ids.indexOf(neighbour) + (target > position ? 1 : 0), 0, channelId);
  return ids;
}
