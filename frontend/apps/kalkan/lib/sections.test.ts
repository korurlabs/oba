import { describe, expect, test } from "bun:test";
import enMessages from "@/locales/en.json";
import { SECTION_GROUPS, SECTION_ROOT_TITLES, SECTION_SLUGS, isSectionSlug } from "./sections";

describe("sections", () => {
  test("lists the approved sections in sidebar order", () => {
    expect(SECTION_SLUGS).toEqual([
      "frameworks",
      "controls",
      "policies",
      "risks",
      "evidence",
      "audits",
      "assets",
      "suppliers",
      "access",
      "architecture",
      "protection",
      "monitoring",
      "infrastructure",
      "guides",
      "laws",
      "incentives",
    ]);
    expect(SECTION_GROUPS.map((group) => group.key)).toEqual([
      "compliance",
      "inventory",
      "knowledge",
      "legal",
    ]);
  });

  test("accepts only a section slug as a route segment", () => {
    expect(isSectionSlug("policies")).toBe(true);
    expect(isSectionSlug("Policies")).toBe(false);
    expect(isSectionSlug("policy")).toBe(false);
    expect(isSectionSlug("pages")).toBe(false);
    expect(isSectionSlug("")).toBe(false);
  });

  test("names every section in the messages and in both content locales", () => {
    for (const slug of SECTION_SLUGS) {
      expect(enMessages.sidebar.sections[slug]).toBeString();
      expect(SECTION_ROOT_TITLES[slug].TR).not.toBe("");
      expect(SECTION_ROOT_TITLES[slug].EN).toBe(enMessages.sidebar.sections[slug]);
    }
  });
});
