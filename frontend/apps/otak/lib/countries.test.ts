import { describe, expect, test } from "bun:test";
import { COUNTRY_CODES, CountrySchema, countryOptions } from "./countries";

describe("COUNTRY_CODES", () => {
  test("holds 249 unique upper-case alpha-2 codes", () => {
    expect(COUNTRY_CODES).toHaveLength(249);
    expect(new Set(COUNTRY_CODES).size).toBe(249);
    for (const code of COUNTRY_CODES) {
      expect(code).toMatch(/^[A-Z]{2}$/);
    }
  });

  test("the schema accepts a listed code and refuses an unknown or lower-case one", () => {
    expect(CountrySchema.safeParse("NL").success).toBe(true);
    expect(CountrySchema.safeParse("XX").success).toBe(false);
    expect(CountrySchema.safeParse("nl").success).toBe(false);
    expect(CountrySchema.safeParse("").success).toBe(false);
  });
});

describe("countryOptions", () => {
  test.each([
    ["en", "Netherlands"],
    ["nl", "Nederland"],
    ["tr", "Hollanda"],
  ])("names every country in %s", (locale, netherlands) => {
    const options = countryOptions(locale);
    expect(options).toHaveLength(COUNTRY_CODES.length);
    expect(options.find((option) => option.value === "NL")?.label).toBe(netherlands);
    for (const option of options) {
      expect(option.label.length).toBeGreaterThan(0);
    }
  });

  test("offers each code once and sorts by name in the given language", () => {
    const options = countryOptions("en");
    expect(new Set(options.map((option) => option.value)).size).toBe(COUNTRY_CODES.length);
    const collator = new Intl.Collator("en");
    const labels = options.map((option) => option.label);
    expect(labels).toEqual([...labels].sort((a, b) => collator.compare(a, b)));
  });
});
