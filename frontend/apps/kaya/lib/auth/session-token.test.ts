import { describe, expect, test } from "bun:test";

import { sessionAccessToken, SessionUnavailableError } from "./session-token";

function answering(status: number, body: unknown): (input: string) => Promise<Response> {
  return async () => new Response(JSON.stringify(body), { status });
}

describe("sessionAccessToken", () => {
  test("returns the access token of a signed-in session", async () => {
    expect(await sessionAccessToken(answering(200, { accessToken: "at-1" }))).toBe("at-1");
  });

  test("reads the Auth.js session endpoint", async () => {
    const asked: string[] = [];
    await sessionAccessToken(async (input: string) => {
      asked.push(input);
      return new Response("null");
    });
    expect(asked).toEqual(["/api/auth/session"]);
  });

  test("returns null for a signed-out user and for a session that can no longer be refreshed", async () => {
    expect(await sessionAccessToken(answering(200, null))).toBeNull();
    expect(
      await sessionAccessToken(
        answering(200, { accessToken: "at-1", error: "REFRESH_TOKEN_ERROR" })
      )
    ).toBeNull();
  });

  test("a session request that fails rejects, so no call goes out without the token", async () => {
    const cutOff = sessionAccessToken(async () => {
      throw new TypeError("Failed to fetch");
    });
    await expect(cutOff).rejects.toBeInstanceOf(SessionUnavailableError);
    await expect(sessionAccessToken(answering(500, { message: "boom" }))).rejects.toBeInstanceOf(
      SessionUnavailableError
    );
    await expect(
      sessionAccessToken(async () => new Response("<html>", { status: 200 }))
    ).rejects.toBeInstanceOf(SessionUnavailableError);
  });
});
