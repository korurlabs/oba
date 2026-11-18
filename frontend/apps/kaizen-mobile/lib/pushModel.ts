import { parseInstant } from "@oba/shared/lib/format-instant";

import type { OrganizationAccess } from "@/lib/organizationAccess";
import {
  PUSH_APP_VERSION_MAX,
  PUSH_DEVICE_NAME_MAX,
  PushKind,
  pushDataSchema,
  PushLocaleSchema,
  PushPlatform,
  type PushData,
  type PushDeviceRegisterRequest,
  type PushLocale,
} from "@/lib/schemas/push";

/*
 * The push rules without native modules, so they run under `bun test`: what a
 * payload means, where a tap goes, whether a push that arrives while the app
 * is open is shown, and what `PUT /push/devices` sends.
 */

// ── Channels and categories ─────────────────────────────────────────

/**
 * The Android channels the server names in `channelId`. Their ids must match
 * the server's, or Android delivers to a default channel the user cannot tune.
 */
export const PUSH_CHANNEL = {
  mentionsReplies: "mentions_replies",
  everyoneHere: "everyone_here",
  urgentIncidents: "urgent_incidents",
} as const;

/** The category the server sets on every chat push; it carries Reply and Mark as read. */
export const CHAT_MESSAGE_CATEGORY = "chat_message";

/** The notification actions of `CHAT_MESSAGE_CATEGORY`. */
export const PUSH_ACTION = {
  reply: "REPLY",
  markAsRead: "MARK_AS_READ",
} as const;

/** The chat kinds: each opens a conversation and carries Reply and Mark as read. */
const CHAT_KINDS: ReadonlySet<PushKind> = new Set<PushKind>([
  PushKind.MENTION,
  PushKind.REPLY,
  PushKind.EVERYONE,
  PushKind.HERE,
  PushKind.REVIEW,
]);

export function isChatPushKind(kind: PushKind): boolean {
  return CHAT_KINDS.has(kind);
}

// ── Payload ─────────────────────────────────────────────────────────

/** The push data of a notification, or null when it is not a Kaizen push. */
export function parsePushData(raw: unknown): PushData | null {
  const result = pushDataSchema.safeParse(raw);
  return result.success ? result.data : null;
}

// ── Tap routing ─────────────────────────────────────────────────────

/** Where a tapped notification opens, as an expo-router href. */
export type PushHref =
  | {
      pathname: "/chat/channel/[channelId]";
      params: { channelId: string; message?: string; thread?: string };
    }
  | { pathname: "/ticket/[ticketKey]"; params: { ticketKey: string } }
  | { pathname: "/incidents" }
  | { pathname: "/chat/updates" }
  | { pathname: "/chat/pull-requests" };

/**
 * The screen a tap opens:
 * - a review request opens the pull requests channel, whose cards carry
 *   Approve; the plain channel shows a share as a one-line entry;
 * - any other chat push opens its channel at the message; in a thread the
 *   channel screen opens the thread over itself at that reply;
 * - an incident opens its ticket, or the Incidents tab without a key;
 * - a daily update nudge opens the daily updates;
 * - a read signal opens nothing.
 */
export function pushHref(data: PushData): PushHref | null {
  if (data.type === PushKind.REVIEW) return { pathname: "/chat/pull-requests" };
  if (isChatPushKind(data.type)) {
    if (!data.channelId) return null;
    const params: { channelId: string; message?: string; thread?: string } = {
      channelId: data.channelId,
    };
    if (data.messageId) params.message = data.messageId;
    if (data.threadId) params.thread = data.threadId;
    return { pathname: "/chat/channel/[channelId]", params };
  }
  if (data.type === PushKind.INCIDENT) {
    return data.ticketKey
      ? { pathname: "/ticket/[ticketKey]", params: { ticketKey: data.ticketKey } }
      : { pathname: "/incidents" };
  }
  if (data.type === PushKind.NUDGE) return { pathname: "/chat/updates" };
  return null;
}

/**
 * What a tap needs before it navigates. A push of another organization
 * switches to it first, so the screen loads that organization's data. One of
 * an organization the user has left opens nothing, and neither does one of an
 * organization that no longer grants the user Kaizen, whose every Kaizen call
 * the API refuses.
 */
export type PushOrganizationStep = "stay" | "switch" | "not-member" | "no-kaizen";

