import { describe, expect, it } from "bun:test";

import {
  chatReorderShift,
  chatReorderTarget,
  moveChatChannel,
} from "@/components/chat/chatChannelReorder";
import type { ChatChannelResponse } from "@/lib/schemas/chat";

const HEIGHTS = [50, 50, 70, 50];

describe("chatReorderTarget", () => {
  it("passes a neighbour once half of it is covered", () => {
    expect(chatReorderTarget(0, 24, HEIGHTS)).toBe(0);
    expect(chatReorderTarget(0, 26, HEIGHTS)).toBe(1);
    expect(chatReorderTarget(0, 50 + 36, HEIGHTS)).toBe(2);
    expect(chatReorderTarget(3, -60, HEIGHTS)).toBe(2);
  });

  it("stops at the ends of the list", () => {
    expect(chatReorderTarget(1, 1000, HEIGHTS)).toBe(3);
    expect(chatReorderTarget(1, -1000, HEIGHTS)).toBe(0);
  });
});

describe("chatReorderShift", () => {
  it("moves the rows between the place left and the target aside", () => {
    expect([0, 1, 2, 3].map((index) => chatReorderShift(index, 0, 2, 50))).toEqual([
      0, -50, -50, 0,
    ]);
    expect([0, 1, 2, 3].map((index) => chatReorderShift(index, 3, 1, 50))).toEqual([0, 50, 50, 0]);
    expect(chatReorderShift(1, -1, 2, 50)).toBe(0);
  });
});

describe("moveChatChannel", () => {
  const channel = (id: string, purpose: string = "GENERAL") =>
    ({ id, purpose }) as unknown as ChatChannelResponse;
  const ordered = [channel("a"), channel("u", "DAILY_UPDATES"), channel("b"), channel("c")];

  it("moves a channel several places among the listed ones", () => {
    expect(moveChatChannel(ordered, "a", 2)).toEqual(["u", "b", "c", "a"]);
    expect(moveChatChannel(ordered, "c", -2)).toEqual(["c", "a", "u", "b"]);
    expect(moveChatChannel(ordered, "a", 1)).toEqual(["u", "b", "a", "c"]);
  });

  it("clamps to the list and leaves a move that changes nothing", () => {
    expect(moveChatChannel(ordered, "b", 9)).toEqual(["a", "u", "c", "b"]);
    expect(moveChatChannel(ordered, "a", -1)).toBeNull();
    expect(moveChatChannel(ordered, "a", 0)).toBeNull();
    expect(moveChatChannel(ordered, "u", 1)).toBeNull();
  });
});
