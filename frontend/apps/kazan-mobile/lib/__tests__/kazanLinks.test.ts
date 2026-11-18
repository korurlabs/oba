import { describe, expect, it } from "bun:test";

import { sprintForView } from "@/components/boards/boardModel";

import {
  chatLinkHref,
  chatLinkLookupOutcome,
  chatLinkOutcome,
  findChatMessageLinks,
  findLinkChannel,
  kazanLinkDirectPath,
  linkedMessageInConversation,
  linkScreenPath,
  linkSprintId,
  parseChatMessageLink,
  parseKazanLink,
} from "@/lib/kazanLinks";
import type { Sprint } from "@/lib/schemas/boards";

const HOST = "kazan.test";
const MESSAGE = "0a1b2c3d-0000-4000-8000-000000000001";
const THREAD = "0a1b2c3d-0000-4000-8000-000000000002";
const CHANNEL = "0a1b2c3d-0000-4000-8000-000000000003";

describe("parseChatMessageLink", () => {
  it("reads a channel message link as the web copies it, with or without a locale", () => {
    expect(parseChatMessageLink(`https://${HOST}/chats/general?jump=${MESSAGE}`)).toEqual({
      href: `https://${HOST}/chats/general?jump=${MESSAGE}`,
      messageId: MESSAGE,
      threadId: null,
      channel: { slug: "general" },
    });
    expect(
      parseChatMessageLink(`https://${HOST}/tr/chats/general?jump=${MESSAGE}`)?.messageId
    ).toBe(MESSAGE);
  });

  it("reads a thread message link and the purpose channels", () => {
    expect(
      parseChatMessageLink(
        `https://${HOST}/daily-updates?thread=${THREAD}&threadMessage=${MESSAGE}`
      )
    ).toMatchObject({
      messageId: MESSAGE,
      threadId: THREAD,
      channel: { purpose: "DAILY_UPDATES" },
    });
    expect(parseChatMessageLink(`https://${HOST}/pull-requests/?jump=${MESSAGE}`)?.channel).toEqual(
      {
        purpose: "PULL_REQUESTS",
      }
    );
  });

  it("reads an old link by channel id, and one with no channel", () => {
    expect(
      parseChatMessageLink(`https://${HOST}/dashboard/chat?channelId=${CHANNEL}&jump=${MESSAGE}`)
        ?.channel
    ).toEqual({ id: CHANNEL });
    expect(parseChatMessageLink(`https://${HOST}/dashboard/updates?jump=${MESSAGE}`)?.channel).toBe(
      null
    );
  });

  it("recognises a link of another deployment, as the web does", () => {
    expect(parseChatMessageLink(`https://other.example/chats/x?jump=${MESSAGE}`)?.messageId).toBe(
      MESSAGE
    );
  });

  it("rejects links that point at no message", () => {
    expect(parseChatMessageLink(`https://${HOST}/chats/general`)).toBeNull();
    expect(parseChatMessageLink(`https://${HOST}/chats/general?jump=not-a-uuid`)).toBeNull();
    expect(parseChatMessageLink(`https://${HOST}/chats/general?thread=${THREAD}`)).toBeNull();
    expect(parseChatMessageLink(`https://${HOST}/board/alpha?jump=${MESSAGE}`)).toBeNull();
    expect(parseChatMessageLink(`ftp://${HOST}/chats/general?jump=${MESSAGE}`)).toBeNull();
  });
});

describe("findChatMessageLinks", () => {
  it("finds every message link in text, without trailing punctuation", () => {
    const text = `See https://${HOST}/chats/general?jump=${MESSAGE}. And https://example.com too, and https://${HOST}/daily-updates?jump=${THREAD})`;
    expect(findChatMessageLinks(text).map((link) => link.messageId)).toEqual([MESSAGE, THREAD]);
  });
});

