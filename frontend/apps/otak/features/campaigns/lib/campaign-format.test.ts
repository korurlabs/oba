import { describe, expect, test } from "bun:test";
import { formatCampaignDates } from "./campaign-format";

const t = (key: string, values: Record<string, string>) => `${key}:${values.date}`;

describe("formatCampaignDates", () => {
  test("a range shows start and end, one day shows once", () => {
    expect(formatCampaignDates("2026-12-01", "2026-12-31", "en", t)).toBe(
      "Dec 1, 2026 – Dec 31, 2026"
    );
    expect(formatCampaignDates("2026-12-01", "2026-12-01", "en", t)).toBe("Dec 1, 2026");
  });

  test("one side only says from or until, none gives null", () => {
    expect(formatCampaignDates("2026-12-01", null, "en", t)).toBe("datesFrom:Dec 1, 2026");
    expect(formatCampaignDates(null, "2026-12-31", "en", t)).toBe("datesUntil:Dec 31, 2026");
    expect(formatCampaignDates(null, null, "en", t)).toBeNull();
  });
});
