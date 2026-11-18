"use client";

import { useCallback, useEffect, useMemo, useRef, type PointerEvent, type RefObject, type WheelEvent } from "react";
import { prefersReducedMotion } from "@oba/shared/lib/reduced-motion";

/** How far a row must be swiped to arm the reply, in pixels. */
export const CHAT_SWIPE_THRESHOLD_PX = 64;
/** The furthest a row moves, however far the finger goes, in pixels. */
export const CHAT_SWIPE_MAX_PX = 96;
/** Movement below this is not yet a direction, so a tap stays a tap. */
const SWIPE_SLOP_PX = 8;
/** Past the threshold the row follows the finger at this fraction, so it feels anchored. */
const SWIPE_RESISTANCE = 0.5;
/** A pause this long ends a trackpad swipe; until then its momentum starts no second reply. */
const WHEEL_WINDOW_MS = 300;
/** How long the row shows a trackpad swipe after the last wheel event. */
const WHEEL_SETTLE_MS = 150;
const VIBRATE_MS = 10;

/**
 * CSS variables written on the row while it is swiped: the horizontal
 * offset in pixels and the progress towards the threshold (0 to 1). They are
 * written straight to the element, so a swipe never re-renders the row.
 */
export const CHAT_SWIPE_OFFSET_VAR = "--chat-swipe-offset";
export const CHAT_SWIPE_PROGRESS_VAR = "--chat-swipe-progress";
/** Present on the row while a gesture moves it; transitions are off then, so it follows without lag. */
export const CHAT_SWIPING_ATTRIBUTE = "data-swiping";
/** Present on the row while it is at or past the threshold, so the arrow can show that a reply will start. */
export const CHAT_SWIPE_ARMED_ATTRIBUTE = "data-swipe-armed";

/** A short vibration where the device has one (most phones; never on iOS Safari). */
export function chatVibrate(): void {
  if (typeof navigator !== "undefined" && typeof navigator.vibrate === "function") navigator.vibrate(VIBRATE_MS);
}

interface SwipeTouch {
  pointerId: number;
  startX: number;
  startY: number;
  swiping: boolean;
  distance: number;
}

interface WheelSwipe {
  /** The rightward distance so far; it falls again when the fingers move back. */
  total: number;
  /** The reply has started; the rest of the gesture only moves the row. */
  replied: boolean;
}

export interface SwipeToReplyHandlers {
  onPointerDown: (event: PointerEvent<HTMLElement>) => void;
  onPointerMove: (event: PointerEvent<HTMLElement>) => void;
  onPointerUp: (event: PointerEvent<HTMLElement>) => void;
  onPointerCancel: (event: PointerEvent<HTMLElement>) => void;
  onWheel: (event: WheelEvent<HTMLElement>) => void;
}

/**
 * Swipe right on a message to reply to it.
 *
 * On touch and pen the row follows the finger horizontally while vertical
 * scrolling keeps working (the row sets `touch-action: pan-y`, so the
 * browser hands horizontal moves to the page and cancels the pointer when
 * it scrolls). At 64 px the row is armed: its arrow turns solid and the
 * device vibrates once. Releasing while armed starts the reply; moving back
 * below 64 px disarms the row, and releasing then only springs it back. On a
 * trackpad, a two-finger swipe to the right over the row moves it the same
 * way. A wheel has no release, so the reply starts on reaching 64 px, and the
 * row keeps following until the fingers stop. A mouse never swipes, so
 * dragging selects text as usual. With reduced motion the row does not move;
 * the arrow still fills and turns solid.
 */