describe("parseKazanLink", () => {
  it("opens tickets from the short and the board ticket page", () => {
    expect(parseKazanLink(`https://${HOST}/ticket/alp-4`, HOST)).toEqual({
      kind: "ticket",
      ticketKey: "ALP-4",
    });
    expect(parseKazanLink(`https://${HOST}/en/board/alpha/ticket/ALP-4`, HOST)).toEqual({
      kind: "ticket",
      ticketKey: "ALP-4",
    });
    expect(parseKazanLink(`https://${HOST}/en/ticket/new`, HOST)).toEqual({ kind: "newTicket" });
  });

  it("opens a board at the view its page names", () => {
    expect(parseKazanLink(`https://${HOST}/en/board/alpha/roadmap`, HOST)).toEqual({
      kind: "board",
      boardSlug: "alpha",
      view: "roadmap",
      sprint: null,
    });
    for (const view of ["all", "sprint", "backlog"] as const) {
      expect(parseKazanLink(`https://${HOST}/en/board/alpha/${view}`, HOST)).toEqual({
        kind: "board",
        boardSlug: "alpha",
        view,
        sprint: null,
      });
    }
  });

  it("opens a bare board link on All, as the web redirects it", () => {
    expect(parseKazanLink(`https://${HOST}/board/alpha`, HOST)).toEqual({
      kind: "board",
      boardSlug: "alpha",
      view: "all",
      sprint: null,
    });
    expect(parseKazanLink(`https://${HOST}/en/board/alpha?q=login`, HOST)).toMatchObject({
      view: "all",
    });
  });

  it("opens a sprint's own page on the sprint view, on that sprint", () => {
    expect(parseKazanLink(`https://${HOST}/board/alpha/sprint-12`, HOST)).toEqual({
      kind: "board",
      boardSlug: "alpha",
      view: "sprint",
      sprint: { slug: "sprint-12" },
    });
    expect(parseKazanLink(`https://${HOST}/nl/board/alpha/sprint-12/?q=x#top`, HOST)).toEqual({
      kind: "board",
      boardSlug: "alpha",
      view: "sprint",
      sprint: { slug: "sprint-12" },
    });
  });

  it("reads the sprint of a Sprint tab link by ID and of an All link by slug", () => {
    expect(parseKazanLink(`https://${HOST}/en/board/alpha/sprint?sprintId=s1`, HOST)).toMatchObject(
      { view: "sprint", sprint: { id: "s1" } }
    );
    expect(parseKazanLink(`https://${HOST}/board/alpha/all?sprint=sprint-12`, HOST)).toMatchObject({
      view: "all",
      sprint: { slug: "sprint-12" },
    });
    expect(parseKazanLink(`https://${HOST}/board/alpha/backlog?sprintId=s1`, HOST)).toMatchObject({
      view: "backlog",
      sprint: null,
    });
  });

  it("opens channels, threads, chat home and the chat forms", () => {
    expect(parseKazanLink(`https://${HOST}/chats/general`, HOST)).toEqual({
      kind: "channel",
      channel: { slug: "general" },
      messageId: null,
      threadId: null,
    });
    expect(parseKazanLink(`https://${HOST}/chats/general?thread=${THREAD}`, HOST)).toMatchObject({
      threadId: THREAD,
    });
    expect(parseKazanLink(`https://${HOST}/chats`, HOST)).toEqual({ kind: "chatHome" });
    expect(parseKazanLink(`https://${HOST}/daily-updates/new`, HOST)).toEqual({
      kind: "newUpdate",
    });
    expect(parseKazanLink(`https://${HOST}/pull-requests/new`, HOST)).toEqual({
      kind: "sharePullRequest",
    });
    expect(parseKazanLink(`https://${HOST}/pull-requests/abc/edit`, HOST)).toMatchObject({
      kind: "channel",
      channel: { purpose: "PULL_REQUESTS" },
    });
  });

  it("opens the fixed tabs", () => {
    expect(parseKazanLink(`https://${HOST}/en/home`, HOST)).toEqual({ kind: "home" });
    expect(parseKazanLink(`https://${HOST}/nl`, HOST)).toEqual({ kind: "home" });
    expect(parseKazanLink(`https://${HOST}/incidents`, HOST)).toEqual({ kind: "incidents" });
  });

  it("leaves pages the app has no screen for, and other hosts, to the browser", () => {
    expect(parseKazanLink(`https://${HOST}/en/tickets`, HOST)).toBeNull();
    expect(parseKazanLink(`https://${HOST}/downloads`, HOST)).toBeNull();
    expect(parseKazanLink("https://example.com/ticket/ALP-4", HOST)).toBeNull();
    expect(parseKazanLink(`https://${HOST}/ticket/ALP-4`, null)).toBeNull();
    expect(parseKazanLink("mailto:a@b.c", HOST)).toBeNull();
  });
});

