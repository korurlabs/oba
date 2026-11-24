import { describe, expect, it, mock } from "bun:test";

import { keepPreviousWithin } from "@/hooks/queries/placeholder";

// Bun cannot parse react-native's Flow source; lib/api.ts reads only Platform.OS.
mock.module("react-native", () => ({
  Platform: { OS: "ios" },
  AppState: { addEventListener: () => ({ remove: () => undefined }) },
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

const { pullRequestKeys } = await import("@/hooks/queries/pullRequests");

const ORG = "org-a";
const REPO = 101;
const OTHER_REPO = 202;

describe("GitHub pull request picker placeholder", () => {
  const rows: string[] = ["#7 of the previous list"];

  it("keeps the rows while the next page or search of the same repository loads", () => {
    const placeholder = keepPreviousWithin(pullRequestKeys.githubRepository(ORG, REPO));
    const firstSearch = pullRequestKeys.githubPullRequests(ORG, REPO, "", [], []);
    const narrowed = pullRequestKeys.githubPullRequests(ORG, REPO, "login", ["OPEN"], ["alice"]);
    expect(placeholder(rows, { queryKey: firstSearch })).toBe(rows);
    expect(placeholder(rows, { queryKey: narrowed })).toBe(rows);
  });

  it("drops the rows of another repository, so its row numbers cannot be picked", () => {
    const placeholder = keepPreviousWithin(pullRequestKeys.githubRepository(ORG, REPO));
    const otherRepository = pullRequestKeys.githubPullRequests(ORG, OTHER_REPO, "", [], []);
    expect(placeholder(rows, { queryKey: otherRepository })).toBeUndefined();
  });

  it("drops the rows of the same repository number in another organization", () => {
    const placeholder = keepPreviousWithin(pullRequestKeys.githubRepository("org-b", REPO));
    const otherOrganization = pullRequestKeys.githubPullRequests(ORG, REPO, "", [], []);
    expect(placeholder(rows, { queryKey: otherOrganization })).toBeUndefined();
  });

  it("builds every repository key under the repository scope", () => {
    const scope = pullRequestKeys.githubRepository(ORG, REPO);
    const placeholder = keepPreviousWithin(scope);
    expect(placeholder(rows, { queryKey: pullRequestKeys.githubContributors(ORG, REPO) })).toBe(
      rows
    );
    expect(placeholder(rows, { queryKey: pullRequestKeys.githubPullRequest(ORG, REPO, 7) })).toBe(
      rows
    );
  });
});
