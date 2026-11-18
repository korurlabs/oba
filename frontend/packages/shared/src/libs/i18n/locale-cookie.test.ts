import { describe, expect, test } from "bun:test";

import { Locale } from "./available-languages";
import { localeCookieString } from "./locale-cookie";

describe("localeCookieString", () => {
  test("stores the choice in OBA_LOCALE for one year on every path", () => {
    expect(localeCookieString(Locale.NL, "http://localhost:3017")).toBe(
      "OBA_LOCALE=nl; path=/; max-age=31536000; samesite=lax",
    );
  });

  test("writes the parent domain of a https base URL, as the proxy does", () => {
    expect(localeCookieString(Locale.EN, "https://account.korur.com")).toBe(
      "OBA_LOCALE=en; path=/; max-age=31536000; samesite=lax; domain=.korur.com",
    );
  });

  test("writes no domain when there is no base URL", () => {
    expect(localeCookieString(Locale.TR, undefined)).not.toContain("domain");
  });
});
