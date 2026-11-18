import { describe, expect, test } from "bun:test";

import { LOCALE_COOKIE_NAME, localeCookieConfig } from "./routing";

describe("localeCookieConfig", () => {
  test("names the cookie OBA_LOCALE, not next-intl's default", () => {
    expect(LOCALE_COOKIE_NAME).toBe("OBA_LOCALE");
    expect(localeCookieConfig("https://kaizen.korur.com").name).toBe("OBA_LOCALE");
  });

  test("keeps the choice for one year", () => {
    expect(localeCookieConfig("https://kaizen.korur.com").maxAge).toBe(31_536_000);
  });

  test("shares the cookie across the subdomains of a https base URL", () => {
    expect(localeCookieConfig("https://account.korur.com")).toEqual({
      name: "OBA_LOCALE",
      maxAge: 31_536_000,
      domain: ".korur.com",
    });
  });

  test("leaves the domain key out on localhost", () => {
    const config = localeCookieConfig("http://localhost:3017");

    expect(config).toEqual({ name: "OBA_LOCALE", maxAge: 31_536_000 });
    expect("domain" in config).toBe(false);
  });

  test("leaves the domain key out when there is no base URL", () => {
    const config = localeCookieConfig(undefined);

    expect(config).toEqual({ name: "OBA_LOCALE", maxAge: 31_536_000 });
    expect("domain" in config).toBe(false);
  });
});