describe("kazanLinkDirectPath", () => {
  it("maps the targets that need no lookup to app paths", () => {
    expect(kazanLinkDirectPath({ kind: "ticket", ticketKey: "ALP-4" })).toBe("/ticket/ALP-4");
    expect(kazanLinkDirectPath({ kind: "chatHome" })).toBe("/chat");
    expect(kazanLinkDirectPath({ kind: "newUpdate" })).toBe("/chat/new-update");
    expect(kazanLinkDirectPath({ kind: "settings" })).toBe("/account/settings");
  });

  it("leaves boards and channels to the link screen", () => {
    expect(
      kazanLinkDirectPath({ kind: "board", boardSlug: "a", view: "sprint", sprint: null })
    ).toBeNull();
    expect(
      kazanLinkDirectPath({ kind: "channel", channel: null, messageId: MESSAGE, threadId: null })
    ).toBeNull();
    expect(linkScreenPath(`https://${HOST}/chats/a?jump=${MESSAGE}`)).toBe(
      `/link?url=${encodeURIComponent(`https://${HOST}/chats/a?jump=${MESSAGE}`)}`
    );
  });
});

describe("findLinkChannel and chatLinkHref", () => {
  const channels = [
    { id: CHANNEL, slug: "general", purpose: "GENERAL" },
    { id: "u", slug: "daily", purpose: "DAILY_UPDATES" },
  ];

  it("finds the channel by id, slug or purpose", () => {
    expect(findLinkChannel(channels, { id: CHANNEL })?.slug).toBe("general");
    expect(findLinkChannel(channels, { slug: "general" })?.id).toBe(CHANNEL);
    expect(findLinkChannel(channels, { purpose: "DAILY_UPDATES" })?.id).toBe("u");
    expect(findLinkChannel(channels, { slug: "gone" })).toBeUndefined();
  });

  it("opens a message in the channel screen, a thread alone in the thread, a purpose channel on its screen", () => {
    expect(chatLinkHref({ id: "u", purpose: "DAILY_UPDATES" }, MESSAGE, THREAD)).toEqual({
      pathname: "/chat/channel/[channelId]",
      params: { channelId: "u", message: MESSAGE, thread: THREAD },
    });
    expect(chatLinkHref({ id: CHANNEL, purpose: "GENERAL" }, null, THREAD)).toEqual({
      pathname: "/chat/thread/[threadId]",
      params: { threadId: THREAD, channelId: CHANNEL },
    });
    expect(chatLinkHref({ id: "u", purpose: "DAILY_UPDATES" }, null, null)).toEqual({
      pathname: "/chat/updates",
    });
    expect(chatLinkHref({ id: "p", purpose: "PULL_REQUESTS" }, null, null)).toEqual({
      pathname: "/chat/pull-requests",
    });
    expect(chatLinkHref({ id: CHANNEL, purpose: "GENERAL" }, null, null)).toEqual({
      pathname: "/chat/channel/[channelId]",
      params: { channelId: CHANNEL },
    });
  });
});

describe("linkedMessageInConversation", () => {
  const general = { id: CHANNEL, slug: "general", purpose: "GENERAL" };

  it("scrolls in place for a message of the open channel or thread", () => {
    expect(
      linkedMessageInConversation(
        { channel: { slug: "general" }, messageId: MESSAGE, threadId: null },
        general,
        null
      )
    ).toBe(MESSAGE);
    expect(
      linkedMessageInConversation(
        { channel: { id: CHANNEL }, messageId: MESSAGE, threadId: THREAD },
        general,
        THREAD
      )
    ).toBe(MESSAGE);
  });

  it("opens other channels, other threads, channel links and old links elsewhere", () => {
    const base = { channel: { slug: "general" }, messageId: MESSAGE, threadId: null };
    expect(linkedMessageInConversation({ ...base, channel: { slug: "x" } }, general, null)).toBe(
      null
    );
    expect(linkedMessageInConversation({ ...base, threadId: THREAD }, general, null)).toBe(null);
    expect(linkedMessageInConversation(base, general, THREAD)).toBe(null);
    expect(linkedMessageInConversation({ ...base, messageId: null }, general, null)).toBe(null);
    expect(linkedMessageInConversation({ ...base, channel: null }, general, null)).toBe(null);
    expect(linkedMessageInConversation(base, undefined, null)).toBe(null);
  });
});

