import { describe, expect, test } from "bun:test";

import { safeCallbackPath } from "./callback-url";
import { REAUTH_PARAM, signInUrl } from "./reauth";

describe("signInUrl", () => {
  test("returns to the page, query included", () => {
    const url = new URL(signInUrl("/sessions/s1?stage=plan"), "http://localhost:3014");
    expect(url.pathname).toBe("/api/auth/login");
    expect(safeCallbackPath(url.searchParams.get("callbackUrl"))).toBe("/sessions/s1?stage=plan");
    expect(url.searchParams.has(REAUTH_PARAM)).toBe(false);
  });

  test("asks for a forced sign-in only when told to", () => {
    expect(signInUrl("/")).toBe("/api/auth/login?callbackUrl=%2F");
    expect(signInUrl("/", true)).toBe("/api/auth/login?callbackUrl=%2F&reauth=1");
  });
});
