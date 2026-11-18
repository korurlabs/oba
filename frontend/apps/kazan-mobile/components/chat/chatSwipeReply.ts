/*
 * The swipe that starts a reply, as iOS Messages and Slack have it: the
 * message follows the finger to the right, the reply icon fades in under
 * it, and a release past the threshold starts the reply. The functions run
 * on the UI thread inside the gesture (hence the worklet directive) and in
 * the unit tests alike.
 */

/** How far the message moves before a release starts the reply. */
export const CHAT_SWIPE_REPLY_THRESHOLD = 64;

/** The message never moves further than this, however far the finger goes. */
export const CHAT_SWIPE_REPLY_MAX = 96;

/** Past the threshold the message follows only this share of the finger, so the end feels firm. */
const PAST_THRESHOLD_FOLLOW = 1 / 3;

/** A horizontal move of this many points to the right starts the swipe; a move to the left never does. */
export const CHAT_SWIPE_REPLY_ACTIVATE_PX = 12;

/** A vertical move of this many points first leaves the touch to the list's scroll. */
export const CHAT_SWIPE_REPLY_FAIL_Y_PX = 10;

/**
 * A touch that starts this close to the left edge is left to the screen's
 * Back swipe (the navigator's edge gesture on iOS, the system's on Android).
 */
export const CHAT_SWIPE_REPLY_EDGE_GUARD_PX = 28;

/** Where the message sits for a finger moved `translationX` points. */
export function chatSwipeReplyOffset(translationX: number): number {
  "worklet";
  if (translationX <= 0) return 0;
  if (translationX <= CHAT_SWIPE_REPLY_THRESHOLD) return translationX;
  return Math.min(
    CHAT_SWIPE_REPLY_MAX,
    CHAT_SWIPE_REPLY_THRESHOLD + (translationX - CHAT_SWIPE_REPLY_THRESHOLD) * PAST_THRESHOLD_FOLLOW
  );
}

/** True while a release at `offset` starts the reply. */
export function chatSwipeReplyArmed(offset: number): boolean {
  "worklet";
  return offset >= CHAT_SWIPE_REPLY_THRESHOLD;
}

/** How far the reply icon has faded in, from 0 at rest to 1 at the threshold. */
export function chatSwipeReplyProgress(offset: number): number {
  "worklet";
  return Math.min(1, Math.max(0, offset / CHAT_SWIPE_REPLY_THRESHOLD));
}

/**
 * Whether a move from `wasArmed` to `offset` crosses the threshold outward,
 * the moment the light haptic marks. Moving back below it disarms silently.
 */
export function chatSwipeReplyCrossed(wasArmed: boolean, offset: number): boolean {
  "worklet";
  return !wasArmed && chatSwipeReplyArmed(offset);
}
