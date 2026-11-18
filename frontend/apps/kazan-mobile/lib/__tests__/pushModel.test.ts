import { describe, expect, it } from "bun:test";

import {
  backgroundPushStep,
  foregroundDecision,
  openConversationFromRoute,
  parsePushData,
  pushDeviceRequest,
  pushHref,
  pushInConversation,
  pushLocale,
  pushOrganizationStep,
  pushPermissionStep,
  pushSentAt,
  readBackgroundPushData,
  type OpenConversation,
} from "@/lib/pushModel";
import type { PushData } from "@/lib/schemas/push";

const ORG = "org-1";

function push(overrides: Partial<PushData> = {}): PushData {
  return {
    type: "MENTION",
    organizationId: ORG,
    channelId: "ch-1",
    threadId: null,
    messageId: "m-1",
    ticketKey: null,
    conversation: "#general",
    sentAt: "2026-11-09T09:41:00Z",
    ...overrides,
  };
}

describe("parsePushData", () => {
  it("reads the server payload with every key", () => {
    expect(
      parsePushData({
        type: "REPLY",
        organizationId: ORG,
        channelId: "ch-1",
        threadId: "th-1",
        messageId: "m-1",
        ticketKey: null,
        conversation: "KZN-311 root cause · #general",
        sentAt: "2026-11-09T09:41:00Z",
      })
    ).toEqual(
      push({ type: "REPLY", threadId: "th-1", conversation: "KZN-311 root cause · #general" })
    );
  });

  it("reads missing keys as null", () => {
    expect(parsePushData({ type: "NUDGE", organizationId: ORG })).toEqual({
      type: "NUDGE",
      organizationId: ORG,
      channelId: null,
      threadId: null,
      messageId: null,
      ticketKey: null,
      conversation: null,
      sentAt: null,
    });
  });

  it("rejects a payload that is not a Kazan push", () => {
    expect(parsePushData({})).toBeNull();
    expect(parsePushData({ type: "OTHER", organizationId: ORG })).toBeNull();
    expect(parsePushData(null)).toBeNull();
  });
});

describe("pushHref", () => {
  it("opens a channel message", () => {
    expect(pushHref(push())).toEqual({
      pathname: "/chat/channel/[channelId]",
      params: { channelId: "ch-1", message: "m-1" },
    });
  });

  it("opens a thread reply through its channel", () => {
    for (const type of ["REPLY", "EVERYONE", "HERE"] as const) {
      expect(pushHref(push({ type, threadId: "th-1" }))).toEqual({
        pathname: "/chat/channel/[channelId]",
        params: { channelId: "ch-1", message: "m-1", thread: "th-1" },
      });
    }
  });

  it("opens the pull requests channel for a review request", () => {
    expect(pushHref(push({ type: "REVIEW" }))).toEqual({ pathname: "/chat/pull-requests" });
  });

  it("opens nothing for a chat push without a channel", () => {
    expect(pushHref(push({ channelId: null }))).toBeNull();
  });

  it("opens the incident's ticket, or the Incidents tab without a key", () => {
    expect(pushHref(push({ type: "INCIDENT", channelId: null, ticketKey: "KZN-311" }))).toEqual({
      pathname: "/ticket/[ticketKey]",
      params: { ticketKey: "KZN-311" },
    });
    expect(pushHref(push({ type: "INCIDENT", channelId: null }))).toEqual({
      pathname: "/incidents",
    });
  });

  it("opens the daily updates for a nudge and nothing for a read signal", () => {
    expect(pushHref(push({ type: "NUDGE", channelId: null }))).toEqual({
      pathname: "/chat/updates",
    });
    expect(pushHref(push({ type: "READ" }))).toBeNull();
  });
});

describe("pushOrganizationStep", () => {
  const access = (memberIds: string[], kazanIds: string[] = memberIds) => ({
    memberIds,
    kazanIds,
  });

  it("stays in the active organization", () => {
    expect(pushOrganizationStep(push(), ORG, access([ORG]))).toBe("stay");
  });

  it("switches to another organization of the user before navigating", () => {
    expect(
      pushOrganizationStep(push({ organizationId: "org-2" }), ORG, access([ORG, "org-2"]))
    ).toBe("switch");
    expect(pushOrganizationStep(push({ organizationId: "org-2" }), ORG, null)).toBe("switch");
  });

  it("refuses an organization the user has left", () => {
    expect(pushOrganizationStep(push({ organizationId: "org-9" }), ORG, access([ORG]))).toBe(
      "not-member"
    );
  });

  it("refuses an organization of the user that no longer grants Kazan", () => {
    expect(
      pushOrganizationStep(push({ organizationId: "org-2" }), ORG, access([ORG, "org-2"], [ORG]))
    ).toBe("no-kazan");
  });
});

