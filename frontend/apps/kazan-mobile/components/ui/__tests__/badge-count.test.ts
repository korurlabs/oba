import { describe, expect, it } from "bun:test";

import { formatBadgeCount, MAX_BADGE_COUNT } from "@/components/ui/badge-count";

describe("formatBadgeCount", () => {
  it("shows nothing for a missing, zero, negative or non-finite count", () => {
    expect(formatBadgeCount(undefined)).toBeNull();
    expect(formatBadgeCount(null)).toBeNull();
    expect(formatBadgeCount(0)).toBeNull();
    expect(formatBadgeCount(-3)).toBeNull();
    expect(formatBadgeCount(Number.NaN)).toBeNull();
    expect(formatBadgeCount(Number.POSITIVE_INFINITY)).toBeNull();
  });

  it("spells out counts up to the maximum", () => {
    expect(formatBadgeCount(1)).toBe("1");
    expect(formatBadgeCount(MAX_BADGE_COUNT)).toBe("99");
  });

  it("caps larger counts at 99+", () => {
    expect(formatBadgeCount(100)).toBe("99+");
    expect(formatBadgeCount(12_345)).toBe("99+");
  });
});
