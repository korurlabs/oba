import { describe, expect, it } from "bun:test";

import {
  pullRequestFilterOf,
  setPullRequestFilter,
} from "@/components/pull-requests/pullRequestFilterStore";
import { NO_PULL_REQUEST_FILTER } from "@/components/pull-requests/pullRequestModel";

describe("pull request filter store", () => {
  it("narrows nothing until a filter is set, and without an organization", () => {
    expect(pullRequestFilterOf("org-unset")).toEqual(NO_PULL_REQUEST_FILTER);
    expect(pullRequestFilterOf(null)).toEqual(NO_PULL_REQUEST_FILTER);
  });

  it("keeps each organization's filter apart", () => {
    setPullRequestFilter("org-a", { statuses: ["OPEN"], personId: "user-1" });
    setPullRequestFilter("org-b", { statuses: [], personId: "user-2" });
    expect(pullRequestFilterOf("org-a")).toEqual({ statuses: ["OPEN"], personId: "user-1" });
    expect(pullRequestFilterOf("org-b")).toEqual({ statuses: [], personId: "user-2" });
  });
});
