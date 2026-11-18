import { describe, expect, test } from "bun:test";

import { hasRole, type OrganizationRole } from "@/lib/identity/schemas";

import type { ChangeDetailResponse } from "../schemas";
import { approverText, reviewActions } from "./change-data";

type Detail = Parameters<typeof reviewActions>[0];

function detail(over: Partial<Omit<Detail, "version">> & { proposedBy?: string; status?: string }) {
  const { proposedBy = "user-ada", status = "IN_REVIEW", ...rest } = over;
  return {
    version: { status, proposedBy } as ChangeDetailResponse["version"],
    requiredRole: "ADMIN",
    violations: [],
    canApprove: true,
    canReview: true,
    ...rest,
  } as Detail;
}

const as = (role: OrganizationRole, id = "user-me") => ({
  me: { id },
  can: (required: OrganizationRole) => hasRole(role, required),
});

const ROLES: OrganizationRole[] = ["MEMBER", "ADMIN", "OWNER"];
const STATUSES = ["DRAFT", "APPROVED", "REJECTED", "SUPERSEDED"];

describe("review buttons per role and status", () => {
  test("a member sees nothing, whatever the server permits", () => {
    for (const status of ["IN_REVIEW", ...STATUSES]) {
      const actions = reviewActions(detail({ status }), as("MEMBER"));
      expect([actions.approve.kind, actions.reject.kind, actions.comment.kind]).toEqual([
        "hidden",
        "hidden",
        "hidden",
      ]);
    }
  });

  test("no role sees a review control outside IN_REVIEW", () => {
    for (const role of ROLES) {
      for (const status of STATUSES) {
        expect(reviewActions(detail({ status }), as(role)).approve.kind).toBe("hidden");
      }
    }
  });

  test("an admin and an owner both see the controls of an ordinary change", () => {
    for (const role of ["ADMIN", "OWNER"] as const) {
      const actions = reviewActions(detail({}), as(role));
      expect([actions.approve.kind, actions.reject.kind, actions.comment.kind]).toEqual([
        "enabled",
        "enabled",
        "enabled",
      ]);
    }
  });
});

describe("plugin and executable code needs an owner", () => {
  const executable = detail({ requiredRole: "OWNER", canApprove: false });

  test("an admin sees Approve greyed out with the owner reason, and still rejects and comments", () => {
    const actions = reviewActions(executable, as("ADMIN"));
    expect(actions.approve).toEqual({
      kind: "disabled",
      reason:
        "This change touches executable code, hooks or permissions; only an owner approves it.",
    });
    expect(actions.reject.kind).toBe("enabled");
    expect(actions.comment.kind).toBe("enabled");
  });

  test("an owner who the server lets approve sees Approve enabled", () => {
    expect(
      reviewActions(detail({ requiredRole: "OWNER", canApprove: true }), as("OWNER")).approve.kind
    ).toBe("enabled");
  });

  test("the owner reason is not given to an owner refused for another cause", () => {
    const actions = reviewActions(
      detail({ requiredRole: "OWNER", canApprove: false, violations: [{ path: "a", rule: "X" }] }),
      as("OWNER")
    );
    expect(actions.approve.kind === "disabled" && actions.approve.reason).toContain(
      "breaks the rules"
    );
  });

  test("the approver is named from the required role", () => {
    expect(approverText("OWNER")).toBe("An owner");
    expect(approverText("ADMIN")).toBe("An admin or owner");
  });
});

describe("the proposer rule", () => {
  test("the proposer sees the proposer reason on all three controls, as admin or owner", () => {
    for (const role of ["ADMIN", "OWNER"] as const) {
      const actions = reviewActions(
        detail({ proposedBy: "user-me", canApprove: false, canReview: false }),
        as(role)
      );
      const reason = "You proposed this change, so another admin or owner reviews it.";
      expect(actions.approve).toEqual({ kind: "disabled", reason });
      expect(actions.reject).toEqual({ kind: "disabled", reason });
      expect(actions.comment).toEqual({ kind: "disabled", reason });
    }
  });

  test("the proposer rule wins over the owner and violation reasons", () => {
    const actions = reviewActions(
      detail({
        proposedBy: "user-me",
        requiredRole: "OWNER",
        canApprove: false,
        canReview: false,
        violations: [{ path: "a", rule: "X" }],
      }),
      as("ADMIN")
    );
    expect(actions.approve.kind === "disabled" && actions.approve.reason).toContain(
      "You proposed this change"
    );
  });

  test("another reviewer is not told they proposed it", () => {
    const actions = reviewActions(
      detail({ canApprove: false, canReview: false }),
      as("ADMIN", "user-bob")
    );
    expect(actions.reject).toEqual({
      kind: "disabled",
      reason: "You cannot review this change now.",
    });
    expect(actions.approve.kind === "disabled" && actions.approve.reason).toBe(
      "You cannot approve this change now."
    );
  });

  test("a change without a violations list is handled", () => {
    const actions = reviewActions(
      detail({ canApprove: false, violations: null as unknown as Detail["violations"] }),
      as("ADMIN")
    );
    expect(actions.approve.kind).toBe("disabled");
  });
});
