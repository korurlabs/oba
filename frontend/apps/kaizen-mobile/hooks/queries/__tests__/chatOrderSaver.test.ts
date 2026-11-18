import { describe, expect, it } from "bun:test";

import { createChatOrderSaver, type PendingChatOrder } from "@/hooks/queries/chatOrderSaver";

const DELAY_MS = 20;

function wait(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

describe("createChatOrderSaver", () => {
  it("saves a burst of moves once, with the last order, after the delay", async () => {
    const saved: PendingChatOrder[] = [];
    const saver = createChatOrderSaver((pending) => saved.push(pending), DELAY_MS);

    saver.schedule({ organizationId: "org-1", order: ["a", "b"] });
    saver.schedule({ organizationId: "org-1", order: ["b", "a"] });
    expect(saved).toEqual([]);

    await wait(DELAY_MS * 3);
    expect(saved).toEqual([{ organizationId: "org-1", order: ["b", "a"] }]);
  });

  it("saves the pending order at once when flushed, and not again after the delay", async () => {
    const saved: PendingChatOrder[] = [];
    const saver = createChatOrderSaver((pending) => saved.push(pending), DELAY_MS);

    saver.schedule({ organizationId: "org-1", order: ["b", "a"] });
    saver.flush();
    expect(saved).toEqual([{ organizationId: "org-1", order: ["b", "a"] }]);

    await wait(DELAY_MS * 3);
    expect(saved).toHaveLength(1);
  });

  it("saves nothing when flushed with no pending move", () => {
    const saved: PendingChatOrder[] = [];
    const saver = createChatOrderSaver((pending) => saved.push(pending), DELAY_MS);
    saver.flush();
    expect(saved).toEqual([]);
  });
});
