import { describe, expect, test } from "bun:test";

import { extractLocale, stripLocale } from "./locale-prefix";

describe("stripLocale", () => {
  test("removes the prefix and keeps the rest of the path", () => {
    expect(stripLocale("/nl/home")).toBe("/home");
    expect(stripLocale("/en/settings/profile")).toBe("/settings/profile");
  });

  test("gives the root for a bare prefix, with or without a trailing slash", () => {
    expect(stripLocale("/tr")).toBe("/");
    expect(stripLocale("/nl/")).toBe("/");
  });

  test("leaves a path whose first segment only starts with a locale code", () => {
    expect(stripLocale("/enabled")).toBe("/enabled");
    expect(stripLocale("/nlx/home")).toBe("/nlx/home");
  });

  test("leaves an unprefixed path and an unsupported locale code", () => {
    expect(stripLocale("/home")).toBe("/home");
    expect(stripLocale("/")).toBe("/");
    expect(stripLocale("/de/home")).toBe("/de/home");
  });

  test("removes only the leading prefix", () => {
    expect(stripLocale("/nl/docs/en/intro")).toBe("/docs/en/intro");
  });
});

describe("extractLocale", () => {
  test("returns the locale of a prefix", () => {
    expect(extractLocale("/nl/home")).toBe("nl");
    expect(extractLocale("/en")).toBe("en");
  });

  test("returns null when the path has no prefix", () => {
    expect(extractLocale("/home")).toBeNull();
    expect(extractLocale("/enabled")).toBeNull();
    expect(extractLocale("/")).toBeNull();
  });
});
