import { describe, expect, it } from "bun:test";

import {
  CHAT_VIEWER_DISMISS_DISTANCE,
  CHAT_VIEWER_DISMISS_VELOCITY,
  chatViewerClamp,
  chatViewerContentSize,
  chatViewerDismisses,
  chatViewerPanLimit,
} from "@/components/chat/chatImageViewerModel";

describe("chatViewerContentSize", () => {
  const box = { width: 400, height: 700 };

  it("fits a landscape photo to the box width, leaving bands above and below", () => {
    expect(chatViewerContentSize(box, { width: 4000, height: 3000 })).toEqual({
      width: 400,
      height: 300,
    });
  });

  it("fits a tall portrait photo to the box height, leaving bands at the sides", () => {
    expect(chatViewerContentSize(box, { width: 1000, height: 3500 })).toEqual({
      width: 200,
      height: 700,
    });
  });

  it("stands in the box when the image size is unknown", () => {
    expect(chatViewerContentSize(box, null)).toEqual(box);
    expect(chatViewerContentSize(box, { width: 0, height: 0 })).toEqual(box);
  });
});

describe("chatViewerPanLimit", () => {
  it("lets a fitted image not move and a box-filling one move by its overflow", () => {
    expect(chatViewerPanLimit(400, 400, 1)).toBe(0);
    expect(chatViewerPanLimit(400, 400, 2)).toBe(200);
    expect(chatViewerPanLimit(400, 400, 4)).toBe(600);
    expect(chatViewerPanLimit(0, 0, 3)).toBe(0);
  });

  it("keeps a letterboxed landscape photo inside the box on its short axis", () => {
    const content = chatViewerContentSize(
      { width: 400, height: 700 },
      { width: 4000, height: 3000 }
    );
    // 300 points high at 2x is 600, still shorter than the 700-point box.
    expect(chatViewerPanLimit(700, content.height, 2)).toBe(0);
    // At 4x it is 1200 high, so it may move by half of the 500 that overflows.
    expect(chatViewerPanLimit(700, content.height, 4)).toBe(250);
    expect(chatViewerPanLimit(400, content.width, 2)).toBe(200);
  });

  it("keeps a portrait photo inside the box on its narrow axis", () => {
    const content = chatViewerContentSize(
      { width: 400, height: 700 },
      { width: 1000, height: 3500 }
    );
    expect(chatViewerPanLimit(400, content.width, 2)).toBe(0);
    expect(chatViewerPanLimit(400, content.width, 3)).toBe(100);
    expect(chatViewerPanLimit(700, content.height, 2)).toBe(350);
  });
});

describe("chatViewerClamp", () => {
  it("holds the offset within the limit on both sides", () => {
    expect(chatViewerClamp(250, 200)).toBe(200);
    expect(chatViewerClamp(-250, 200)).toBe(-200);
    expect(chatViewerClamp(50, 200)).toBe(50);
    expect(chatViewerClamp(30, 0)).toBe(0);
  });
});

describe("chatViewerDismisses", () => {
  it("closes on a long or a quick swipe down, never on a swipe up", () => {
    expect(chatViewerDismisses(CHAT_VIEWER_DISMISS_DISTANCE + 1, 0)).toBe(true);
    expect(chatViewerDismisses(40, CHAT_VIEWER_DISMISS_VELOCITY + 1)).toBe(true);
    expect(chatViewerDismisses(40, 100)).toBe(false);
    expect(chatViewerDismisses(-200, CHAT_VIEWER_DISMISS_VELOCITY + 1)).toBe(false);
  });
});
