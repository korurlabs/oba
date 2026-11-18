import { describe, expect, it } from "bun:test";

import en from "@/i18n/locales/en.json";
import nl from "@/i18n/locales/nl.json";
import tr from "@/i18n/locales/tr.json";

describe("locale files", () => {
  it("nl and tr have exactly the keys of en", () => {
    const englishKeys: string[] = Object.keys(en).sort();
    expect(Object.keys(nl).sort()).toEqual(englishKeys);
    expect(Object.keys(tr).sort()).toEqual(englishKeys);
  });

  it("no translation is empty", () => {
    for (const messages of [en, nl, tr] as Record<string, string>[]) {
      for (const [key, value] of Object.entries(messages)) {
        expect({ key, empty: value.trim() === "" }).toEqual({ key, empty: false });
      }
    }
  });
});
