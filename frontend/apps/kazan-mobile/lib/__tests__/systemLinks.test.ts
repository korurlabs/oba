import { describe, expect, it } from "bun:test";

import { isAppRootLink, isDevClientLink } from "@/lib/systemLinks";

describe("isAppRootLink", () => {
  it("accepts the root URL the router starts with when no link opened the app", () => {
    expect(isAppRootLink("kazan:///")).toBe(true);
    expect(isAppRootLink("kazan://")).toBe(true);
    expect(isAppRootLink("KAZAN:///")).toBe(true);
  });

  it("accepts a bare root path and a root with only a query or fragment", () => {
    expect(isAppRootLink("/")).toBe(true);
    expect(isAppRootLink("")).toBe(true);
    expect(isAppRootLink("kazan:///?source=widget")).toBe(true);
    expect(isAppRootLink("/#top")).toBe(true);
  });

  it("rejects a link that names a screen", () => {
    expect(isAppRootLink("kazan:///share/ticket?ticketKey=ALP-4")).toBe(false);
    expect(isAppRootLink("kazan://ticket/ALP-4")).toBe(false);
    expect(isAppRootLink("/home")).toBe(false);
    expect(isAppRootLink("https://kazan.test/en/ticket/ALP-4")).toBe(false);
  });

  it("rejects the root of another scheme or a web host", () => {
    expect(isAppRootLink("https://kazan.test/")).toBe(false);
    expect(isAppRootLink("exp+kazan:///")).toBe(false);
  });
});

describe("isDevClientLink", () => {
  it("accepts the development client URL under any of the app's schemes", () => {
    expect(
      isDevClientLink(
        "com.korur.kazan://expo-development-client/?url=http%3A%2F%2Flocalhost%3A8082"
      )
    ).toBe(true);
    expect(isDevClientLink("exp+kazan://expo-development-client/?url=x")).toBe(true);
    expect(isDevClientLink("kazan://expo-development-client")).toBe(true);
    expect(isDevClientLink(" KAZAN://EXPO-DEVELOPMENT-CLIENT/ ")).toBe(true);
  });

  it("rejects the app's own links and web links", () => {
    expect(isDevClientLink("kazan:///")).toBe(false);
    expect(isDevClientLink("kazan://ticket/ALP-4")).toBe(false);
    expect(isDevClientLink("/expo-development-client")).toBe(false);
    expect(isDevClientLink("https://expo-development-client/")).toBe(false);
    expect(isDevClientLink("kazan://expo-development-client-x/")).toBe(false);
  });
});
