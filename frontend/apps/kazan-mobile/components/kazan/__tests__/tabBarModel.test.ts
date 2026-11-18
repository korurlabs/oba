import { describe, expect, it } from "bun:test";

import { hidesTabBar } from "@/components/kazan/tabBarModel";

describe("hidesTabBar", () => {
  it("hides the bar on a chat thread and on chat search", () => {
    expect(hidesTabBar(["(app)", "(tabs)", "chat", "thread", "[threadId]"])).toBe(true);
    expect(hidesTabBar(["(app)", "(tabs)", "chat", "search"])).toBe(true);
  });

  it("hides the bar under the full-screen Share pull request form and its picker", () => {
    expect(hidesTabBar(["(app)", "(tabs)", "chat", "share-pr"])).toBe(true);
    expect(hidesTabBar(["(app)", "(tabs)", "chat", "share-pr-reviewers"])).toBe(true);
  });

  it("keeps the bar on the Chat list, a channel and the other tabs", () => {
    expect(hidesTabBar(["(app)", "(tabs)", "chat"])).toBe(false);
    expect(hidesTabBar(["(app)", "(tabs)", "chat", "channel", "[channelId]"])).toBe(false);
    expect(hidesTabBar(["(app)", "(tabs)", "boards", "search"])).toBe(false);
    expect(hidesTabBar(["(app)", "(tabs)", "home"])).toBe(false);
  });

  it("keeps the bar outside the tabs", () => {
    expect(hidesTabBar(["(app)", "ticket", "[ticketKey]"])).toBe(false);
    expect(hidesTabBar([])).toBe(false);
  });
});
