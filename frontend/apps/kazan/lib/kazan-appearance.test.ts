import { describe, expect, test } from "bun:test";
import {
  KAZAN_APPEARANCE_KEY,
  normalizeKazanAppearance,
  storedKazanAppearance,
} from "./kazan-appearance";

describe("normalizeKazanAppearance", () => {
  test("is Modern on the grid when nothing is stored", () => {
    expect(normalizeKazanAppearance(undefined)).toEqual({ layout: "modern", background: "grid" });
    expect(normalizeKazanAppearance(null)).toEqual({ layout: "modern", background: "grid" });
    expect(normalizeKazanAppearance({})).toEqual({ layout: "modern", background: "grid" });
  });

  test("gives Classic the plain background until one is picked", () => {
    expect(normalizeKazanAppearance({ [KAZAN_APPEARANCE_KEY]: { layout: "classic" } })).toEqual({
      layout: "classic",
      background: "plain",
    });
  });

  test("keeps a picked background in either layout", () => {
    expect(
      normalizeKazanAppearance({
        [KAZAN_APPEARANCE_KEY]: { layout: "classic", background: "grid" },
      })
    ).toEqual({ layout: "classic", background: "grid" });
    expect(normalizeKazanAppearance({ [KAZAN_APPEARANCE_KEY]: { background: "slate" } })).toEqual({
      layout: "modern",
      background: "slate",
    });
  });

  test("ignores unknown values and a record of the wrong shape", () => {
    expect(
      normalizeKazanAppearance({ [KAZAN_APPEARANCE_KEY]: { layout: "retro", background: 3 } })
    ).toEqual({ layout: "modern", background: "grid" });
    expect(normalizeKazanAppearance({ [KAZAN_APPEARANCE_KEY]: "classic" })).toEqual({
      layout: "modern",
      background: "grid",
    });
    expect(
      normalizeKazanAppearance({ [KAZAN_APPEARANCE_KEY]: { layout: "classic", background: "x" } })
    ).toEqual({ layout: "classic", background: "plain" });
  });
});

describe("storedKazanAppearance", () => {
  test("returns only the valid values as written", () => {
    expect(
      storedKazanAppearance({ [KAZAN_APPEARANCE_KEY]: { layout: "classic", background: "nope" } })
    ).toEqual({ layout: "classic" });
  });
});
