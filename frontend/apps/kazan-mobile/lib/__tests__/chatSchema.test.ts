import { describe, expect, it } from "bun:test";

import {
  CHAT_MAX_TICKET_REFS,
  CHAT_MAX_USER_MENTIONS,
  CHAT_MESSAGE_MAX_LENGTH,
  chatAttachmentResponseSchema,
  chatComposerProblem,
  chatMessagePageSchema,
  chatThreadResponseSchema,
  threadRootAsMessage,
  type ChatThreadResponse,
} from "@/lib/schemas/chat";

const uuid = (index: number): string =>
  `00000000-0000-4000-8000-${String(index).padStart(12, "0")}`;

describe("chatComposerProblem", () => {
  it("accepts ordinary text", () => {
    expect(chatComposerProblem("hello")).toBeNull();
  });

  it("refuses blank text and text over the limit", () => {
    expect(chatComposerProblem("  \n ")).toBe("empty");
    expect(chatComposerProblem("x".repeat(CHAT_MESSAGE_MAX_LENGTH))).toBeNull();
    expect(chatComposerProblem("x".repeat(CHAT_MESSAGE_MAX_LENGTH + 1))).toBe("tooLong");
  });

  it("refuses too many distinct mentions or ticket references", () => {
    const mentions: string = Array.from(
      { length: CHAT_MAX_USER_MENTIONS + 1 },
      (_, index) => `<@${uuid(index)}>`
    ).join(" ");
    expect(chatComposerProblem(mentions)).toBe("tooManyMentions");
    const tickets: string = Array.from(
      { length: CHAT_MAX_TICKET_REFS + 1 },
      (_, index) => `<#${uuid(index)}>`
    ).join(" ");
    expect(chatComposerProblem(tickets)).toBe("tooManyTicketRefs");
  });
});

/**
 * `GET /kazan/chat/threads/{threadId}` from the local backend, trimmed to one
 * member, with the other member's name replaced. The root and organization
 * ids are seeded ones without an RFC 9562 version nibble.
 */
const SEEDED_ORGANIZATION_ID = "b0000000-0000-0000-0000-000000000001";
const SEEDED_ROOT_ID = "a5600000-0000-0000-0000-000000000033";
const CHANNEL_ID = "a3fb4f65-08ef-4c2f-8204-3d2dd6d0860c";
const THREAD_ID = "d5711f45-2568-40fc-af78-07e227e1ee46";
const AUTHOR_ID = "a5600000-0000-0000-0000-000000000001";

function realRootMessage(): Record<string, unknown> {
  return {
    id: SEEDED_ROOT_ID,
    organizationId: SEEDED_ORGANIZATION_ID,
    channelId: CHANNEL_ID,
    threadId: null,
    deleted: false,
    author: { userId: AUTHOR_ID, email: "ada@example.com", firstName: "Ada", lastName: "Lovelace" },
    content: "Could you please review the onboarding checklist before Friday",
    createdAt: "2026-11-05T13:23:44.740239Z",
    editedAt: null,
    pinnedAt: null,
    clientNonce: null,
    replyTo: null,
    mentionedUserIds: [],
    mentionsEveryone: false,
    mentionsHere: false,
    mentionsMe: false,
    repliesToMe: false,
    ticketRefs: [],
    linkedTickets: [],
    attachments: [],
    reactions: [],
    thread: {
      threadId: THREAD_ID,
      title: "Onboarding checklist review",
      messageCount: 6,
      lastMessageAt: "2026-11-05T13:27:19.262048Z",
      status: "ACTIVE",
      memberPreviewIds: [AUTHOR_ID],
      memberCount: 1,
    },
    dailyUpdateId: null,
    dailyUpdate: null,
    pullRequestShareId: null,
    pullRequestShare: null,
    users: {
      [AUTHOR_ID]: {
        userId: AUTHOR_ID,
        firstName: "Ada",
        lastName: "Lovelace",
        email: "ada@example.com",
        member: true,
      },
    },
    version: "2026-11-05T13:23:44.740239Z",
  };
}

function realThread(rootMessage: Record<string, unknown>): Record<string, unknown> {
  return {
    id: THREAD_ID,
    channelId: CHANNEL_ID,
    rootMessageId: SEEDED_ROOT_ID,
    rootMessage,
    title: "Onboarding checklist review",
    createdBy: AUTHOR_ID,
    messageCount: 6,
    lastMessageAt: "2026-11-05T13:27:19.262048Z",
    autoArchiveMinutes: 10080,
    status: "ACTIVE",
    statusChangedAt: null,
    statusChangedBy: null,
    member: true,
    members: [
      {
        userId: AUTHOR_ID,
        firstName: "Ada",
        lastName: "Lovelace",
        email: "ada@example.com",
        member: true,
      },
    ],
    canManage: true,
  };
}

/**
 * The placeholder the backend answers for a thread root whose row is gone:
 * no channel, creation time, author, text or version, only the thread summary.
 */
function gonePlaceholderRoot(): Record<string, unknown> {
  return {
    ...realRootMessage(),
    channelId: null,
    deleted: true,
    author: null,
    content: "",
    createdAt: null,
    version: null,
    users: {},
  };
}

describe("chatThreadResponseSchema", () => {
  it("accepts the backend thread with seeded ids", () => {
    const thread: ChatThreadResponse = chatThreadResponseSchema.parse(
      realThread(realRootMessage())
    );
    expect(thread.rootMessage.id).toBe(SEEDED_ROOT_ID);
  });

  it("accepts a root placeholder without channel and creation time", () => {
    const thread: ChatThreadResponse = chatThreadResponseSchema.parse(
      realThread(gonePlaceholderRoot())
    );
    expect(thread.rootMessage.channelId).toBeNull();
    expect(thread.rootMessage.createdAt).toBeNull();
  });

  it("still requires channel and creation time of a page message", () => {
    expect(
      chatMessagePageSchema.safeParse({
        items: [gonePlaceholderRoot()],
        hasOlder: false,
        hasNewer: false,
      }).success
    ).toBe(false);
  });
});

describe("threadRootAsMessage", () => {
  it("returns the root as a channel message", () => {
    const thread: ChatThreadResponse = chatThreadResponseSchema.parse(
      realThread(realRootMessage())
    );
    expect(threadRootAsMessage(thread)?.createdAt).toBe("2026-11-05T13:23:44.740239Z");
  });

  it("treats a root placeholder without a row as not loaded", () => {
    const thread: ChatThreadResponse = chatThreadResponseSchema.parse(
      realThread(gonePlaceholderRoot())
    );
    expect(threadRootAsMessage(thread)).toBeNull();
  });
});

describe("chatAttachmentResponseSchema", () => {
  it("accepts an attachment of a seeded message id", () => {
    const attachment = chatAttachmentResponseSchema.parse({
      id: "a5600000-0000-0000-0000-000000000090",
      url: "https://example.blob.core.windows.net/village-container/file.png?sig=x",
      fileName: "file.png",
      contentType: "image/png",
      fileSize: 2048,
      entityType: "CHAT_MESSAGE",
      entityId: SEEDED_ROOT_ID,
      createdAt: "2026-11-05T13:23:44.740239Z",
      width: 640,
      height: 480,
      documentId: null,
    });
    expect(attachment.entityId).toBe(SEEDED_ROOT_ID);
  });
});
