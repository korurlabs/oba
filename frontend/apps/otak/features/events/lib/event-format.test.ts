import { describe, expect, test } from "bun:test";
import { formatEventDates, formatEventPlace } from "./event-format";

describe("formatEventDates", () => {
  test("a one-day event shows its start only", () => {
    expect(formatEventDates("2026-11-10", null, "en")).toBe("Nov 10, 2026");
    expect(formatEventDates("2026-11-10", "2026-11-10", "en")).toBe("Nov 10, 2026");
  });

  test("a longer event shows start and end", () => {
    expect(formatEventDates("2026-11-10", "2026-11-13", "en")).toBe("Nov 10, 2026 – Nov 13, 2026");
  });
});

describe("formatEventPlace", () => {
  const name = (code: string) => `country:${code}`;

  test("city and country are joined", () => {
    expect(formatEventPlace("Ankara", "TR", name)).toBe("Ankara, country:TR");
  });

  test("only the parts that are set are shown, none gives null", () => {
    expect(formatEventPlace(null, "TR", name)).toBe("country:TR");
    expect(formatEventPlace("Ankara", null, name)).toBe("Ankara");
    expect(formatEventPlace(null, null, name)).toBeNull();
  });
});
