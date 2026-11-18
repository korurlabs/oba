import { describe, expect, test } from "bun:test";

import { KayaApiError } from "@/lib/api/errors";
import { hasRole, type OrganizationRole } from "@/lib/identity/schemas";

import type { ChangeDetailResponse } from "../schemas";
import type { FileDiffResponse } from "@/features/versions/schemas";
import {
  diffLines,
  diffNotes,
  diffStats,
  mayRestore,
  rebaseConflictsOf,
  reviewActions,
  toChangeQuery,
} from "./change-data";

type Detail = Parameters<typeof reviewActions>[0];

function detail(
  overrides: Partial<Omit<Detail, "version">> & { status?: string; proposedBy?: string } = {}
): Detail {
  const { status = "IN_REVIEW", proposedBy = "user-ada", ...rest } = overrides;
  return {
    version: { status, proposedBy } as ChangeDetailResponse["version"],
    requiredRole: "ADMIN",
    violations: [],
    canApprove: true,
    canReview: true,
    ...rest,
  };
}

function viewer(role: OrganizationRole, id = "user-me") {
  return { me: { id }, can: (required: OrganizationRole) => hasRole(role, required) };
}

describe("reviewActions", () => {
  test("a member sees no review control", () => {
    expect(reviewActions(detail(), viewer("MEMBER"))).toEqual({
      approve: { kind: "hidden" },
      reject: { kind: "hidden" },
      comment: { kind: "hidden" },
    });
  });

  test("nothing is reviewed outside IN_REVIEW", () => {
    const actions = reviewActions(detail({ status: "APPROVED" }), viewer("OWNER"));
    expect(actions.approve.kind).toBe("hidden");
    expect(actions.comment.kind).toBe("hidden");
  });

  test("an eligible admin approves, rejects and comments", () => {
    const actions = reviewActions(detail(), viewer("ADMIN"));
    expect(actions.approve.kind).toBe("enabled");
    expect(actions.reject.kind).toBe("enabled");
    expect(actions.comment.kind).toBe("enabled");
  });

  test("the proposer's controls are greyed out with the proposer rule", () => {
    const actions = reviewActions(
      detail({ proposedBy: "user-me", canApprove: false, canReview: false }),
      viewer("OWNER")
    );
    expect(actions.approve).toEqual({
      kind: "disabled",
      reason: "You proposed this change, so another admin or owner reviews it.",
    });
    expect(actions.reject.kind).toBe("disabled");
    expect(actions.comment.kind).toBe("disabled");
  });

  test("an admin may not approve executable code but may reject it", () => {
    const actions = reviewActions(
      detail({ requiredRole: "OWNER", canApprove: false, canReview: true }),
      viewer("ADMIN")
    );
    expect(actions.approve.kind).toBe("disabled");
    expect(actions.approve.kind === "disabled" && actions.approve.reason).toContain(
      "only an owner approves it"
    );
    expect(actions.reject.kind).toBe("enabled");
  });

  test("a change that breaks rules cannot be approved now", () => {
    const actions = reviewActions(
      detail({ canApprove: false, violations: [{ path: "a", rule: "FILE_TOO_LARGE" }] }),
      viewer("OWNER")
    );
    expect(actions.approve.kind === "disabled" && actions.approve.reason).toContain(
      "breaks the rules"
    );
  });

  test("an owner approving their own rollback follows the server", () => {
    const actions = reviewActions(
      detail({ proposedBy: "user-me", requiredRole: "OWNER", canApprove: true, canReview: false }),
      viewer("OWNER")
    );
    expect(actions.approve.kind).toBe("enabled");
    expect(actions.reject.kind).toBe("disabled");
  });
});

