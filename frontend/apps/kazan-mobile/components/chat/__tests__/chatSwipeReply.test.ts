import { describe, expect, it } from "bun:test";

import {
  CHAT_SWIPE_REPLY_MAX,
  CHAT_SWIPE_REPLY_THRESHOLD,
  chatSwipeReplyArmed,
  chatSwipeReplyCrossed,
  chatSwipeReplyOffset,
  chatSwipeReplyProgress,
} from "@/components/chat/chatSwipeReply";

describe("chatSwipeReplyOffset", () => {
  it("does not move the message for a move to the left", () => {
    expect(chatSwipeReplyOffset(-40)).toBe(0);
  });

  it("follows the finger up to the threshold", () => {
    expect(chatSwipeReplyOffset(30)).toBe(30);
    expect(chatSwipeReplyOffset(CHAT_SWIPE_REPLY_THRESHOLD)).toBe(CHAT_SWIPE_REPLY_THRESHOLD);
  });

  it("follows a third of the finger past the threshold, never beyond the maximum", () => {
    expect(chatSwipeReplyOffset(CHAT_SWIPE_REPLY_THRESHOLD + 30)).toBe(
      CHAT_SWIPE_REPLY_THRESHOLD + 10
    );
    expect(chatSwipeReplyOffset(1000)).toBe(CHAT_SWIPE_REPLY_MAX);
  });
});

describe("arming the reply", () => {
  it("arms at the threshold and not before", () => {
    expect(chatSwipeReplyArmed(CHAT_SWIPE_REPLY_THRESHOLD - 1)).toBe(false);
    expect(chatSwipeReplyArmed(CHAT_SWIPE_REPLY_THRESHOLD)).toBe(true);
  });

  it("marks only the outward crossing with the haptic", () => {
    expect(chatSwipeReplyCrossed(false, CHAT_SWIPE_REPLY_THRESHOLD)).toBe(true);
    expect(chatSwipeReplyCrossed(true, CHAT_SWIPE_REPLY_THRESHOLD + 5)).toBe(false);
    expect(chatSwipeReplyCrossed(true, 10)).toBe(false);
    expect(chatSwipeReplyCrossed(false, 10)).toBe(false);
  });

  it("fades the icon in from rest to the threshold", () => {
    expect(chatSwipeReplyProgress(0)).toBe(0);
    expect(chatSwipeReplyProgress(CHAT_SWIPE_REPLY_THRESHOLD / 2)).toBe(0.5);
    expect(chatSwipeReplyProgress(CHAT_SWIPE_REPLY_MAX)).toBe(1);
  });
});
