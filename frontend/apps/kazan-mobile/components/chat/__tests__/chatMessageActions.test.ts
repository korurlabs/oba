import { describe, expect, it } from "bun:test";

import {
  chatCanWrite,
  chatMessageActions,
  chatMessageWebLink,
} from "@/components/chat/chatMessageActions";

import { ALICE, ME, chatChannel, chatMessage } from "./chatFixtures";

const own = chatMessage({
  author: { userId: ME, email: "me@example.com", firstName: "Me", lastName: null },
});

describe("chatMessageActions", () => {
  it("offers a member everything on their own message", () => {
    expect(chatMessageActions(own, { meId: ME, role: "MEMBER" })).toEqual([
      "reply",
      "react",
      "thread",
      "pin",
      "copyText",
      "copyLink",
      "edit",
      "delete",
    ]);
  });

  it("offers a member no pin, edit or delete on someone else's message", () => {
    expect(chatMessageActions(chatMessage(), { meId: ME, role: "MEMBER" })).toEqual([
      "reply",
      "react",
      "thread",
      "copyText",
      "copyLink",
    ]);
  });

  it("lets an owner or admin pin and delete anyone's message, but not edit it", () => {
    for (const role of ["OWNER", "ADMIN"]) {
      expect(chatMessageActions(chatMessage(), { meId: ME, role })).toEqual([
        "reply",
        "react",
        "thread",
        "pin",
        "copyText",
        "copyLink",
        "delete",
      ]);
    }
  });

  it("leaves a guest only the copy actions", () => {
    expect(chatMessageActions(own, { meId: ME, role: "GUEST" })).toEqual(["copyText", "copyLink"]);
    expect(chatCanWrite("GUEST")).toBe(false);
    expect(chatCanWrite(null)).toBe(false);
  });

  it("never edits or deletes a daily update or a pull request share", () => {
    const update = chatMessage({
      author: own.author,
      content: "",
      dailyUpdateId: "u1",
      dailyUpdate: { summaryText: "Working on KZN-1" },
    });
    expect(chatMessageActions(update, { meId: ME, role: "OWNER" })).toEqual([
      "reply",
      "react",
      "thread",
      "pin",
      "copyText",
      "copyLink",
    ]);
  });

  it("opens a thread from a root, starts one on a channel message, never on a reply", () => {
    const root = chatMessage({
      thread: {
        threadId: "t1",
        title: "Root",
        messageCount: 2,
        lastMessageAt: "2026-11-01T10:00:00Z",
        status: "ACTIVE",
        memberPreviewIds: [],
        memberCount: 1,
      },
    });
    expect(chatMessageActions(root, { meId: ME, role: "GUEST" })).toContain("thread");
    expect(chatMessageActions(chatMessage(), { meId: ME, role: "GUEST" })).not.toContain("thread");
    expect(
      chatMessageActions(chatMessage({ threadId: "t1" }), { meId: ME, role: "MEMBER" })
    ).not.toContain("thread");
  });

  it("takes no reply and no edit in a closed thread; reactions, pins and delete stay", () => {
    const reply = chatMessage({ author: own.author, threadId: "t1" });
    expect(chatMessageActions(reply, { meId: ME, role: "MEMBER", closed: true })).toEqual([
      "react",
      "pin",
      "copyText",
      "copyLink",
      "delete",
    ]);
  });

  it("offers no copy of text when there is none, and nothing on a deleted message", () => {
    expect(
      chatMessageActions(chatMessage({ content: "  " }), { meId: ME, role: "MEMBER" })
    ).not.toContain("copyText");
    expect(
      chatMessageActions(chatMessage({ deleted: true }), { meId: ALICE, role: "OWNER" })
    ).toEqual([]);
  });
});

describe("chatMessageWebLink", () => {
  it("opens a channel message by its slug with jump", () => {
    expect(
      chatMessageWebLink(
        "https://kazan.korur.com/",
        chatMessage({ id: "m 1" }),
        chatChannel({ slug: "dev-ops" })
      )
    ).toBe("https://kazan.korur.com/chats/dev-ops?jump=m%201");
  });

  it("opens a thread message with thread and threadMessage", () => {
    expect(
      chatMessageWebLink(
        "https://kazan.korur.com",
        chatMessage({ id: "m2", threadId: "t1" }),
        chatChannel()
      )
    ).toBe("https://kazan.korur.com/chats/general?thread=t1&threadMessage=m2");
  });

  it("uses the fixed paths of the daily updates and pull requests channels", () => {
    const message = chatMessage({ id: "m3" });
    expect(
      chatMessageWebLink("https://k.io", message, chatChannel({ purpose: "DAILY_UPDATES" }))
    ).toBe("https://k.io/daily-updates?jump=m3");
    expect(
      chatMessageWebLink("https://k.io", message, chatChannel({ purpose: "PULL_REQUESTS" }))
    ).toBe("https://k.io/pull-requests?jump=m3");
  });
});
