import type {
  ChatChannelResponse,
  ChatMessageResponse,
  ChatOutboxMessage,
  ChatThreadResponse,
} from "@/lib/schemas/chat";

export const ORG_ID = "org-1";
export const CHANNEL_ID = "channel-1";
export const ME = "11111111-1111-4111-8111-111111111111";
export const ALICE = "22222222-2222-4222-8222-222222222222";
export const TICKET = "33333333-3333-4333-8333-333333333333";

/** A channel message by Alice; override what the case is about. */
export function chatMessage(overrides: Partial<ChatMessageResponse> = {}): ChatMessageResponse {
  return {
    id: "m1",
    organizationId: ORG_ID,
    channelId: CHANNEL_ID,
    threadId: null,
    dailyUpdateId: null,
    pullRequestShareId: null,
    deleted: false,
    author: { userId: ALICE, email: "alice@example.com", firstName: "Alice", lastName: "Akın" },
    content: "hello",
    createdAt: "2026-11-05T10:00:00.000Z",
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
    thread: null,
    dailyUpdate: null,
    pullRequestShare: null,
    users: {
      [ALICE]: {
        userId: ALICE,
        firstName: "Alice",
        lastName: "Akın",
        email: "alice@example.com",
        member: true,
      },
    },
    version: "2026-11-05T10:00:00.000000001Z",
    ...overrides,
  };
}

export function chatChannel(overrides: Partial<ChatChannelResponse> = {}): ChatChannelResponse {
  return {
    id: CHANNEL_ID,
    name: "general",
    slug: "general",
    type: "CHANNEL",
    purpose: "GENERAL",
    visibility: "PUBLIC",
    member: true,
    memberCount: 3,
    createdAt: "2026-01-01T00:00:00Z",
    ...overrides,
  };
}

/** An active thread Alice started in the channel; override what the case is about. */
export function chatThread(overrides: Partial<ChatThreadResponse> = {}): ChatThreadResponse {
  return {
    id: "t1",
    channelId: CHANNEL_ID,
    rootMessageId: "m1",
    rootMessage: chatMessage(),
    title: "Release checklist",
    createdBy: ALICE,
    messageCount: 2,
    lastMessageAt: "2026-11-05T10:30:00Z",
    autoArchiveMinutes: 10080,
    status: "ACTIVE",
    statusChangedAt: null,
    statusChangedBy: null,
    member: true,
    members: [
      {
        userId: ALICE,
        firstName: "Alice",
        lastName: "Akın",
        email: "alice@example.com",
        member: true,
      },
    ],
    canManage: false,
    ...overrides,
  };
}

export function chatOutboxRow(overrides: Partial<ChatOutboxMessage> = {}): ChatOutboxMessage {
  return {
    clientNonce: "nonce-1",
    organizationId: ORG_ID,
    channelId: CHANNEL_ID,
    threadId: null,
    content: "on its way",
    replyToMessageId: null,
    files: [],
    progress: null,
    createdAt: "2026-11-05T10:05:00.000Z",
    status: "pending",
    errorCode: null,
    failedByNetwork: false,
    ...overrides,
  };
}
