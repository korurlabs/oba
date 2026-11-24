import { beforeEach, describe, expect, it, mock } from "bun:test";
import type { AxiosResponse, InternalAxiosRequestConfig } from "axios";
import type * as Notifications from "expo-notifications";

import type { PushData } from "@/lib/schemas/push";

// The same stubs as lib/__tests__/api.test.ts: lib/api.ts loads the env,
// SecureStore and Keycloak discovery at import.
const platform = { OS: "ios" };
mock.module("react-native", () => ({
  Platform: platform,
  Linking: { openSettings: async () => undefined },
  AppState: { currentState: "active", addEventListener: () => ({ remove: () => undefined }) },
}));

mock.module("@react-native-community/netinfo", () => ({
  default: { addEventListener: () => () => undefined },
}));

mock.module("expo-secure-store", () => ({
  getItemAsync: async () => null,
  setItemAsync: async () => undefined,
  deleteItemAsync: async () => undefined,
}));

mock.module("@/lib/env", () => ({
  ENV: {
    apiBaseUrl: "https://api.test",
    keycloakIssuer: "https://auth.test/realms/sensey",
    keycloakClientId: "kazan-mobile",
    accountUrl: "https://account.test",
    ulakUrl: "https://ulak.test",
    kazanWebUrl: "https://kazan.test",
  },
}));

mock.module("expo-auth-session", () => ({
  fetchDiscoveryAsync: async (issuer: string) => ({
    authorizationEndpoint: `${issuer}/protocol/openid-connect/auth`,
    tokenEndpoint: `${issuer}/protocol/openid-connect/token`,
    endSessionEndpoint: `${issuer}/protocol/openid-connect/logout`,
  }),
  makeRedirectUri: ({ scheme, path }: { scheme: string; path: string }) => `${scheme}://${path}`,
}));

mock.module("expo-application", () => ({}));
mock.module("expo-constants", () => ({ default: { expoConfig: null, easConfig: null } }));
mock.module("expo-crypto", () => ({ randomUUID: () => "uuid-1" }));
mock.module("@/i18n", () => ({ translate: (key: string) => key }));

const scheduled: Notifications.NotificationRequestInput[] = [];
const dismissed: string[] = [];
let presented: Notifications.Notification[] = [];
mock.module("expo-notifications", () => ({
  scheduleNotificationAsync: async (request: Notifications.NotificationRequestInput) => {
    scheduled.push(request);
    return "local-1";
  },
  dismissNotificationAsync: async (identifier: string) => {
    dismissed.push(identifier);
  },
  getPresentedNotificationsAsync: async () => presented,
}));

const { api } = await import("@/lib/api");
const { handlePushAction, runPushActionOnce } = await import("@/lib/pushActions");
const { notifyActionFailed, withdrawConversationNotifications } = await import("@/lib/push");

const DATA: PushData = {
  type: "MENTION",
  organizationId: "org-push",
  channelId: "ch-1",
  threadId: "th-1",
  messageId: "m-1",
  ticketKey: null,
  conversation: "#general",
  sentAt: "2026-11-09T09:41:00Z",
};

function response(actionIdentifier: string, data: PushData = DATA, userText?: string) {
  return {
    actionIdentifier,
    userText,
    notification: { request: { identifier: "n-1", content: { data: { ...data } } } },
  } as unknown as Notifications.NotificationResponse;
}

function delivered(identifier: string, data: PushData): Notifications.Notification {
  return {
    request: { identifier, content: { data: { ...data } } },
  } as unknown as Notifications.Notification;
}

let requests: InternalAxiosRequestConfig[] = [];
let failRequests = false;

beforeEach(() => {
  scheduled.length = 0;
  dismissed.length = 0;
  presented = [];
  requests = [];
  failRequests = false;
  platform.OS = "ios";
  api.defaults.adapter = async (config: InternalAxiosRequestConfig): Promise<AxiosResponse> => {
    requests.push(config);
    if (failRequests) throw new Error("offline");
    return { config, status: 200, statusText: "200", headers: {}, data: { success: true } };
  };
});

describe("Reply from a notification", () => {
  it("posts into the push's own organization and removes the notification", async () => {
    expect(await handlePushAction(response("REPLY", DATA, "  on it  "))).toBe(true);
    expect(requests).toHaveLength(1);
    expect(requests[0]!.headers.get("X-Organization-ID")).toBe("org-push");
    expect(scheduled).toEqual([]);
    expect(dismissed).toEqual(["n-1"]);
  });

  it("sends nothing for an empty reply", async () => {
    expect(await handlePushAction(response("REPLY", DATA, "   "))).toBe(true);
    expect(requests).toEqual([]);
    expect(dismissed).toEqual([]);
  });

  it("leaves a local notification that opens the conversation when the reply fails", async () => {
    failRequests = true;
    expect(await handlePushAction(response("REPLY", DATA, "on it"))).toBe(true);
    expect(dismissed).toEqual(["n-1"]);
    expect(scheduled).toHaveLength(1);
    const content = scheduled[0]!.content;
    expect(content.title).toBe("pushReplyNotSent");
    expect(content.body).toBe("#general");
    expect(content.data).toEqual({ ...DATA });
    expect(scheduled[0]!.trigger).toBeNull();
  });

  it("puts the failure notice on the mentions and replies channel on Android", async () => {
    platform.OS = "android";
    await notifyActionFailed({ ...DATA, conversation: null }, "pushReplyNotSent");
    expect(scheduled[0]!.content.body).toBe("pushActionFailedBody");
    expect(scheduled[0]!.trigger).toMatchObject({ channelId: expect.any(String) });
  });

  it("runs a response that reaches both listeners once", async () => {
    const same = response("REPLY", DATA, "once");
    await runPushActionOnce(same);
    await runPushActionOnce(same);
    expect(requests).toHaveLength(1);
  });
});

describe("Mark as read from a notification", () => {
  it("moves the read position of the push's conversation and removes the notification", async () => {
    expect(await handlePushAction(response("MARK_AS_READ"))).toBe(true);
    expect(requests).toHaveLength(1);
    expect(requests[0]!.headers.get("X-Organization-ID")).toBe("org-push");
    expect(dismissed).toEqual(["n-1"]);
  });

  it("tells the user when it could not be marked, and keeps the notification", async () => {
    failRequests = true;
    expect(await handlePushAction(response("MARK_AS_READ"))).toBe(true);
    expect(scheduled.map((request) => request.content.title)).toEqual(["pushMarkReadFailed"]);
    expect(dismissed).toEqual([]);
  });

  it("leaves a tap to the push host", async () => {
    expect(await handlePushAction(response("expo.modules.notifications.actions.DEFAULT"))).toBe(
      false
    );
    expect(requests).toEqual([]);
  });
});

describe("READ dismissal", () => {
  it("removes the delivered notifications of the read conversation only", async () => {
    presented = [
      delivered("same-thread", DATA),
      delivered("same-channel-other-thread", { ...DATA, threadId: "th-2" }),
      delivered("channel-message", { ...DATA, threadId: null }),
    ];
    await withdrawConversationNotifications({ channelId: "ch-1", threadId: "th-1" });
    expect(dismissed).toEqual(["same-thread"]);
  });
});
