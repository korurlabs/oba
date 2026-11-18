import { afterEach, describe, expect, mock, test } from "bun:test";
import { usersApi } from "./index";

const originalFetch = globalThis.fetch;

afterEach(() => {
  globalThis.fetch = originalFetch;
});

describe("usersApi.clearDefaultOrganization", () => {
  test("sends DELETE /users/me/default-organization with the token and no organization header", async () => {
    const body = { success: true, message: "ok", data: { defaultOrganizationId: null } };
    const fetchMock = mock(async (_url: string, _init?: RequestInit) => Response.json(body));
    globalThis.fetch = fetchMock as unknown as typeof fetch;

    const response = await usersApi.clearDefaultOrganization("token-1");

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0]!;
    expect(url.endsWith("/users/me/default-organization")).toBe(true);
    expect(init?.method).toBe("DELETE");
    expect(init?.body).toBeUndefined();
    const headers = init?.headers as Record<string, string>;
    expect(headers.Authorization).toBe("Bearer token-1");
    expect(headers["X-Organization-ID"]).toBeUndefined();
    expect(response).toEqual(body as never);
  });
});