describe("openConversationFromRoute", () => {
  it("reads a channel screen", () => {
    expect(openConversationFromRoute("/chat/channel/ch-1", { message: "m-1" }, ORG)).toEqual({
      organizationId: ORG,
      channelId: "ch-1",
      threadId: null,
    });
  });

  it("reads a thread screen with its channel param", () => {
    expect(openConversationFromRoute("/chat/thread/th-1", { channelId: "ch-1" }, ORG)).toEqual({
      organizationId: ORG,
      channelId: "ch-1",
      threadId: "th-1",
    });
  });

  const purposeChannels = { dailyUpdates: "ch-daily", pullRequests: "ch-prs" };

  it("reads the daily updates and pull requests screens as their channels", () => {
    expect(openConversationFromRoute("/chat/updates", {}, ORG, purposeChannels)).toEqual({
      organizationId: ORG,
      channelId: "ch-daily",
      threadId: null,
    });
    expect(openConversationFromRoute("/chat/pull-requests", {}, ORG, purposeChannels)).toEqual({
      organizationId: ORG,
      channelId: "ch-prs",
      threadId: null,
    });
  });

  it("is null on a narrowed or not yet known purpose channel screen", () => {
    const narrowed = { dailyUpdates: null, pullRequests: null };
    expect(openConversationFromRoute("/chat/updates", {}, ORG, narrowed)).toBeNull();
    expect(openConversationFromRoute("/chat/pull-requests", {}, ORG, narrowed)).toBeNull();
    expect(openConversationFromRoute("/chat/updates", {}, ORG)).toBeNull();
  });

  it("keeps a push of the shown daily updates channel off the screen", () => {
    const open = openConversationFromRoute("/chat/updates", {}, ORG, purposeChannels);
    expect(foregroundDecision(push({ channelId: "ch-daily" }), open)).toBe("silent");
    expect(foregroundDecision(push({ channelId: "ch-daily", threadId: "th-1" }), open)).toBe(
      "banner"
    );
    expect(foregroundDecision(push({ channelId: "ch-prs", type: "REVIEW" }), open)).toBe("banner");
  });

  it("is null on every other screen", () => {
    expect(openConversationFromRoute("/chat", {}, ORG)).toBeNull();
    expect(openConversationFromRoute("/chat/search", { channelId: "ch-1" }, ORG)).toBeNull();
    expect(openConversationFromRoute("/ticket/KZN-1", {}, ORG)).toBeNull();
    expect(openConversationFromRoute("/chat/updates-filter", {}, ORG, purposeChannels)).toBeNull();
  });
});

describe("foregroundDecision", () => {
  const inChannel: OpenConversation = { organizationId: ORG, channelId: "ch-1", threadId: null };
  const inThread: OpenConversation = { organizationId: ORG, channelId: "ch-1", threadId: "th-1" };

  it("shows nothing for a message in the open channel", () => {
    expect(foregroundDecision(push(), inChannel)).toBe("silent");
  });

  it("shows nothing for a reply in the open thread", () => {
    expect(foregroundDecision(push({ threadId: "th-1" }), inThread)).toBe("silent");
    expect(foregroundDecision(push({ threadId: "th-1" }), { ...inThread, channelId: null })).toBe(
      "silent"
    );
  });

  it("shows the banner for another conversation", () => {
    expect(foregroundDecision(push({ threadId: "th-1" }), inChannel)).toBe("banner");
    expect(foregroundDecision(push(), inThread)).toBe("banner");
    expect(foregroundDecision(push({ channelId: "ch-2" }), inChannel)).toBe("banner");
    expect(foregroundDecision(push(), null)).toBe("banner");
  });

  it("shows the banner for the same channel id of another organization", () => {
    expect(foregroundDecision(push({ organizationId: "org-2" }), inChannel)).toBe("banner");
  });

  it("shows incidents and nudges whatever is open", () => {
    expect(foregroundDecision(push({ type: "INCIDENT", ticketKey: "KZN-1" }), inChannel)).toBe(
      "banner"
    );
    expect(foregroundDecision(push({ type: "NUDGE", channelId: null }), null)).toBe("banner");
  });

  it("withdraws on a read signal", () => {
    expect(foregroundDecision(push({ type: "READ" }), inChannel)).toBe("withdraw");
    expect(foregroundDecision(push({ type: "READ" }), null)).toBe("withdraw");
  });
});

describe("pushInConversation", () => {
  it("matches a read signal to the notifications of its conversation only", () => {
    const channel = { channelId: "ch-1", threadId: null };
    expect(pushInConversation(push(), channel)).toBe(true);
    expect(pushInConversation(push({ threadId: "th-1" }), channel)).toBe(false);
    expect(
      pushInConversation(push({ threadId: "th-1" }), { channelId: "ch-1", threadId: "th-1" })
    ).toBe(true);
    expect(pushInConversation(push(), { channelId: null, threadId: null })).toBe(false);
  });
});