export function useSwipeToReply({
  rowRef,
  enabled,
  onReply,
}: {
  rowRef: RefObject<HTMLElement | null>;
  enabled: boolean;
  onReply: () => void;
}): SwipeToReplyHandlers {
  const touchRef = useRef<SwipeTouch | null>(null);
  const wheelRef = useRef<WheelSwipe | null>(null);
  /** Momentum that outlasts the row of a swipe that replied is ignored until the gesture pauses. */
  const wheelLockedUntilRef = useRef<number>(0);
  const settleTimerRef = useRef<number | null>(null);
  const onReplyRef = useRef(onReply);
  useEffect(() => {
    onReplyRef.current = onReply;
  }, [onReply]);

  const show = useCallback(
    (distance: number) => {
      const row = rowRef.current;
      if (!row) return;
      const progress = Math.min(1, distance / CHAT_SWIPE_THRESHOLD_PX);
      const beyond = Math.max(0, distance - CHAT_SWIPE_THRESHOLD_PX);
      const travel = Math.min(distance, CHAT_SWIPE_THRESHOLD_PX) + beyond * SWIPE_RESISTANCE;
      const offset = prefersReducedMotion() ? 0 : Math.min(travel, CHAT_SWIPE_MAX_PX);
      row.style.setProperty(CHAT_SWIPE_OFFSET_VAR, `${offset}px`);
      row.style.setProperty(CHAT_SWIPE_PROGRESS_VAR, String(progress));
      row.setAttribute(CHAT_SWIPING_ATTRIBUTE, "");
      row.toggleAttribute(CHAT_SWIPE_ARMED_ATTRIBUTE, distance >= CHAT_SWIPE_THRESHOLD_PX);
    },
    [rowRef],
  );

  /** Springs the row back; the transition of the row's content animates it. */
  const settle = useCallback(() => {
    const row = rowRef.current;
    if (!row) return;
    row.removeAttribute(CHAT_SWIPING_ATTRIBUTE);
    row.removeAttribute(CHAT_SWIPE_ARMED_ATTRIBUTE);
    row.style.setProperty(CHAT_SWIPE_OFFSET_VAR, "0px");
    row.style.setProperty(CHAT_SWIPE_PROGRESS_VAR, "0");
  }, [rowRef]);

  useEffect(
    () => () => {
      if (settleTimerRef.current !== null) window.clearTimeout(settleTimerRef.current);
    },
    [],
  );

  const end = useCallback(
    (event: PointerEvent<HTMLElement>, released: boolean) => {
      const touch = touchRef.current;
      if (!touch || touch.pointerId !== event.pointerId) return;
      touchRef.current = null;
      if (touch.swiping && event.currentTarget.hasPointerCapture(event.pointerId)) {
        event.currentTarget.releasePointerCapture(event.pointerId);
      }
      settle();
      if (released && touch.swiping && touch.distance >= CHAT_SWIPE_THRESHOLD_PX) onReplyRef.current();
    },
    [settle],
  );

  return useMemo<SwipeToReplyHandlers>(
    () => ({
      onPointerDown: (event) => {
        if (!enabled || event.pointerType === "mouse" || !event.isPrimary) return;
        touchRef.current = {
          pointerId: event.pointerId,
          startX: event.clientX,
          startY: event.clientY,
          swiping: false,
          distance: 0,
        };
      },
      onPointerMove: (event) => {
        const touch = touchRef.current;
        if (!touch || touch.pointerId !== event.pointerId) return;
        const dx = event.clientX - touch.startX;
        const dy = event.clientY - touch.startY;
        if (!touch.swiping) {
          if (Math.abs(dy) > SWIPE_SLOP_PX && Math.abs(dy) >= Math.abs(dx)) {
            // A vertical move is the page's scroll, not a swipe.
            touchRef.current = null;
            return;
          }
          if (dx <= SWIPE_SLOP_PX || dx <= Math.abs(dy)) return;
          touch.swiping = true;
          event.currentTarget.setPointerCapture(event.pointerId);
        }
        const wasArmed = touch.distance >= CHAT_SWIPE_THRESHOLD_PX;
        touch.distance = Math.max(0, dx - SWIPE_SLOP_PX);
        show(touch.distance);
        if (!wasArmed && touch.distance >= CHAT_SWIPE_THRESHOLD_PX) chatVibrate();
      },
      onPointerUp: (event) => end(event, true),
      onPointerCancel: (event) => end(event, false),
      onWheel: (event) => {
        if (!enabled) return;
        // Lines and pages (Firefox with a mouse wheel) to pixels.
        const scale = event.deltaMode === 1 ? 16 : event.deltaMode === 2 ? (rowRef.current?.clientWidth ?? 800) : 1;
        // A swipe of two fingers to the right scrolls the content left: negative deltaX.
        const rightward = -event.deltaX * scale;
        const vertical = Math.abs(event.deltaY * scale);
        if (Math.abs(rightward) <= vertical) return;
        const now = event.timeStamp;
        const current = wheelRef.current;
        if (!current && now < wheelLockedUntilRef.current) {
          wheelLockedUntilRef.current = now + WHEEL_WINDOW_MS;
          return;
        }
        const swipe = current ?? { total: 0, replied: false };
        swipe.total = Math.max(0, swipe.total + rightward);
        wheelRef.current = swipe;

        if (settleTimerRef.current !== null) window.clearTimeout(settleTimerRef.current);
        show(swipe.total);
        if (!swipe.replied && swipe.total >= CHAT_SWIPE_THRESHOLD_PX) {
          swipe.replied = true;
          onReplyRef.current();
        }
        if (swipe.replied) wheelLockedUntilRef.current = now + WHEEL_WINDOW_MS;
        settleTimerRef.current = window.setTimeout(() => {
          settleTimerRef.current = null;
          wheelRef.current = null;
          settle();
        }, WHEEL_SETTLE_MS);
      },
    }),
    [enabled, end, rowRef, settle, show],
  );
}