describe("chatLinkOutcome", () => {
  const target = { messageId: MESSAGE, threadId: THREAD };

  it("opens a channel the reader is on at the linked message", () => {
    expect(chatLinkOutcome({ id: CHANNEL, purpose: "GENERAL", member: true }, target)).toEqual({
      kind: "open",
      href: {
        pathname: "/chat/channel/[channelId]",
        params: { channelId: CHANNEL, message: MESSAGE, thread: THREAD },
      },
    });
  });

  it("opens a listed private channel the reader is not on in its own screen, without the message", () => {
    expect(chatLinkOutcome({ id: CHANNEL, purpose: "GENERAL", member: false }, target)).toEqual({
      kind: "open",
      href: { pathname: "/chat/channel/[channelId]", params: { channelId: CHANNEL } },
    });
  });

  it("finds no channel the reader's list does not hold", () => {
    expect(chatLinkOutcome(undefined, target)).toEqual({ kind: "channelNotFound" });
  });

  it("reads a refused message lookup by its error code", () => {
    const refusal = (code: string | undefined) => ({ code, errorValues: {} });
    expect(chatLinkLookupOutcome(refusal("KAZ_NOT_015"), [])).toEqual({ kind: "channelNotFound" });
    expect(chatLinkLookupOutcome(refusal("KAZ_NOT_013"), [])).toEqual({ kind: "messageGone" });
    // A network failure or another refusal says nothing about the link.
    expect(chatLinkLookupOutcome(refusal(undefined), [])).toBe(null);
    expect(chatLinkLookupOutcome(refusal("GEN_API_004"), [])).toBe(null);
  });

  it("opens the not-member screen of a listed channel a refused message lookup names", () => {
    const refusal = { code: "KAZ_NOT_015", errorValues: { channelId: CHANNEL } };
    expect(
      chatLinkLookupOutcome(refusal, [{ id: CHANNEL, purpose: "GENERAL", member: false }])
    ).toEqual({
      kind: "open",
      href: { pathname: "/chat/channel/[channelId]", params: { channelId: CHANNEL } },
    });
  });

  it("finds no channel a refused message lookup names when the reader's list does not hold it", () => {
    const refusal = { code: "KAZ_NOT_015", errorValues: { channelId: CHANNEL } };
    expect(chatLinkLookupOutcome(refusal, [])).toEqual({ kind: "channelNotFound" });
    // Listed as a member, yet refused: the list is out of date, and the channel cannot be read.
    expect(
      chatLinkLookupOutcome(refusal, [{ id: CHANNEL, purpose: "GENERAL", member: true }])
    ).toEqual({ kind: "channelNotFound" });
  });
});

describe("linkSprintId", () => {
  const sprint = (id: string, slug: string | null, status: string): Sprint => ({
    id,
    name: id,
    slug,
    status,
    startDate: null,
    endDate: null,
  });
  const sprints: Sprint[] = [
    sprint("s-active", "sprint-13", "ACTIVE"),
    sprint("s-done", "sprint-12", "COMPLETED"),
    sprint("s-old", null, "COMPLETED"),
  ];

  it("finds the linked sprint by slug or by ID", () => {
    expect(linkSprintId(sprints, { slug: "sprint-12" })).toBe("s-done");
    expect(linkSprintId(sprints, { id: "s-active" })).toBe("s-active");
  });

  it("names no sprint for a deleted or unknown sprint, so the board opens its default", () => {
    expect(linkSprintId(sprints, { slug: "sprint-99" })).toBeNull();
    expect(linkSprintId(sprints, { id: "s-gone" })).toBeNull();
    expect(linkSprintId([], { slug: "sprint-12" })).toBeNull();
    expect(linkSprintId(sprints, null)).toBeNull();
  });

  it("opens the unknown sprint's board on the default sprint", () => {
    const chosen: string | null = linkSprintId(sprints, { slug: "sprint-99" });
    expect(sprintForView("sprint", sprints, chosen)?.id).toBe("s-active");
    expect(sprintForView("sprint", sprints, linkSprintId(sprints, { slug: "sprint-12" }))?.id).toBe(
      "s-done"
    );
  });
});
