import { describe, expect, it } from "bun:test";

import { chatKeys } from "@/hooks/queries/chatCache";
import { keepPreviousWithin } from "@/hooks/queries/placeholder";

// Keys shaped as the organization-scoped factories build them: family, organization, rest.
const homeOf = (orgId: string) => ["home", orgId] as const;
const leaderboardOf = (orgId: string) => [...homeOf(orgId), "leaderboard", "SPRINT"] as const;

describe("keepPreviousWithin", () => {
  const data: string[] = ["previous"];

  it("keeps the previous data while another key of the same organization loads", () => {
    const placeholder = keepPreviousWithin(homeOf("org-a"));
    expect(placeholder(data, { queryKey: leaderboardOf("org-a") })).toBe(data);
  });

  it("drops the previous data when it belongs to another organization", () => {
    const placeholder = keepPreviousWithin(homeOf("org-b"));
    expect(placeholder(data, { queryKey: leaderboardOf("org-a") })).toBeUndefined();
  });

  it("drops data whose key is shorter than the scope or from another family", () => {
    const placeholder = keepPreviousWithin(homeOf("org-a"));
    expect(placeholder(data, { queryKey: ["home"] })).toBeUndefined();
    expect(placeholder(data, { queryKey: ["tickets", "org-a", "mine"] })).toBeUndefined();
  });

  it("keeps a search's results while the next search of the same organization loads", () => {
    const placeholder = keepPreviousWithin(chatKeys.all("org-a"));
    const previousQuery = { queryKey: chatKeys.ticketSuggestions("org-a", "KAI") };
    expect(placeholder(data, previousQuery)).toBe(data);
  });

  it("drops a search's results after a switch to another organization", () => {
    const placeholder = keepPreviousWithin(chatKeys.all("org-b"));
    const previousQuery = { queryKey: chatKeys.ticketSuggestions("org-a", "KAI") };
    expect(placeholder(data, previousQuery)).toBeUndefined();
  });

  it("has nothing to keep without a previous query", () => {
    const placeholder = keepPreviousWithin(homeOf("org-a"));
    expect(placeholder(undefined, undefined)).toBeUndefined();
    expect(placeholder(data, undefined)).toBeUndefined();
  });
});