export function pushOrganizationStep(
  data: PushData,
  activeOrganizationId: string | null,
  access: OrganizationAccess | null
): PushOrganizationStep {
  if (data.organizationId === activeOrganizationId) return "stay";
  // Before the user's organizations load, trust the push: the organization
  // gate moves the user back if the switch was to one they cannot use.
  if (access === null) return "switch";
  if (!access.memberIds.includes(data.organizationId)) return "not-member";
  if (!access.kaizenIds.includes(data.organizationId)) return "no-kaizen";
  return "switch";
}

// ── The open conversation ───────────────────────────────────────────

/** The chat conversation on screen: a channel, or a thread (`channelId` may be unknown). */
export interface OpenConversation {
  organizationId: string | null;
  channelId: string | null;
  threadId: string | null;
}

const CHANNEL_PATH = /^\/chat\/channel\/([^/?#]+)$/;
const THREAD_PATH = /^\/chat\/thread\/([^/?#]+)$/;
const UPDATES_PATH = "/chat/updates";
const PULL_REQUESTS_PATH = "/chat/pull-requests";

/**
 * The purpose channels their own screens show whole: the daily updates
 * channel on today with no person picked, and the pull requests channel with
 * no filter. Null while the channel is unknown or its screen is narrowed,
 * because a narrowed view may not show the message a push is about.
 */
export interface PurposeChannelsOnScreen {
  dailyUpdates: string | null;
  pullRequests: string | null;
}

export const NO_PURPOSE_CHANNELS_ON_SCREEN: PurposeChannelsOnScreen = {
  dailyUpdates: null,
  pullRequests: null,
};

function decodeSegment(segment: string): string {
  try {
    return decodeURIComponent(segment);
  } catch (error: unknown) {
    // A malformed escape cannot be a real id; the raw segment simply matches no push.
    console.warn(
      "[push] could not decode route segment",
      error instanceof Error ? error.message : error
    );
    return segment;
  }
}

function firstParam(value: string | string[] | undefined): string | null {
  const raw: string | undefined = Array.isArray(value) ? value[0] : value;
  return raw ? raw : null;
}

/**
 * The conversation the current route shows, read from the expo-router
 * pathname and its params, or null on any other screen. The daily updates
 * and pull requests screens show their channel when `purposeChannels` names
 * it.
 */
export function openConversationFromRoute(
  pathname: string,
  params: Record<string, string | string[] | undefined>,
  organizationId: string | null,
  purposeChannels: PurposeChannelsOnScreen = NO_PURPOSE_CHANNELS_ON_SCREEN
): OpenConversation | null {
  const thread: RegExpMatchArray | null = pathname.match(THREAD_PATH);
  if (thread?.[1]) {
    return {
      organizationId,
      channelId: firstParam(params.channelId),
      threadId: decodeSegment(thread[1]),
    };
  }
  const channel: RegExpMatchArray | null = pathname.match(CHANNEL_PATH);
  if (channel?.[1]) {
    return { organizationId, channelId: decodeSegment(channel[1]), threadId: null };
  }
  const purposeChannelId: string | null =
    pathname === UPDATES_PATH
      ? purposeChannels.dailyUpdates
      : pathname === PULL_REQUESTS_PATH
        ? purposeChannels.pullRequests
        : null;
  return purposeChannelId ? { organizationId, channelId: purposeChannelId, threadId: null } : null;
}

/**
 * Whether a push belongs to a conversation: the same thread, or the same
 * channel outside any thread. Thread ids are unique, so a thread matches on
 * its id alone.
 */
export function pushInConversation(
  data: PushData,
  conversation: { channelId: string | null; threadId: string | null }
): boolean {
  if (conversation.threadId) return data.threadId === conversation.threadId;
  return (
    conversation.channelId !== null &&
    data.channelId === conversation.channelId &&
    data.threadId === null
  );
}

// ── Arrival while the app is open ───────────────────────────────────

/**
 * What a push that arrives while the app is open does:
 * - `withdraw`: a read signal; the conversation's notifications are removed;
 * - `silent`: the user is reading that conversation, so nothing is shown;
 * - `banner`: the app shows its own banner.
 */
export type ForegroundDecision = "withdraw" | "silent" | "banner";

export function foregroundDecision(
  data: PushData,
  open: OpenConversation | null
): ForegroundDecision {
  if (data.type === PushKind.READ) return "withdraw";
  if (
    open &&
    isChatPushKind(data.type) &&
    open.organizationId === data.organizationId &&
    pushInConversation(data, open)
  ) {
    return "silent";
  }
  return "banner";
}

/**
 * When a push was sent, as an ISO instant for the banner: the server's
 * `sentAt`, or else the time the system gave the notification. A push the
 * server did not build (a local or test notification) has no `sentAt`, and an
 * unreadable one would draw no time at all.
 *
 * expo-notifications reports `Notification.date` in seconds on iOS (the
 * `timeIntervalSince1970` of the `UNNotification`) and in milliseconds on
 * Android. Read as milliseconds, an iOS value is a moment in January 1970, so
 * the banner showed a time of day hours away from the real one.
 */
export function pushSentAt(data: PushData, notificationDate: number, os: string): string {
  if (data.sentAt && parseInstant(data.sentAt)) return data.sentAt;
  const ms: number = os === "ios" ? notificationDate * 1000 : notificationDate;
  return new Date(ms).toISOString();
}

// ── Device registration ─────────────────────────────────────────────

/** The push text language: the app language when the server knows it, else English. */
export function pushLocale(language: string | null | undefined): PushLocale {
  const base: string = (language ?? "").split(/[-_]/)[0]?.toLowerCase() ?? "";
  const parsed = PushLocaleSchema.safeParse(base);
  return parsed.success ? parsed.data : "en";
}

function trimTo(value: string | null | undefined, max: number): string | null {
  const trimmed: string = (value ?? "").trim();
  return trimmed ? trimmed.slice(0, max) : null;
}

export interface PushDeviceFacts {
  expoPushToken: string;
  os: string;
  deviceName: string | null | undefined;
  appVersion: string | null | undefined;
  language: string | null | undefined;
}

/** The body of `PUT /push/devices`, cut to the server's limits. */
export function pushDeviceRequest(facts: PushDeviceFacts): PushDeviceRegisterRequest {
  return {
    expoPushToken: facts.expoPushToken,
    platform: facts.os === "ios" ? PushPlatform.IOS : PushPlatform.ANDROID,
    deviceName: trimTo(facts.deviceName, PUSH_DEVICE_NAME_MAX),
    appVersion: trimTo(facts.appVersion, PUSH_APP_VERSION_MAX) ?? "0.0.0",
    locale: pushLocale(facts.language),
  };
}

// ── Permission ──────────────────────────────────────────────────────

/**
 * What the app does about notifications once signed in:
 * - `register`: permission is given; send the token to the server;
 * - `ask`: never asked and not yet declined in the app; show the pre-prompt;
 * - `none`: denied, or "Not now" was chosen; Settings is the way back.
 */
export type PushPermissionStep = "register" | "ask" | "none";

export function pushPermissionStep(input: {
  granted: boolean;
  undetermined: boolean;
  prePromptSeen: boolean;
}): PushPermissionStep {
  if (input.granted) return "register";
  if (input.undetermined && !input.prePromptSeen) return "ask";
  return "none";
}

// ── Background ──────────────────────────────────────────────────────

/** The conversation whose notifications a read signal removes. */
export interface ReadConversation {
  channelId: string;
  threadId: string | null;
}

/**
 * What the background notification task does with what it was handed:
 *
 * - `action`: Reply or Mark as read chosen while the app was not open
 *   (Android hands these to the task, not only to the response listener);
 * - `withdraw`: a read signal; remove the conversation's notifications;
 * - `ignore`: any other push, which the system has already shown.
 */
export type BackgroundPushStep =
  | { step: "action" }
  | { step: "withdraw"; conversation: ReadConversation }
  | { step: "ignore" };

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

/**
 * The push data of a remote notification as the background task receives
 * it. Both platforms put the Expo `data` in `data.dataString` as JSON; iOS
 * also keeps it as an object under `data.body`. Null when it is not a Kaizen
 * push.
 */
export function readBackgroundPushData(payload: unknown): PushData | null {
  if (!isRecord(payload) || !isRecord(payload.data)) return null;
  const raw: Record<string, unknown> = payload.data;
  if (typeof raw.dataString === "string") {
    try {
      return parsePushData(JSON.parse(raw.dataString));
    } catch (error: unknown) {
      // A push of another sender, or a cut payload: nothing for Kaizen to do.
      console.warn(
        "[push] a background notification carried unreadable data",
        error instanceof Error ? error.message : error
      );
      return null;
    }
  }
  return isRecord(raw.body) ? parsePushData(raw.body) : null;
}

export function backgroundPushStep(payload: unknown): BackgroundPushStep {
  if (isRecord(payload) && typeof payload.actionIdentifier === "string") {
    return { step: "action" };
  }
  const data: PushData | null = readBackgroundPushData(payload);
  if (data?.type === PushKind.READ && data.channelId) {
    return {
      step: "withdraw",
      conversation: { channelId: data.channelId, threadId: data.threadId },
    };
  }
  return { step: "ignore" };
}
