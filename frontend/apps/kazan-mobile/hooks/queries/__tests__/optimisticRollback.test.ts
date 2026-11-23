import { beforeEach, describe, expect, it, mock } from "bun:test";
import * as reactQuery from "@tanstack/react-query";
import { QueryClient } from "@tanstack/react-query";
import {
  AxiosError,
  AxiosHeaders,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from "axios";
import * as react from "react";

import { CHANNEL_ID, ME, ORG_ID, chatMessage } from "@/components/chat/__tests__/chatFixtures";
import type { ChatMessageListData } from "@/hooks/queries/chatCache";
import type { BoardTicket } from "@/lib/schemas/boards";

// The same stubs as lib/__tests__/api.test.ts: lib/api.ts loads the env,
// SecureStore and Keycloak discovery at import.
mock.module("react-native", () => ({
  Platform: { OS: "ios" },
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

// The mutations import the toast host and the translator, which pull in the native UI.
mock.module("@/components/ui/toast", () => ({
  toast: { error: () => undefined, success: () => undefined, info: () => undefined },
}));
mock.module("expo-crypto", () => ({ randomUUID: () => "uuid-1" }));
mock.module("@/i18n", () => ({ translate: (key: string) => key }));

// A chat query hook asks the router whether its screen is focused; every screen is.
mock.module("expo-router", () => ({ useIsFocused: () => true }));

// No renderer is installed, and the workspace holds two copies of React, so
// the hooks run against a plain client: useMutation drives the real
// MutationObserver with the hook's own options, and the other hooks read the
// client under test. The fakes spread the real modules, so other tests that
// import them are unaffected.
let activeClient: QueryClient = new QueryClient();
mock.module("@tanstack/react-query", () => ({
  ...reactQuery,
  useQueryClient: (): QueryClient => activeClient,
  useQuery: (options: { queryKey: readonly unknown[] }) => ({
    data: activeClient.getQueryData(options.queryKey),
  }),
  useMutation: (options: ConstructorParameters<typeof reactQuery.MutationObserver>[1]) => {
    const observer = new reactQuery.MutationObserver(activeClient, options);
    return { mutateAsync: (variables: unknown) => observer.mutate(variables as never) };
  },
}));
mock.module("react", () => ({
  ...react,
  default: react,
  useRef: <T>(initial: T) => ({ current: initial }),
}));

const organizationStore = await import("@/lib/organizationStore");
mock.module("@/lib/organizationStore", () => ({
  ...organizationStore,
  useActiveOrganizationId: (): string => ORG_ID,
}));

const { api, addApiErrorListener } = await import("@/lib/api");
const { apiErrorToastMessage } = await import("@/components/auth/apiErrorToastModel");
const { boardTicketKeys } = await import("@/hooks/queries/boards");
const { chatKeys } = await import("@/hooks/queries/chatCache");
const { useToggleChatReaction } = await import("@/hooks/queries/chatMutations");
const { useTicketMove } = await import("@/hooks/queries/ticketMoves");
const { userKeys } = await import("@/hooks/queries/users");

const BOARD = "board-1";
const LIST_PARAMS = { sprintId: "s1" };

function ticket(id: string, status: string, displayOrder: number): BoardTicket {
  return {
    id,
    createdAt: "2026-11-01T09:00:00Z",
    title: `Ticket ${id}`,
    key: `KZN-${id}`,
    displayOrder,
    type: "FEATURE",
    status,
    priority: "MEDIUM",
    missionRank: "M",
    dueDate: null,
    assignees: [],
    board: { id: BOARD, name: "Board", prefix: "KZN" },
  } as BoardTicket;
}

/** Runs a hook against the client; the hooks under test call no React state. */
function renderHook<T>(client: QueryClient, hook: () => T): T {
  activeClient = client;
  return hook();
}

function newClient(): QueryClient {
  return new QueryClient({ defaultOptions: { mutations: { retry: false } } });
}

let failWith: number | null = null;
let gate: Promise<void> | null = null;
let announced: string[] = [];
let toasts: (string | null)[] = [];

beforeEach(() => {
  failWith = null;
  gate = null;
  announced = [];
  toasts = [];
  api.defaults.adapter = async (config: InternalAxiosRequestConfig): Promise<AxiosResponse> => {
    const respond = (status: number, data: unknown): AxiosResponse => ({
      config,
      status,
      statusText: String(status),
      headers: {},
      data,
    });
    if (gate) await gate;
    if (failWith !== null) {
      throw new AxiosError(
        "failed",
        AxiosError.ERR_BAD_REQUEST,
        { ...config, headers: config.headers ?? new AxiosHeaders() } as InternalAxiosRequestConfig,
        {},
        respond(failWith, { error: { code: "TKT_VAL_002", message: "Not allowed" } })
      );
    }
    return respond(200, { success: true, data: [] });
  };
});

addApiErrorListener((event) => {
  announced.push(event.error.code ?? event.error.kind);
  toasts.push(apiErrorToastMessage(event, (key: string) => `t:${key}`));
});

describe("a refused board move", () => {
  function seeded(): { client: QueryClient; key: readonly unknown[]; before: BoardTicket[] } {
    const client: QueryClient = newClient();
    const key = boardTicketKeys.list(ORG_ID, BOARD, LIST_PARAMS);
    const before: BoardTicket[] = [ticket("a", "TODO", 1), ticket("b", "TODO", 2)];
    client.setQueryData(key, before);
    return { client, key, before };
  }

  it("shows a drop at once and puts the board back when the API refuses it", async () => {
    const { client, key, before } = seeded();
    failWith = 422;
    let release: () => void = () => undefined;
    gate = new Promise<void>((resolve) => {
      release = resolve;
    });
    const move = renderHook(client, () => useTicketMove(BOARD));
    const attempt = move.mutateAsync({
      kind: "status",
      ticket: before[0]!,
      boardId: BOARD,
      target: { status: "DONE", beforeId: null } as never,
    });
    await new Promise((resolve) => setTimeout(resolve, 0));
    expect(client.getQueryData<BoardTicket[]>(key)!.find((row) => row.id === "a")?.status).toBe(
      "DONE"
    );
    release();
    await expect(attempt).rejects.toBeDefined();
    expect(client.getQueryData(key)).toEqual(before);
  });

  it("keeps the move when the API accepts it", async () => {
    const { client, key, before } = seeded();
    const move = renderHook(client, () => useTicketMove(BOARD));
    await move.mutateAsync({
      kind: "status",
      ticket: before[0]!,
      boardId: BOARD,
      target: { status: "DONE", beforeId: null } as never,
    });
    const after = client.getQueryData<BoardTicket[]>(key)!;
    expect(after.find((row) => row.id === "a")?.status).toBe("DONE");
  });

  it("leaves the failure to the caller's toast instead of announcing it twice", async () => {
    const { client, before } = seeded();
    failWith = 422;
    const move = renderHook(client, () => useTicketMove(BOARD));
    await move
      .mutateAsync({
        kind: "status",
        ticket: before[0]!,
        boardId: BOARD,
        target: { status: "DONE", beforeId: null } as never,
      })
      .catch(() => undefined);
    expect(announced).toEqual([]);
  });
});

describe("a refused reaction", () => {
  function seeded(): { client: QueryClient; messageKey: readonly unknown[] } {
    const client: QueryClient = newClient();
    client.setQueryData(userKeys.me, { id: ME });
    const messageKey = chatKeys.channel(ORG_ID, CHANNEL_ID);
    const data: ChatMessageListData = {
      pages: [{ items: [chatMessage()], hasOlder: false, hasNewer: false }],
      pageParams: [{}],
    };
    client.setQueryData(messageKey, data);
    return { client, messageKey };
  }

  function reactions(client: QueryClient, key: readonly unknown[]) {
    return client.getQueryData<ChatMessageListData>(key)!.pages[0]!.items[0]!.reactions;
  }

  it("removes the reaction again and toasts the server's message", async () => {
    const { client, messageKey } = seeded();
    failWith = 422;
    let release: () => void = () => undefined;
    gate = new Promise<void>((resolve) => {
      release = resolve;
    });
    const toggle = renderHook(client, () => useToggleChatReaction());
    const attempt = toggle
      .mutateAsync({ messageId: "m1", emoji: "👍", add: true })
      .catch(() => undefined);
    await new Promise((resolve) => setTimeout(resolve, 0));
    expect(reactions(client, messageKey)).toHaveLength(1);
    release();
    await attempt;
    expect(reactions(client, messageKey)).toEqual([]);
    expect(announced).toEqual(["TKT_VAL_002"]);
    expect(toasts).toEqual(["Not allowed"]);
  });

  it("puts a removed reaction back when the removal is refused", async () => {
    const { client, messageKey } = seeded();
    const first = reactions(client, messageKey);
    expect(first).toEqual([]);
    client.setQueryData(messageKey, {
      pages: [
        {
          items: [
            chatMessage({
              reactions: [{ emoji: "👍", count: 1, reactedByMe: true, userIds: [ME] }],
            }),
          ],
          hasOlder: false,
          hasNewer: false,
        },
      ],
      pageParams: [{}],
    });
    failWith = 500;
    const toggle = renderHook(client, () => useToggleChatReaction());
    await toggle.mutateAsync({ messageId: "m1", emoji: "👍", add: false }).catch(() => undefined);
    expect(reactions(client, messageKey)).toEqual([
      { emoji: "👍", count: 1, reactedByMe: true, userIds: [ME] },
    ]);
  });
});
