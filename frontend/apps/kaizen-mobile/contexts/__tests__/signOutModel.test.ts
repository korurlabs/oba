import { describe, expect, it } from "bun:test";

import { markNudged, nudgedMembersOf } from "@/components/updates/nudgeStore";
import { setUpdateFilter, updateFilterOf } from "@/components/updates/updateFilterStore";
import { NO_UPDATE_FILTER } from "@/components/updates/updateModel";
import {
  pullRequestFilterOf,
  setPullRequestFilter,
} from "@/components/pull-requests/pullRequestFilterStore";
import { NO_PULL_REQUEST_FILTER } from "@/components/pull-requests/pullRequestModel";
import { clearSessionStores } from "@/contexts/sessionStores";
import { signOutWarningKey } from "@/contexts/signOutModel";

describe("clearSessionStores", () => {
  it("forgets the filters and nudges of the user who signs out", () => {
    setPullRequestFilter("org-a", { statuses: ["OPEN"], personId: "user-1" });
    setUpdateFilter("org-a", { day: "2026-11-06", personId: "user-1" });
    markNudged("org-a", "2026-11-09", "user-2");

    clearSessionStores();

    expect(pullRequestFilterOf("org-a")).toEqual(NO_PULL_REQUEST_FILTER);
    expect(updateFilterOf("org-a")).toEqual(NO_UPDATE_FILTER);
    expect(nudgedMembersOf("org-a", "2026-11-09").size).toBe(0);
  });
});

describe("signOutWarningKey", () => {
  it("warns when the push device could not be unregistered", () => {
    expect(signOutWarningKey({ pushDeviceUnregistered: false, grantRevoked: true })).toBe(
      "authSignOutPushStillRegistered"
    );
  });

  it("stays quiet when push was unregistered or there was no device", () => {
    expect(signOutWarningKey({ pushDeviceUnregistered: true, grantRevoked: true })).toBeNull();
    expect(signOutWarningKey({ pushDeviceUnregistered: null, grantRevoked: null })).toBeNull();
  });

  it("stays quiet when only the grant revocation failed", () => {
    expect(signOutWarningKey({ pushDeviceUnregistered: true, grantRevoked: false })).toBeNull();
  });
});
