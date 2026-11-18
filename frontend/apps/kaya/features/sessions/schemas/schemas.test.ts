import { describe, expect, test } from "bun:test";

import {
  sessionDetailResponseSchema,
  sessionFiltersResponseSchema,
  sessionHandoffResponseSchema,
  sessionHandoffSummaryResponseSchema,
  sessionLinkCreateRequestSchema,
  sessionListResponseSchema,
  sessionQueryRequestSchema,
  repositoryResponseSchema,
} from ".";

const SESSION = {
  id: "c1",
  sessionId: "s1",
  ended: false,
  eventCount: 12,
  features: ["board-export"],
  links: [],
  createdAt: "2026-10-23T09:00:00Z",
};

describe("sessionListResponseSchema", () => {
  test("reads a session with only its required fields", () => {
    expect(sessionListResponseSchema.safeParse(SESSION).success).toBe(true);
  });

  test("reads the user, machine, account and links of a full session", () => {
    const parsed = sessionListResponseSchema.parse({
      ...SESSION,
      title: "Export",
      repositoryId: "r",
      repositoryName: "village",
      userId: "a",
      userDisplayName: "Ada",
      machineId: "g",
      machineName: "ada-mini",
      identityConflictUserId: "b",
      identityConflictUserDisplayName: "Bob",
      model: "claude-opus",
      accountOrganizationUuid: "uuid",
      lastEventAt: "2026-10-23T09:05:00Z",
      links: [
        {
          id: "l1",
          kind: "PULL_REQUEST",
          reference: "#12",
          url: "https://github.com/senseylabs/x/pull/12",
          source: "RECORDER",
          createdAt: "t",
        },
      ],
    });
    expect(parsed.identityConflictUserId).toBe("b");
    expect(parsed.links[0]?.kind).toBe("PULL_REQUEST");
  });

  test("refuses a link kind the app does not know", () => {
    expect(
      sessionListResponseSchema.safeParse({
        ...SESSION,
        links: [{ id: "l", kind: "SLACK", reference: "x", source: "MANUAL", createdAt: "t" }],
      }).success
    ).toBe(false);
  });
});

describe("sessionFiltersResponseSchema", () => {
  test("reads the choices of every filter", () => {
    expect(
      sessionFiltersResponseSchema.safeParse({
        users: [{ id: "a", displayName: "Ada" }],
        machines: [{ id: "g", machineName: "ada-mini", userId: null }],
        models: ["claude-opus"],
        subscriptions: [{ uuid: "u", name: "Korur", billingType: null }],
      }).success
    ).toBe(true);
    expect(
      sessionFiltersResponseSchema.safeParse({ users: [], machines: [], models: [] }).success
    ).toBe(false);
  });
});

describe("sessionQueryRequestSchema and repositories", () => {
  test("a page holds at most 60 sessions", () => {
    expect(sessionQueryRequestSchema.safeParse({ amount: 60 }).success).toBe(true);
    expect(sessionQueryRequestSchema.safeParse({ amount: 61 }).success).toBe(false);
    expect(sessionQueryRequestSchema.safeParse({ page: 0 }).success).toBe(false);
    expect(sessionQueryRequestSchema.safeParse({ search: "a".repeat(201) }).success).toBe(false);
  });

  test("reads a repository", () => {
    expect(
      repositoryResponseSchema.safeParse({
        id: "r",
        name: "n",
        remoteUrl: "u",
        status: "ACTIVE",
      }).success
    ).toBe(true);
    expect(repositoryResponseSchema.safeParse({ id: "r", name: "n" }).success).toBe(false);
  });
});

const DETAIL = {
  ...SESSION,
  ended: true,
  subagents: [],
  skillUses: [],
  handoffs: [],
};

describe("sessionDetailResponseSchema", () => {
  test("reads a detail with its handoffs", () => {
    const parsed = sessionDetailResponseSchema.parse({
      ...DETAIL,
      handoffs: [{ path: "k/a.md", status: "PARTIAL" }],
    });
    expect(parsed.handoffs[0]?.status).toBe("PARTIAL");
  });

  test("refuses a detail without its lists", () => {
    const { handoffs: _handoffs, ...rest } = DETAIL;
    void _handoffs;
    expect(sessionDetailResponseSchema.safeParse(rest).success).toBe(false);
  });
});

describe("handoff responses", () => {
  test("a handoff with its content, and a summary whose status is missing", () => {
    expect(
      sessionHandoffResponseSchema.safeParse({ path: "k/a.md", status: "DONE", content: "# x" })
        .success
    ).toBe(true);
    expect(
      sessionHandoffSummaryResponseSchema.safeParse({ path: "k/a.md", status: null }).success
    ).toBe(true);
    expect(sessionHandoffResponseSchema.safeParse({ path: "k/a.md" }).success).toBe(false);
    expect(
      sessionHandoffSummaryResponseSchema.safeParse({ path: "k/a.md", status: "FINISHED" }).success
    ).toBe(false);
  });
});

describe("sessionLinkCreateRequestSchema", () => {
  const link = (kind: string, reference: string) =>
    sessionLinkCreateRequestSchema.safeParse({ kind, reference });

  test("a ticket is a Kazan key, stripped of surrounding whitespace", () => {
    const parsed = sessionLinkCreateRequestSchema.parse({
      kind: "KAZAN_TICKET",
      reference: "  KAYA-42 ",
    });
    expect(parsed.reference).toBe("KAYA-42");
    expect(link("KAZAN_TICKET", "kaya-42").success).toBe(false);
    expect(link("KAZAN_TICKET", "K-42").success).toBe(false);
    expect(link("KAZAN_TICKET", "KAYA-42x").success).toBe(false);
  });

  test("a pull request is a GitHub pull request URL", () => {
    expect(link("PULL_REQUEST", "https://github.com/senseylabs/village/pull/7").success).toBe(true);
    expect(link("PULL_REQUEST", "http://github.com/senseylabs/village/pull/7").success).toBe(false);
    expect(link("PULL_REQUEST", "https://github.com/senseylabs/village/issues/7").success).toBe(
      false
    );
    expect(link("PULL_REQUEST", "KAYA-42").success).toBe(false);
  });

  test("a blank or overlong reference and an unknown kind are refused", () => {
    expect(link("KAZAN_TICKET", "   ").success).toBe(false);
    expect(link("KAZAN_TICKET", `KAYA-${"1".repeat(500)}`).success).toBe(false);
    expect(link("ISSUE", "KAYA-42").success).toBe(false);
  });

  test("a refused reference names the expected form", () => {
    const result = link("KAZAN_TICKET", "nope");
    expect(result.success).toBe(false);
    expect(result.error?.issues[0]?.path).toEqual(["reference"]);
    expect(result.error?.issues[0]?.message).toContain("KAYA-42");
  });
});

describe("sessionDetailResponseSchema, config release", () => {
  test("reads the release, its state and how it was delivered", () => {
    const parsed = sessionDetailResponseSchema.parse({
      ...DETAIL,
      releaseId: "r7",
      releaseNumber: 7,
      releaseState: "STALE",
      deliveredBy: "HOOK",
    });
    expect(parsed.releaseNumber).toBe(7);
    expect(parsed.releaseState).toBe("STALE");
    expect(parsed.deliveredBy).toBe("HOOK");
  });

  test("a session without a reported release reads as null fields", () => {
    const parsed = sessionDetailResponseSchema.parse({
      ...DETAIL,
      releaseId: null,
      releaseNumber: null,
      releaseState: null,
      deliveredBy: null,
    });
    expect(parsed.releaseState).toBeNull();
  });
});
