import { z } from "zod";

// ── Enums ───────────────────────────────────────────────────────────

/** The phone's platform, as `PUT /push/devices` stores it. */
export const PushPlatformSchema = z.enum(["IOS", "ANDROID"]);
export type PushPlatform = z.infer<typeof PushPlatformSchema>;
// eslint-disable-next-line @typescript-eslint/no-redeclare -- reason: the enum's type and its value map share one name.
export const PushPlatform = PushPlatformSchema.enum;

/**
 * Why the server sent a push. `READ` carries no alert: it tells this phone
 * that the conversation was read elsewhere, so its notifications go away.
 */
export const PushKindSchema = z.enum([
  "MENTION",
  "REPLY",
  "EVERYONE",
  "HERE",
  "REVIEW",
  "NUDGE",
  "INCIDENT",
  "READ",
]);
export type PushKind = z.infer<typeof PushKindSchema>;
// eslint-disable-next-line @typescript-eslint/no-redeclare -- reason: the enum's type and its value map share one name.
export const PushKind = PushKindSchema.enum;

/** The language the server writes this phone's push text in. */
export const PushLocaleSchema = z.enum(["en", "nl", "tr"]);
export type PushLocale = z.infer<typeof PushLocaleSchema>;

// ── Limits ──────────────────────────────────────────────────────────

/** The longest device name `PUT /push/devices` accepts. */
export const PUSH_DEVICE_NAME_MAX = 100;
/** The longest app version `PUT /push/devices` accepts. */
export const PUSH_APP_VERSION_MAX = 32;

// ── Requests ────────────────────────────────────────────────────────

/** `PUT /push/devices`: idempotent per token; a token of another user moves to the caller. */
export const pushDeviceRegisterRequestSchema = z.object({
  expoPushToken: z.string().regex(/^ExponentPushToken\[.+\]$/),
  platform: PushPlatformSchema,
  deviceName: z.string().min(1).max(PUSH_DEVICE_NAME_MAX).nullable(),
  appVersion: z.string().min(1).max(PUSH_APP_VERSION_MAX),
  locale: PushLocaleSchema,
});

// ── Responses ───────────────────────────────────────────────────────

/** The registered device; its `id` unregisters it at sign-out. The token is never returned. */
export const pushDeviceResponseSchema = z.object({
  id: z.string(),
  platform: PushPlatformSchema,
  deviceName: z.string().nullable(),
  appVersion: z.string(),
  lastRegisteredAt: z.iso.datetime({ offset: true }),
});

/**
 * The user's push switches, the same for every phone and organization.
 * `GET /push/preferences` answers all true for a user who never saved them;
 * `PUT /push/preferences` takes all five and answers with the saved set.
 */
export const pushPreferencesSchema = z.object({
  /** Mentions, replies, review requests and daily update nudges. */
  mentionsAndReplies: z.boolean(),
  everyone: z.boolean(),
  here: z.boolean(),
  /** Open P1 and P2 incidents on the user's boards. */
  urgentIncidents: z.boolean(),
  /** Off: chat pushes name the conversation without the message text. */
  showPreviews: z.boolean(),
});

/** A key the server may send as null and a local notification may leave out. */
const nullableKey = () =>
  z
    .string()
    .nullish()
    .transform((value: string | null | undefined): string | null => value ?? null);

/**
 * The `data` of every push. The server always sends every key, null when it
 * does not apply; a notification the app scheduled itself may leave some out,
 * so a missing key reads as null.
 */
export const pushDataSchema = z.object({
  type: PushKindSchema,
  organizationId: z.string().min(1),
  channelId: nullableKey(),
  threadId: nullableKey(),
  messageId: nullableKey(),
  ticketKey: nullableKey(),
  conversation: nullableKey(),
  sentAt: nullableKey(),
});

// ── Types ───────────────────────────────────────────────────────────

export type PushDeviceRegisterRequest = z.infer<typeof pushDeviceRegisterRequestSchema>;
export type PushDeviceResponse = z.infer<typeof pushDeviceResponseSchema>;
export type PushData = z.infer<typeof pushDataSchema>;
export type PushPreferences = z.infer<typeof pushPreferencesSchema>;
export type PushPreferenceKey = keyof PushPreferences;
