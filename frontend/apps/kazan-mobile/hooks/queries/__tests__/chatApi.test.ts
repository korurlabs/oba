import { beforeEach, describe, expect, it, mock } from "bun:test";
import { AxiosError, type AxiosResponse, type InternalAxiosRequestConfig } from "axios";

import { CHANNEL_ID, chatMessage } from "@/components/chat/__tests__/chatFixtures";

// Bun cannot parse react-native's Flow source; lib/api.ts reads only Platform.OS.
mock.module("react-native", () => ({
  Platform: { OS: "ios" },
  AppState: { addEventListener: () => ({ remove: () => undefined }) },
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

const { ORGANIZATION_HEADER, api } = await import("@/lib/api");
const { clearActiveOrganizationId, setActiveOrganizationId } =
  await import("@/lib/organizationStore");
const { createChatMessage, deleteChatChannel, listChatMembers, updateChatChannel } =
  await import("@/hooks/queries/chatApi");
const { isApiError } = await import("@/lib/api");

describe("createChatMessage", () => {
  let calls: InternalAxiosRequestConfig[] = [];

  beforeEach(async () => {
    await clearActiveOrganizationId();
    calls = [];
    api.defaults.adapter = async (config: InternalAxiosRequestConfig): Promise<AxiosResponse> => {
      calls.push(config);
      return {
        config,
        status: 201,
        statusText: "201",
        headers: {},
        data: { success: true, message: "created", data: chatMessage() },
      };
    };
  });

  it("sends the message to the organization it was written in, not the active one", async () => {
    await setActiveOrganizationId("org-active");

    await createChatMessage(
      {
        channelId: CHANNEL_ID,
        content: "hello",
        threadId: null,
        replyToMessageId: null,
        clientNonce: "nonce-1",
      },
      "org-written"
    );

    expect(calls).toHaveLength(1);
    expect(calls[0]?.headers.get(ORGANIZATION_HEADER)).toBe("org-written");
  });
});

/*
 * Bodies the local backend sent for a throwaway channel, created, changed
 * and deleted for this capture, as the dev user (an OWNER).
 */
const THROWAWAY_ID = "1c0c24bb-4904-468d-ad04-57f9dd4db8e0";

const UPDATED_PRIVATE_BODY = {
  success: true,
  message: "Chat channel updated successfully",
  data: {
    id: THROWAWAY_ID,
    name: "zz-u1-throwaway-1794412572-renamed",
    slug: "zz-u1-throwaway-1794412572-renamed",
    type: "TEXT",
    purpose: "GENERAL",
    visibility: "PRIVATE",
    member: true,
    memberCount: 1,
    createdAt: "2026-11-11T15:56:12.848171Z",
  },
  pagination: null,
  error: null,
};

const DELETED_BODY = {
  success: true,
  message: "Chat channel deleted successfully",
  data: null,
  pagination: null,
  error: null,
};

const MEMBERS_NOT_EXPECTED_BODY = {
  success: false,
  message: "Members can be set only when a channel becomes private.",
  data: null,
  pagination: null,
  error: {
    code: "KAZ_VAL_051",
    message: "Members can be set only when a channel becomes private.",
    errorValues: { memberIds: 1 },
  },
};

const AUTHORS_BODY = {
  success: true,
  message: "Chat members retrieved successfully",
  data: [
    {
      userId: "da554751-f027-495c-8019-aa3898742ec3",
      firstName: "Dev",
      lastName: "Owner",
      email: "dev@example.com",
      role: "OWNER",
      online: false,
    },
  ],
  pagination: null,
  error: null,
};

describe("channel management requests", () => {
  let calls: InternalAxiosRequestConfig[] = [];

  function answer(status: number, body: unknown): void {
    api.defaults.adapter = async (config: InternalAxiosRequestConfig): Promise<AxiosResponse> => {
      calls.push(config);
      const response: AxiosResponse = {
        config,
        status,
        statusText: String(status),
        headers: {},
        data: body,
      };
      if (status >= 400) {
        throw new AxiosError("refused", AxiosError.ERR_BAD_REQUEST, config, null, response);
      }
      return response;
    };
  }

  beforeEach(async () => {
    await setActiveOrganizationId("org-active");
    calls = [];
  });

  it("puts the change to the channel, silently, without members it does not send", async () => {
    answer(200, UPDATED_PRIVATE_BODY);

    const channel = await updateChatChannel(THROWAWAY_ID, {
      name: "zz-u1-throwaway-1794412572-renamed",
      visibility: "PRIVATE",
      purpose: "GENERAL",
      memberIds: undefined,
    });

    expect(channel.visibility).toBe("PRIVATE");
    expect(channel.memberCount).toBe(1);
    expect(calls[0]?.method).toBe("put");
    expect(calls[0]?.url).toBe(`/kazan/chat/channels/${THROWAWAY_ID}`);
    expect(calls[0]?._silentErrors).toBe(true);
    expect(JSON.parse(calls[0]?.data as string)).toEqual({
      name: "zz-u1-throwaway-1794412572-renamed",
      visibility: "PRIVATE",
      purpose: "GENERAL",
    });
  });

  it("reads a refused change as its error code", async () => {
    answer(422, MEMBERS_NOT_EXPECTED_BODY);

    const error: unknown = await updateChatChannel(THROWAWAY_ID, {
      name: "x",
      visibility: "PRIVATE",
      purpose: "GENERAL",
      memberIds: ["da554751-f027-495c-8019-aa3898742ec3"],
    }).catch((caught: unknown) => caught);

    expect(isApiError(error, "KAZ_VAL_051")).toBe(true);
  });

  it("deletes the channel, whose answer has no data", async () => {
    answer(200, DELETED_BODY);

    await deleteChatChannel(THROWAWAY_ID);

    expect(calls[0]?.method).toBe("delete");
    expect(calls[0]?.url).toBe(`/kazan/chat/channels/${THROWAWAY_ID}`);
    expect(calls[0]?._silentErrors).toBe(true);
  });

  it("asks for a channel's authors with authorsOnly, and for its members without it", async () => {
    answer(200, AUTHORS_BODY);

    const authors = await listChatMembers(THROWAWAY_ID, true);
    await listChatMembers(THROWAWAY_ID);

    expect(authors.map((author) => author.userId)).toEqual([
      "da554751-f027-495c-8019-aa3898742ec3",
    ]);
    expect(calls[0]?.params).toEqual({ channelId: THROWAWAY_ID, authorsOnly: true });
    expect(calls[1]?.params).toEqual({ channelId: THROWAWAY_ID, authorsOnly: undefined });
  });
});
