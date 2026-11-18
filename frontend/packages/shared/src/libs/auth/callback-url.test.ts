import { afterEach, describe, expect, test } from "bun:test";

import { isSafeCallbackUrl, sanitizeCallbackUrl } from "./callback-url";

const originalNodeEnv = process.env.NODE_ENV;

afterEach(() => {
  process.env.NODE_ENV = originalNodeEnv;
});

describe("sanitizeCallbackUrl", () => {
  test.each([undefined, null, ""])("returns undefined for %p", (raw) => {
    expect(sanitizeCallbackUrl(raw)).toBeUndefined();
  });

  test.each(["/", "/invitations", "/organizations/org-1?tab=members"])(
    "keeps the same-origin path %s",
    (path) => {
      expect(sanitizeCallbackUrl(path)).toBe(path);
    },
  );

  test.each(["//evil.example", "/\\evil.example", "/\t/evil.example", "/\n/evil.example"])(
    "refuses the path %p that a browser resolves to another origin",
    (path) => {
      expect(sanitizeCallbackUrl(path)).toBeUndefined();
    },
  );

  test.each([
    "https://kaizen.korur.com/home",
    "https://admin.korur.com/",
    "https://korur.com",
    "https://app.sensey.io/x",
    "http://localhost:3002/home",
  ])("keeps the trusted absolute URL %s", (url) => {
    expect(sanitizeCallbackUrl(url)).toBe(url);
  });

  test.each([
    "https://evil.example/",
    "https://korur.com.evil.example/",
    "https://evilkorur.com/",
    "https://admin.korur.com@evil.example/",
    "javascript://localhost/%0aalert(1)",
    "ftp://localhost/file",
    "not a url",
  ])("refuses the absolute value %s", (url) => {
    expect(sanitizeCallbackUrl(url)).toBeUndefined();
  });
});

describe("isSafeCallbackUrl", () => {
  test("trusts an http *.localhost instance host only in development", () => {
    const url = "http://oba-kaizen-epics.localhost:26101/home";

    process.env.NODE_ENV = "production";
    expect(isSafeCallbackUrl(url)).toBe(false);

    process.env.NODE_ENV = "development";
    expect(isSafeCallbackUrl(url)).toBe(true);
  });

  test("never trusts an https *.localhost host", () => {
    process.env.NODE_ENV = "development";

    expect(isSafeCallbackUrl("https://oba.localhost/home")).toBe(false);
  });
});
