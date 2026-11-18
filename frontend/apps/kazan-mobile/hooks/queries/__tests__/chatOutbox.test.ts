import { afterEach, describe, expect, it } from "bun:test";

import { ORG_ID, chatOutboxRow } from "@/components/chat/__tests__/chatFixtures";
import {
  chatOutboxRowsToResend,
  clearChatOutbox,
  createChatOutboxResender,
  getChatOutboxMessages,
  putChatOutboxMessage,
  removeChatOutboxMessage,
} from "@/hooks/queries/chatOutbox";
import type { ChatOutboxMessage } from "@/lib/schemas/chat";

const OTHER_ORG = "org-2";

function failedByNetwork(overrides: Partial<ChatOutboxMessage>): ChatOutboxMessage {
  return chatOutboxRow({ status: "failed", failedByNetwork: true, ...overrides });
}

afterEach(() => {
  clearChatOutbox();
});

describe("chatOutboxRowsToResend", () => {
  it("resends only the active organization's rows that got no response", () => {
    putChatOutboxMessage(failedByNetwork({ clientNonce: "a" }));
    putChatOutboxMessage(failedByNetwork({ clientNonce: "b", organizationId: OTHER_ORG }));
    putChatOutboxMessage(chatOutboxRow({ clientNonce: "c", status: "failed" }));
    putChatOutboxMessage(chatOutboxRow({ clientNonce: "d" }));

    expect(chatOutboxRowsToResend(ORG_ID).map((row) => row.clientNonce)).toEqual(["a"]);
    expect(chatOutboxRowsToResend(OTHER_ORG).map((row) => row.clientNonce)).toEqual(["b"]);
  });
});

describe("createChatOutboxResender", () => {
  it("sends the organization's rows and keeps the other organization's for later", async () => {
    putChatOutboxMessage(failedByNetwork({ clientNonce: "a" }));
    putChatOutboxMessage(failedByNetwork({ clientNonce: "b", organizationId: OTHER_ORG }));
    const sent: string[] = [];
    const resend = createChatOutboxResender(async (row: ChatOutboxMessage) => {
      sent.push(`${row.organizationId}/${row.clientNonce}`);
    });

    resend(ORG_ID);
    await Promise.resolve();
    expect(sent).toEqual([`${ORG_ID}/a`]);
    expect(getChatOutboxMessages().map((row) => row.clientNonce)).toEqual(["a", "b"]);
  });
});

describe("clearChatOutbox", () => {
  it("forgets every row of every organization", () => {
    putChatOutboxMessage(chatOutboxRow({ clientNonce: "a" }));
    putChatOutboxMessage(failedByNetwork({ clientNonce: "b", organizationId: OTHER_ORG }));

    clearChatOutbox();

    expect(getChatOutboxMessages()).toEqual([]);
    expect(chatOutboxRowsToResend(OTHER_ORG)).toEqual([]);
  });
});

describe("clientNonce reconciliation", () => {
  it("replaces a pending row with the same nonce in place", () => {
    clearChatOutbox();
    putChatOutboxMessage(chatOutboxRow({ clientNonce: "a" }));
    putChatOutboxMessage(chatOutboxRow({ clientNonce: "b" }));
    putChatOutboxMessage(chatOutboxRow({ clientNonce: "a", status: "failed" }));
    expect(getChatOutboxMessages().map((row) => `${row.clientNonce}:${row.status}`)).toEqual([
      "a:failed",
      "b:pending",
    ]);
  });

  it("drops only the row of the confirmed nonce", () => {
    clearChatOutbox();
    putChatOutboxMessage(chatOutboxRow({ clientNonce: "a" }));
    putChatOutboxMessage(chatOutboxRow({ clientNonce: "b" }));
    removeChatOutboxMessage("a");
    removeChatOutboxMessage("unknown");
    expect(getChatOutboxMessages().map((row) => row.clientNonce)).toEqual(["b"]);
  });
});
