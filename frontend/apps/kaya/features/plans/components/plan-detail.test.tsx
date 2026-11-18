import { afterAll, describe, expect, mock, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";

import { KayaApiError } from "@/lib/api/errors";
import type { OrganizationRole } from "@/lib/identity/schemas";
import { hasRole } from "@/lib/identity/schemas";

import type { PlanDetailResponse, PlanFeedbackResponse } from "../schemas";

// The detail reads the plan and its version through the plan hooks and the
// user from the identity; those reads are replaced, the rendering is real.
let plan: PlanDetailResponse;
const me = "user-me";
const role: OrganizationRole = "MEMBER";

const CONTENT = `**Goal**: Show plans.
**Steps**:
1. Edit \`features/plans/components/plan-detail.tsx\`.
**Verification**: \`bun test\` passes.
`;

const FEEDBACK: PlanFeedbackResponse[] = [
  {
    id: "f-1",
    versionNumber: 1,
    reviewer: { id: "user-bob", name: "Bob" },
    decision: "REQUEST_CHANGES",
    comment: "Name the tests.",
    lineStart: 2,
    lineEnd: 3,
    quote: "**Steps**:",
    createdAt: "2026-10-25T09:00:00Z",
    deliveredAt: "2026-10-25T09:05:00Z",
    deliveredSessionId: "sess-abcdef123",
  },
  {
    id: "f-2",
    versionNumber: 2,
    reviewer: { id: me, name: "Me" },
    decision: "COMMENT",
    comment: "<b>not markup</b>",
    lineStart: null,
    lineEnd: null,
    quote: null,
    createdAt: "2026-10-25T10:00:00Z",
    deliveredAt: null,
    deliveredSessionId: null,
  },
];

const PLAN: PlanDetailResponse = {
  id: "p-1",
  kind: "SESSION",
  title: "Show plans",
  path: null,
  repository: { id: "r-1", name: "village" },
  pack: null,
  user: { id: "a-1", name: "Ada" },
  reviewState: "OPEN",
  latestVersionNumber: 2,
  openFeedbackCount: 1,
  lastChangedAt: "2026-10-25T10:00:00Z",
  feature: null,
  latestSession: { sessionId: "sess-latest99", title: null, ended: false, branch: "main" },
  versions: [
    {
      number: 1,
      source: "PLAN_MODE",
      sessionId: "sess-1",
      capturedAt: "2026-10-25T08:00:00Z",
      lineCount: 4,
    },
    {
      number: 2,
      source: "MESSAGE",
      sessionId: "sess-1",
      capturedAt: "2026-10-25T09:30:00Z",
      lineCount: 4,
    },
  ],
  feedback: FEEDBACK,
  handoffs: [],
  planningHandoffPath: null,
  canApprove: false,
};

const answer = (data: unknown) => ({
  data: { success: true, data },
  error: null,
  refetch: async () => undefined,
});

// Copies, not the namespaces: mock.module rewrites a loaded module in place, so the
// afterAll restore would otherwise put the mocks back for the files that run later.
const actualIdentity = { ...(await import("@/lib/identity/identity-context")) };
const actualPlans = { ...(await import("../hooks/use-plans")) };

const mutation = { isPending: false, mutateAsync: async () => ({}) };

mock.module("@/lib/identity/identity-context", () => ({
  ...actualIdentity,
  useIdentity: () => ({
    me: { id: me },
    organization: { organizationId: "org-1", name: "Korur", role },
    switchOrganization: () => undefined,
    can: (required: OrganizationRole) => hasRole(role, required),
  }),
}));
mock.module("../hooks/use-plans", () => ({
  ...actualPlans,
  usePlan: () => answer(plan),
  usePlanVersion: () => answer({ number: 2, content: CONTENT }),
  usePlanDiff: () => answer({ number: 2, diff: "" }),
  useGiveFeedback: () => mutation,
  useWithdrawFeedback: () => mutation,
}));

afterAll(() => {
  mock.module("@/lib/identity/identity-context", () => actualIdentity);
  mock.module("../hooks/use-plans", () => actualPlans);
});

const { PlanDetail, planningHandoff } = await import("./plan-detail");
const { PlanFeedbackForm } = await import("./plan-feedback-form");
const { feedbackDecisions, planRefusalText } = await import("./plan-detail-data");

function render(value: PlanDetailResponse) {
  plan = value;
  return renderToStaticMarkup(<PlanDetail planId={value.id} />);
}

const button = (label: string) => new RegExp(`<button[^>]*>${label}</button>`);

describe("Approve and canApprove", () => {
  test("Approve is hidden without canApprove", () => {
    const html = render({ ...PLAN, canApprove: false });
    expect(html).not.toMatch(button("Approve"));
    expect(html).toMatch(button("Request changes"));
    expect(html).toMatch(button("Comment"));
  });

  test("Approve shows with canApprove", () => {
    expect(render({ ...PLAN, canApprove: true })).toMatch(button("Approve"));
  });

  test("an older version takes only a comment", () => {
    expect(feedbackDecisions(true, false)).toEqual(["COMMENT"]);
    expect(feedbackDecisions(false, true)).toEqual(["REQUEST_CHANGES", "COMMENT"]);
    expect(feedbackDecisions(true, true)).toEqual(["REQUEST_CHANGES", "COMMENT", "APPROVE"]);
    const html = renderToStaticMarkup(
      <PlanFeedbackForm
        planId="p-1"
        versionNumber={1}
        isLatestVersion={false}
        canApprove
        range={{ start: 4, end: 6 }}
      />
    );
    expect(html).not.toMatch(button("Approve"));
    expect(html).not.toMatch(button("Request changes"));
    expect(html).toContain("Lines 4 to 6 of version 1");
  });
});

describe("PlanDetail", () => {
  test("shows the header, the outline of the latest version and the feedback", () => {
    const html = render(PLAN);
    expect(html).toContain("Show plans");
    expect(html).toContain("Session plan");
    expect(html).toContain("Version 2 · Message");
    expect(html).toContain("features/plans/components/plan-detail.tsx");
    expect(html).toContain("Not in this plan");
    expect(html).toContain("Name the tests.");
  });

  test("feedback of another version is marked with it, and delivery is spelled out", () => {
    const html = render(PLAN);
    expect(html).toContain("on version 1");
    expect(html).toContain("Delivered to");
    expect(html).toContain("sess-abc");
    expect(html).toContain("Waiting for the session");
    expect(html).toContain("Lines 2 to 3");
  });

  test("only the reviewer's own pending feedback can be withdrawn", () => {
    const html = render(PLAN);
    expect(html.match(/>Withdraw</g)).toHaveLength(1);
  });

  test("plan text is shown as text, never as markup", () => {
    const html = render(PLAN);
    expect(html).toContain("&lt;b&gt;not markup&lt;/b&gt;");
    expect(html).not.toContain("<b>not markup</b>");
  });

  test("a spec names its planning handoff in the side panel", () => {
    const html = render({
      ...PLAN,
      kind: "SPEC",
      path: "docs/private/plans/kaya/04_KAYA_PLANS.md",
      feature: "kaya-plans",
      planningHandoffPath: "kaya-plans/planning.md",
      handoffs: [
        {
          path: "kaya-plans/planning.md",
          status: "DONE",
          unitNumber: null,
          content:
            "# Handoff: kaya-plans / planning\n\n**Status:** DONE\n\n## Next step\n\nRun unit 1.\n\n## Open questions and items\n\nConfirm assumption 4.\n",
        },
      ],
    });
    expect(html).toContain("Planning handoff");
    expect(html).toContain("Run unit 1.");
    expect(html).toContain("Confirm assumption 4.");
  });

  test("a planning handoff path without a loaded handoff shows no panel", () => {
    expect(planningHandoff({ ...PLAN, planningHandoffPath: "x/planning.md" })).toBeNull();
  });
});

describe("planRefusalText", () => {
  test("the plan codes read in plain words", () => {
    const stale = new KayaApiError("conflict", 409, "KAY_PLN_001");
    expect(planRefusalText(stale, "feedback")).toContain("A newer version of this plan exists.");
    const delivered = new KayaApiError("conflict", 409, "KAY_PLN_003");
    expect(planRefusalText(delivered, "withdraw")).toContain("already reached a session");
    const role = new KayaApiError("forbidden", 403, "GEN_SEC_003");
    expect(planRefusalText(role, "feedback")).toBe(
      "Only the user who wrote the plan, an admin or an owner can approve it."
    );
    expect(planRefusalText(role, "withdraw")).toBe("You can withdraw only your own feedback.");
  });

  test("an unknown refusal keeps the server's message", () => {
    expect(planRefusalText(new KayaApiError("Boom", 500, null), "feedback")).toBe("Boom");
  });
});