describe("pushSentAt", () => {
  // 12:46 in Istanbul on 11 November 2026.
  const SENT = "2026-11-11T09:46:00.000Z";
  const SENT_MS: number = Date.parse(SENT);

  it("keeps the server's sentAt", () => {
    expect(pushSentAt(push({ sentAt: SENT }), 0, "ios")).toBe(SENT);
  });

  it("reads the iOS notification date in seconds when sentAt is missing", () => {
    expect(pushSentAt(push({ sentAt: null }), SENT_MS / 1000, "ios")).toBe(SENT);
  });

  it("reads the Android notification date in milliseconds when sentAt is missing", () => {
    expect(pushSentAt(push({ sentAt: null }), SENT_MS, "android")).toBe(SENT);
  });

  it("falls back to the notification date when sentAt is unreadable", () => {
    expect(pushSentAt(push({ sentAt: "soon" }), SENT_MS / 1000, "ios")).toBe(SENT);
  });
});

describe("pushDeviceRequest", () => {
  it("builds the registration with the app language", () => {
    expect(
      pushDeviceRequest({
        expoPushToken: "ExponentPushToken[abc]",
        os: "ios",
        deviceName: "  Deniz's iPhone ",
        appVersion: "1.0.0",
        language: "tr",
      })
    ).toEqual({
      expoPushToken: "ExponentPushToken[abc]",
      platform: "IOS",
      deviceName: "Deniz's iPhone",
      appVersion: "1.0.0",
      locale: "tr",
    });
  });

  it("fits the server's limits and fallbacks", () => {
    const request = pushDeviceRequest({
      expoPushToken: "ExponentPushToken[abc]",
      os: "android",
      deviceName: "x".repeat(150),
      appVersion: null,
      language: "de-DE",
    });
    expect(request.platform).toBe("ANDROID");
    expect(request.deviceName).toHaveLength(100);
    expect(request.appVersion).toBe("0.0.0");
    expect(request.locale).toBe("en");
    expect(
      pushDeviceRequest({ ...request, os: "android", deviceName: " ", language: "nl" }).deviceName
    ).toBeNull();
  });
});

describe("pushLocale", () => {
  it("keeps a supported language and its region-less form", () => {
    expect(pushLocale("nl")).toBe("nl");
    expect(pushLocale("en-US")).toBe("en");
    expect(pushLocale("tr_TR")).toBe("tr");
    expect(pushLocale(undefined)).toBe("en");
  });
});

describe("pushPermissionStep", () => {
  it("registers once permission is given", () => {
    expect(pushPermissionStep({ granted: true, undetermined: false, prePromptSeen: true })).toBe(
      "register"
    );
  });

  it("shows the pre-prompt once while the system has never asked", () => {
    expect(pushPermissionStep({ granted: false, undetermined: true, prePromptSeen: false })).toBe(
      "ask"
    );
    expect(pushPermissionStep({ granted: false, undetermined: true, prePromptSeen: true })).toBe(
      "none"
    );
  });

  it("leaves a denied permission to Settings", () => {
    expect(pushPermissionStep({ granted: false, undetermined: false, prePromptSeen: false })).toBe(
      "none"
    );
  });
});

describe("background notification task", () => {
  const read: PushData = push({
    type: "READ",
    channelId: "ch-1",
    threadId: "th-1",
    messageId: null,
    conversation: null,
  });

  it("reads the data Android and iOS hand over as a JSON string", () => {
    expect(readBackgroundPushData({ data: { dataString: JSON.stringify(read) } })).toEqual(read);
  });

  it("reads the data iOS keeps as an object under body", () => {
    expect(readBackgroundPushData({ data: { body: read }, notification: null })).toEqual(read);
  });

  it("ignores a payload that is not a Kazan push", () => {
    expect(readBackgroundPushData({ data: { dataString: "not json" } })).toBeNull();
    expect(readBackgroundPushData({ data: { dataString: '{"type":"OTHER"}' } })).toBeNull();
    expect(readBackgroundPushData(null)).toBeNull();
    expect(readBackgroundPushData({ data: {} })).toBeNull();
  });

  it("withdraws the read conversation, thread included", () => {
    expect(backgroundPushStep({ data: { dataString: JSON.stringify(read) } })).toEqual({
      step: "withdraw",
      conversation: { channelId: "ch-1", threadId: "th-1" },
    });
  });

  it("ignores a read signal without a channel", () => {
    const noChannel: PushData = { ...read, channelId: null };
    expect(backgroundPushStep({ data: { dataString: JSON.stringify(noChannel) } })).toEqual({
      step: "ignore",
    });
  });

  it("leaves a shown push alone", () => {
    expect(backgroundPushStep({ data: { dataString: JSON.stringify(push()) } })).toEqual({
      step: "ignore",
    });
  });

  it("hands Reply and Mark as read to the action handler", () => {
    expect(
      backgroundPushStep({ actionIdentifier: "REPLY", notification: {}, userText: "ok" })
    ).toEqual({ step: "action" });
  });
});
