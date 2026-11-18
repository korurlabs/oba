import { describe, expect, it } from "bun:test";

import { parseChatInline } from "@/components/chat/chatMarkdown";
import {
  chatLinkedTicketKeys,
  chatTicketChipTitle,
  chatTicketRefFinder,
  splitChatTicketKeys,
} from "@/components/chat/chatTicketRefs";
import type { ChatTicketRefResponse } from "@/lib/schemas/chat";

import { TICKET } from "./chatFixtures";

const OTHER = "44444444-4444-4444-8444-444444444444";
const BOARD = "55555555-5555-4555-8555-555555555555";

/** A reference as the server returns it for a `<#ticketId>` token. */
function ref(overrides: Partial<ChatTicketRefResponse> = {}): ChatTicketRefResponse {
  return {
    ticketId: TICKET,
    boardId: BOARD,
    ticketKey: "ALP-6",
    ticketTitle: "Fix the login redirect",
    canOpen: true,
    ...overrides,
  };
}

describe("ticket tokens in the web chat's format", () => {
  // The web composer writes a ticket chip as `<#ticketId>` (chatTicketToken).
  it("parses the web's `<#ticketId>` token as a ticket inline", () => {
    expect(parseChatInline(`see <#${TICKET}> now`)).toEqual([
      { type: "text", text: "see " },
      { type: "ticket", ticketId: TICKET },
      { type: "text", text: " now" },
    ]);
  });

  it("leaves a ticket URL a link, as the web does", () => {
    expect(parseChatInline("http://localhost:3002/en/ticket/ALP-8")).toEqual([
      {
        type: "link",
        url: "http://localhost:3002/en/ticket/ALP-8",
        children: [{ type: "text", text: "http://localhost:3002/en/ticket/ALP-8" }],
      },
    ]);
  });
});

describe("chatTicketRefFinder", () => {
  it("finds a reference by its ticket ID, in any case", () => {
    const find = chatTicketRefFinder(`<#${TICKET}>`, [ref()]);
    expect(find(TICKET.toUpperCase())?.ticketKey).toBe("ALP-6");
  });

  it("finds a reference whose ticket was removed by its token's position", () => {
    const removed: ChatTicketRefResponse = ref({ ticketId: null, ticketKey: "ALP-2" });
    const find = chatTicketRefFinder(`<#${OTHER}> and <#${TICKET}>`, [
      removed,
      ref({ ticketId: TICKET }),
    ]);
    expect(find(OTHER)).toBe(removed);
    expect(find(TICKET)?.ticketKey).toBe("ALP-6");
  });

  it("finds nothing for a token without a snapshot", () => {
    expect(chatTicketRefFinder(`<#${OTHER}>`, [])(OTHER)).toBeUndefined();
  });
});

describe("chatTicketChipTitle", () => {
  it("keeps a short title and cuts a long one with an ellipsis", () => {
    expect(chatTicketChipTitle("  Short  ")).toBe("Short");
    const cut: string = chatTicketChipTitle("a".repeat(60), 10);
    expect(cut).toBe(`${"a".repeat(9)}…`);
  });
});

describe("splitChatTicketKeys", () => {
  const linked = chatLinkedTicketKeys([{ ticketKey: "ALP-6", ticketId: TICKET, boardId: BOARD }]);

  it("splits out only the keys the server linked", () => {
    expect(splitChatTicketKeys("ALP-6 and ALP-7, done", linked)).toEqual([
      { kind: "key", ticketKey: "ALP-6" },
      { kind: "text", text: " and ALP-7, done" },
    ]);
  });

  it("does not link a key inside a longer word", () => {
    expect(splitChatTicketKeys("XALP-6", linked)).toEqual([{ kind: "text", text: "XALP-6" }]);
  });

  it("returns the text whole without linked keys", () => {
    expect(splitChatTicketKeys("ALP-6", new Set())).toEqual([{ kind: "text", text: "ALP-6" }]);
  });
});