describe("mayRestore", () => {
  test("a released version other than the current one", () => {
    expect(mayRestore({ id: "v-1", status: "SUPERSEDED" }, "v-2")).toBe(true);
    expect(mayRestore({ id: "v-2", status: "APPROVED" }, "v-2")).toBe(false);
    expect(mayRestore({ id: "v-2", status: "APPROVED" }, "v-3")).toBe(true);
  });

  test("never a draft or a version in review", () => {
    expect(mayRestore({ id: "v-1", status: "DRAFT" }, "v-2")).toBe(false);
    expect(mayRestore({ id: "v-1", status: "IN_REVIEW" }, "v-2")).toBe(false);
  });

  test("while the current version is unknown, only a superseded one", () => {
    expect(mayRestore({ id: "v-1", status: "SUPERSEDED" }, undefined)).toBe(true);
    expect(mayRestore({ id: "v-1", status: "APPROVED" }, undefined)).toBe(false);
  });
});

describe("rebaseConflictsOf", () => {
  test("reads the conflicts of KAY_CFG_002 and skips malformed entries", () => {
    const error = new KayaApiError("x", 409, "KAY_CFG_002", {
      currentVersionId: "v-9",
      conflicts: [
        { path: "AGENTS.md", draftChange: "MODIFIED", currentChange: "DELETED" },
        { draftChange: "MODIFIED" },
      ],
    });
    expect(rebaseConflictsOf(error)).toEqual([
      { path: "AGENTS.md", draftChange: "MODIFIED", currentChange: "DELETED" },
    ]);
  });

  test("an empty list for a stale base without conflicts, null for another error", () => {
    expect(rebaseConflictsOf(new KayaApiError("x", 409, "KAY_CFG_002", {}))).toEqual([]);
    expect(rebaseConflictsOf(new KayaApiError("x", 409, "KAY_CFG_015"))).toBeNull();
    expect(rebaseConflictsOf(new Error("boom"))).toBeNull();
  });
});

describe("toChangeQuery", () => {
  test("sends only the filters that are set, from page 1", () => {
    expect(toChangeQuery({ status: null, packId: null, page: 0 })).toEqual({
      page: 1,
      amount: 25,
    });
    expect(toChangeQuery({ status: "IN_REVIEW", packId: "p-1", page: 3 })).toEqual({
      page: 3,
      amount: 25,
      status: "IN_REVIEW",
      packId: "p-1",
    });
    expect(toChangeQuery({ status: null, packId: "", page: 1 })).toEqual({
      page: 1,
      amount: 25,
    });
    expect(toChangeQuery({ status: null, packId: "k-1", page: 1 })).toEqual({
      page: 1,
      amount: 25,
      packId: "k-1",
    });
  });
});

const DIFF =
  "--- a/AGENTS.md\n+++ b/AGENTS.md\n@@ -1,2 +1,2 @@\n # Title\n-old\n+new\n+more\n\\ No newline at end of file\n";

const ENTRY: FileDiffResponse = {
  path: "AGENTS.md",
  change: "MODIFIED",
  oldMode: "FILE",
  newMode: "FILE",
  oldSha256: "a",
  newSha256: "b",
  oldSymlinkTarget: null,
  newSymlinkTarget: null,
  binary: false,
  unifiedDiff: DIFF,
};

describe("diff helpers", () => {
  test("classifies each line of a unified diff", () => {
    expect(diffLines(DIFF).map((line) => line.kind)).toEqual([
      "header",
      "header",
      "hunk",
      "context",
      "removed",
      "added",
      "added",
      "note",
    ]);
  });

  test("counts added and removed lines, not the headers", () => {
    expect(diffStats(DIFF)).toEqual({ added: 2, removed: 1 });
    expect(diffStats(null)).toEqual({ added: 0, removed: 0 });
  });

  test("notes a mode change, a symlink, binary content", () => {
    expect(diffNotes(ENTRY)).toEqual([]);
    expect(diffNotes({ ...ENTRY, newMode: "EXECUTABLE", unifiedDiff: "" })).toEqual([
      "Changed from a file to an executable.",
    ]);
    expect(
      diffNotes({
        ...ENTRY,
        change: "ADDED",
        oldMode: null,
        newMode: "SYMLINK",
        newSymlinkTarget: "AGENTS.md",
        unifiedDiff: null,
      })
    ).toEqual(["A symlink to AGENTS.md."]);
    expect(diffNotes({ ...ENTRY, binary: true, unifiedDiff: null })).toEqual([
      "Binary content; no line diff is shown.",
    ]);
  });
});
