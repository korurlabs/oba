import { describe, expect, test } from "bun:test";
import { resolveOrganizationId } from "./organization-id";

const SERVER_ORG = "org-server";
const SESSION_ORG = "org-session";

describe("resolveOrganizationId", () => {
  test("uses the server's organization before the client has read its session", () => {
    // The server render and the first client render: the keys must equal the prefetched ones.
    expect(resolveOrganizationId({ status: "loading", session: undefined }, SERVER_ORG)).toBe(
      SERVER_ORG
    );
  });

  test("uses the session's organization once the session is read", () => {
    expect(
      resolveOrganizationId(
        { status: "authenticated", session: { user: { defaultOrganizationId: SESSION_ORG } } },
        SERVER_ORG
      )
    ).toBe(SESSION_ORG);
  });

  test("keeps the session's organization while the session reloads after a switch", () => {
    expect(
      resolveOrganizationId(
        { status: "loading", session: { user: { defaultOrganizationId: SESSION_ORG } } },
        SERVER_ORG
      )
    ).toBe(SESSION_ORG);
  });

  test("is null when the session has no organization, whatever the server rendered with", () => {
    expect(
      resolveOrganizationId(
        { status: "authenticated", session: { user: { defaultOrganizationId: null } } },
        SERVER_ORG
      )
    ).toBeNull();
  });

  test("is null without a session", () => {
    expect(
      resolveOrganizationId({ status: "unauthenticated", session: null }, SERVER_ORG)
    ).toBeNull();
  });

  test("is null before the session is read when the server had no organization", () => {
    expect(resolveOrganizationId({ status: "loading", session: undefined }, null)).toBeNull();
  });
});
