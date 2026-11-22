import { describe, expect, test } from "bun:test";
import { listedPageTitle, sectionOfRootSlug } from "./page-label";

describe("sectionOfRootSlug", () => {
  test("names the section of a reserved root slug in any case", () => {
    expect(sectionOfRootSlug("policies")).toBe("policies");
    expect(sectionOfRootSlug("Policies")).toBe("policies");
  });

  test("returns null for a root outside the sidebar", () => {
    expect(sectionOfRootSlug("isms")).toBeNull();
  });
});

describe("listedPageTitle", () => {
  test("uses the title when the page has one", () => {
    expect(listedPageTitle({ title: "Access policy", path: "policies/access" })).toBe(
      "Access policy"
    );
  });

  test("falls back to the last slug of the path", () => {
    expect(listedPageTitle({ title: null, path: "policies/access" })).toBe("access");
    expect(listedPageTitle({ title: "", path: "controls" })).toBe("controls");
  });
});
