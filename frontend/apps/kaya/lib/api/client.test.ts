import { afterEach, describe, expect, test } from "bun:test";
import type { AxiosAdapter, InternalAxiosRequestConfig } from "axios";

import { api } from "./client";
import { KayaApiError } from "./errors";

const realFetch = globalThis.fetch;
const realApiBaseUrl = process.env.API_BASE_URL;

/** Answers the Auth.js session read with `session`, or fails it like a cut-off request. */
function sessionAnswers(session: unknown): void {
  globalThis.fetch = (async (input: RequestInfo | URL) => {
    expect(String(input)).toBe("/api/auth/session");
    if (session instanceof Error) {
      throw session;
    }
    return new Response(JSON.stringify(session), { status: 200 });
  }) as typeof fetch;
}

/** An adapter that records the request instead of sending it. */
function recording(sent: InternalAxiosRequestConfig[]): AxiosAdapter {
  return async (config: InternalAxiosRequestConfig) => {
    sent.push(config);
    return {
      data: { success: true, data: null },
      status: 200,
      statusText: "OK",
      headers: {},
      config,
    };
  };
}

afterEach(() => {
  globalThis.fetch = realFetch;
  if (realApiBaseUrl === undefined) delete process.env.API_BASE_URL;
  else process.env.API_BASE_URL = realApiBaseUrl;
});

describe("api client", () => {
  test("sends the session's access token", async () => {
    sessionAnswers({ accessToken: "at-1" });
    const sent: InternalAxiosRequestConfig[] = [];
    await api.get("/sessions", { adapter: recording(sent) });
    expect(sent).toHaveLength(1);
    expect(sent[0]?.headers.get("Authorization")).toBe("Bearer at-1");
  });

  test("sends no token for a signed-out user", async () => {
    sessionAnswers(null);
    const sent: InternalAxiosRequestConfig[] = [];
    await api.get("/sessions", { adapter: recording(sent) });
    expect(sent).toHaveLength(1);
    expect(sent[0]?.headers.has("Authorization")).toBe(false);
  });

  test("a failed session read fails the call instead of sending it without a token", async () => {
    sessionAnswers(new TypeError("Failed to fetch"));
    const sent: InternalAxiosRequestConfig[] = [];
    const call = api.post("/users/me", undefined, {
      adapter: recording(sent),
      withoutOrganization: true,
    });
    await expect(call).rejects.toBeInstanceOf(KayaApiError);
    await expect(call).rejects.toMatchObject({
      status: null,
      message: "Kaya could not read your session. Check your connection and try again.",
    });
    expect(sent).toHaveLength(0);
  });

  test("takes the API origin from the run-time config on every request", async () => {
    sessionAnswers(null);
    const sent: InternalAxiosRequestConfig[] = [];
    process.env.API_BASE_URL = "https://api.one.test";
    await api.get("/sessions", { adapter: recording(sent) });
    process.env.API_BASE_URL = "https://api.two.test";
    await api.get("/sessions", { adapter: recording(sent) });
    expect(sent.map((config: InternalAxiosRequestConfig) => config.baseURL)).toEqual([
      "https://api.one.test",
      "https://api.two.test",
    ]);
  });
});
