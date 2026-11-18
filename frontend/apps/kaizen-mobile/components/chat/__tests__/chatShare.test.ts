import { describe, expect, it } from "bun:test";

import { chatComposerDraft, serializeChatComposerText } from "@/components/chat/chatMentions";
import {
  chatShareChannelHref,
  chatSharedTicketFromParams,
  chatShareTicketDraft,
  chatShareTargets,
  chatShareTicketHref,
} from "@/components/chat/chatShare";
import { parseChatTokens, type ChatTicketRefResponse } from "@/lib/schemas/chat";

import { TICKET, chatChannel, chatMessage } from "./chatFixtures";

/** A reference as the server returns it for a `<#ticketId>` token. */
function ref(): ChatTicketRefResponse {
  return {
    ticketId: TICKET,
    boardId: "55555555-5555-4555-8555-555555555555",
    ticketKey: "ALP-6",
    ticketTitle: "Fix the login redirect",
    canOpen: true,
  };
}

describe("Share to chat", () => {
  const ticket = { id: TICKET, key: "ALP-6", title: "Fix the login redirect" };

  it("prefills the composer with the ticket key and sends the web's token", () => {
    const draft = chatShareTicketDraft(ticket);
    expect(draft.text).toBe("ALP-6 ");
    expect(serializeChatComposerText(draft.text, draft.picks)).toBe(`<#${TICKET}> `);
  });

  it("keeps the chip while the reader types around it", () => {
    const draft = chatShareTicketDraft(ticket);
    const typed = `Please look at ${draft.text}today`;
    const content: string = serializeChatComposerText(typed, draft.picks);
    expect(content).toBe(`Please look at <#${TICKET}> today`);
    expect(parseChatTokens(content).ticketIds).toEqual([TICKET]);
  });

  it("round-trips a web-sent ticket token through the composer", () => {
    const message = chatMessage({ content: `Look at <#${TICKET}> please`, ticketRefs: [ref()] });
    const draft = chatComposerDraft(message, "Unknown");
    expect(draft.text).toBe("Look at ALP-6 please");
    expect(serializeChatComposerText(draft.text, draft.picks)).toBe(message.content);
  });

  it("builds the picker and channel routes with the ticket", () => {
    expect(chatShareTicketHref(ticket)).toEqual({
      pathname: "/share/ticket",
      params: { ticketId: TICKET, ticketKey: "ALP-6", ticketTitle: "Fix the login redirect" },
    });
    expect(chatShareChannelHref("c1", ticket)).toEqual({
      pathname: "/share/channel/[channelId]",
      params: { channelId: "c1", ticketId: TICKET, ticketKey: "ALP-6" },
    });
  });

  it("reads the ticket back from route params and refuses malformed ones", () => {
    expect(chatSharedTicketFromParams({ ticketId: TICKET, ticketKey: "ALP-6" })).toEqual({
      id: TICKET,
      key: "ALP-6",
    });
    expect(chatSharedTicketFromParams({ ticketId: "x", ticketKey: "ALP-6" })).toBeNull();
    expect(chatSharedTicketFromParams({ ticketId: TICKET, ticketKey: "not a key" })).toBeNull();
  });
});

describe("chatShareTargets", () => {
  const ordered = [
    chatChannel({ id: "dev", name: "Dev-Ops" }),
    chatChannel({ id: "updates", name: "updates", purpose: "DAILY_UPDATES" }),
    chatChannel({ id: "random", name: "random", member: false }),
    chatChannel({ id: "ops", name: "ops-alerts" }),
    chatChannel({ id: "general", name: "general" }),
  ];
  const ids = (search: string): string[] =>
    chatShareTargets(ordered, search).map((channel) => channel.id);

  it("offers the general channels the reader belongs to, in the reader's order", () => {
    expect(ids("")).toEqual(["dev", "ops", "general"]);
  });

  it("puts names that start with the search before names that only hold it", () => {
    expect(ids(" OPS ")).toEqual(["ops", "dev"]);
  });

  it("keeps the reader's order within each group and offers nothing that does not match", () => {
    expect(ids("e")).toEqual(["dev", "ops", "general"]);
    expect(ids("upd")).toEqual([]);
    expect(ids("rand")).toEqual([]);
  });
});
