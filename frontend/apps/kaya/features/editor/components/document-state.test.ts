import { describe, expect, test } from "bun:test";

import type { FileHistoryResponse, ReviewResponse } from "@/features/changes/schemas";

import {
  documentState,
  inReviewEntry,
  rejectionOf,
  stateBadge,
  type DocumentStateInput,
} from "./document-state";

function review(values: Partial<ReviewResponse>): ReviewResponse {
  return {
    id: "r-1",
    versionId: "d-1",
    reviewerId: "user-2",
    decision: "COMMENT",
    comment: null,
    createdAt: "2026-10-20T10:00:00Z",
    ...values,
  };
}

function entry(values: Partial<FileHistoryResponse>): FileHistoryResponse {
  return {
    versionId: "v-8",
    number: 8,
    status: "APPROVED",
    source: "MANUAL",
    change: "MODIFIED",
    sha256: "s",
    current: false,
    proposedBy: "user-1",
    reviews: [],
    ...values,
  };
}

const BASE: DocumentStateInput = {
  at: null,
  version: { number: 7, status: "APPROVED" },
  inherited: false,
  editing: false,
  draftDecidedAt: null,
  draftReviews: [],
  history: [],
};

describe("documentState: one state per row of the page map", () => {
  test("current: no draft open, the current version", () => {
    const state = documentState(BASE);
    expect(state).toEqual({ kind: "CURRENT", version: { number: 7, status: "APPROVED" } });
    expect(stateBadge(state)).toEqual({ label: "Approved, version 7", variant: "success" });
  });

  test("draft: the viewer's draft", () => {
    const state = documentState({ ...BASE, editing: true });
    expect(state).toEqual({ kind: "DRAFT" });
    expect(stateBadge(state).label).toBe("Open change");
  });

  test("changes requested: a draft with a decision time and a rejection", () => {
    const rejection = review({ decision: "REJECT", comment: "Too broad." });
    const state = documentState({
      ...BASE,
      editing: true,
      draftDecidedAt: "2026-10-21T10:00:00Z",
      draftReviews: [review({ id: "r-0" }), rejection],
    });
    expect(state).toEqual({ kind: "CHANGES_REQUESTED", review: rejection });
    expect(stateBadge(state)).toEqual({ label: "Changes requested", variant: "error" });
  });

  test("in review: the owner's version in review changed the file", () => {
    const inReview = entry({ versionId: "v-9", number: 9, status: "IN_REVIEW" });
    const state = documentState({ ...BASE, history: [inReview, entry({})] });
    expect(state).toEqual({ kind: "IN_REVIEW", entry: inReview });
    expect(stateBadge(state).label).toBe("In review, version 9");
  });

  test("the viewer's draft wins over a version in review, which it does not wait for", () => {
    const history = [entry({ status: "IN_REVIEW" })];
    expect(documentState({ ...BASE, editing: true, history }).kind).toBe("DRAFT");
  });

  test("older version: `at` set, whatever else holds", () => {
    const state = documentState({
      ...BASE,
      at: "v-3",
      version: { number: 3, status: "SUPERSEDED" },
      inherited: true,
      editing: true,
    });
    expect(state.kind).toBe("OLDER");
    expect(stateBadge(state)).toEqual({ label: "Superseded, version 3", variant: "outline" });
  });

  test("inherited: the file's owner is not the edit target", () => {
    const state = documentState({ ...BASE, inherited: true, editing: false });
    expect(state).toEqual({ kind: "INHERITED" });
    expect(stateBadge(state).label).toBe("Inherited");
  });

  test("an unknown version still names the state", () => {
    expect(stateBadge(documentState({ ...BASE, version: null })).label).toBe("Current");
    expect(stateBadge(documentState({ ...BASE, at: "v-1", version: null })).label).toBe(
      "Older version"
    );
  });
});

describe("rejectionOf", () => {
  test("a draft never decided on was not sent back", () => {
    expect(rejectionOf(null, [review({ decision: "REJECT" })])).toBeNull();
  });

  test("the latest decision counts; comments are no decision", () => {
    const rejected = review({ id: "r-2", decision: "REJECT" });
    expect(
      rejectionOf("t", [review({ decision: "APPROVE" }), rejected, review({ id: "r-3" })])
    ).toBe(rejected);
    expect(rejectionOf("t", [rejected, review({ id: "r-4", decision: "APPROVE" })])).toBeNull();
  });

  test("a draft with a decision time but no rejection left is a plain draft", () => {
    expect(documentState({ ...BASE, editing: true, draftDecidedAt: "t" }).kind).toBe("DRAFT");
  });
});

describe("inReviewEntry", () => {
  test("finds the version in review among the history's newest entries", () => {
    expect(inReviewEntry([entry({})])).toBeNull();
    expect(inReviewEntry([entry({ status: "IN_REVIEW", number: 9 })])?.number).toBe(9);
  });
});
