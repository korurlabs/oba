import { describe, expect, test } from "bun:test";

import {
  AccountProviderSchema,
  PROVIDER_LABELS,
  accountDetailResponseSchema,
  accountListResponseSchema,
  accountQueryRequestSchema,
  providerReportsUsage,
} from ".";

const PROVIDERS = ["CLAUDE", "CHATGPT", "GEMINI", "GROK", "DEEPSEEK"] as const;

// The body GET /accounts answers, field for field, with the optional ones null.
const ACCOUNT = {
  id: "acc-1",
  provider: "CLAUDE",
  accountEmail: "ada@example.com",
  accountOrganizationUuid: null,
  accountOrganizationName: "Korur",
  billingType: "stripe_subscription",
  organizationType: null,
  seatTier: "max_20x",
  lastSeenAt: "2026-10-23T12:21:00Z",
  users: [
    { userId: "user-1", displayName: "Ada", source: "RECORDER", lastSeenAt: null },
    {
      userId: "user-2",
      displayName: "Bob",
      source: "MANUAL",
      lastSeenAt: "2026-10-22T08:00:00Z",
    },
  ],
  machines: 3,
  activeMachines: 2,
  usage: { state: "NO_SNAPSHOT", sharedWith: [] },
};

describe("providers", () => {
  test("the schema accepts exactly the five providers", () => {
    expect(AccountProviderSchema.options).toEqual([...PROVIDERS]);
    expect(AccountProviderSchema.safeParse("COPILOT").success).toBe(false);
    expect(AccountProviderSchema.safeParse("claude").success).toBe(false);
  });

  test("every provider has a product name", () => {
    expect(PROVIDER_LABELS).toEqual({
      CLAUDE: "Claude",
      CHATGPT: "ChatGPT",
      GEMINI: "Gemini",
      GROK: "Grok",
      DEEPSEEK: "DeepSeek",
    });
    for (const provider of PROVIDERS) expect(PROVIDER_LABELS[provider]).toBeTruthy();
  });

  test("only Claude reports usage", () => {
    expect(providerReportsUsage("CLAUDE")).toBe(true);
    for (const provider of PROVIDERS.filter((p: string) => p !== "CLAUDE")) {
      expect(providerReportsUsage(provider)).toBe(false);
    }
  });
});

describe("accountListResponseSchema", () => {
  test("reads an account of every provider", () => {
    for (const provider of PROVIDERS) {
      expect(accountListResponseSchema.parse({ ...ACCOUNT, provider }).provider).toBe(provider);
    }
  });

  test("reads the users, the counts and the usage state", () => {
    const parsed = accountListResponseSchema.parse(ACCOUNT);
    expect(parsed.users.map((a: { displayName: string }) => a.displayName)).toEqual(["Ada", "Bob"]);
    expect(parsed.machines).toBe(3);
    expect(parsed.activeMachines).toBe(2);
    expect(parsed.usage.state).toBe("NO_SNAPSHOT");
  });

  test("an account may have no user and no optional field", () => {
    const minimal = {
      id: "acc-2",
      provider: "GEMINI",
      accountEmail: "x@y.com",
      users: [],
      machines: 0,
      activeMachines: 0,
      usage: { state: "NO_SNAPSHOT", sharedWith: [] },
    };
    expect(accountListResponseSchema.safeParse(minimal).success).toBe(true);
  });

  test("refuses an unknown provider, a missing provider, a bad link source and no usage", () => {
    expect(accountListResponseSchema.safeParse({ ...ACCOUNT, provider: "COPILOT" }).success).toBe(
      false
    );
    expect(accountListResponseSchema.safeParse({ ...ACCOUNT, provider: undefined }).success).toBe(
      false
    );
    const badSource = { ...ACCOUNT, users: [{ ...ACCOUNT.users[0], source: "IMPORT" }] };
    expect(accountListResponseSchema.safeParse(badSource).success).toBe(false);
    expect(accountListResponseSchema.safeParse({ ...ACCOUNT, usage: undefined }).success).toBe(
      false
    );
  });
});

describe("accountDetailResponseSchema", () => {
  test("adds createdAt and an optional updatedAt", () => {
    const parsed = accountDetailResponseSchema.parse({
      ...ACCOUNT,
      createdAt: "2026-10-20T08:00:00Z",
      updatedAt: null,
    });
    expect(parsed.createdAt).toBe("2026-10-20T08:00:00Z");
    expect(accountDetailResponseSchema.safeParse(ACCOUNT).success).toBe(false);
  });
});

describe("accountQueryRequestSchema", () => {
  test("takes an optional user id", () => {
    expect(accountQueryRequestSchema.parse({})).toEqual({});
    expect(accountQueryRequestSchema.parse({ userId: "user-1" }).userId).toBe("user-1");
  });
});
