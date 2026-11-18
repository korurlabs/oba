import { describe, expect, it } from "bun:test";
import { createInstance, type i18n as I18n } from "i18next";

import en from "@/i18n/locales/en.json";
import nl from "@/i18n/locales/nl.json";
import tr from "@/i18n/locales/tr.json";
import {
  businessToday,
  createDates,
  currentWeekRange,
  dayInZone,
  daysBetween,
  type DateFormatter,
} from "@/lib/dates";

// A zone west of UTC with summer time: a date-only value read as UTC midnight
// would show the day before here, and 12-hour device settings are common. Bun
// applies a TZ change at once, and every date here is built after this line.
process.env.TZ = "America/New_York";

type Language = "en" | "nl" | "tr";
const LANGUAGES: Language[] = ["en", "nl", "tr"];
const TWELVE_HOUR_MARKERS = /\b(AM|PM|a\.m\.|p\.m\.|ÖÖ|ÖS)\b/i;

// Initialised synchronously: describe blocks build their formatters at collection time.
const instance: I18n = createInstance();
void instance.init({
  resources: { en: { translation: en }, nl: { translation: nl }, tr: { translation: tr } },
  lng: "en",
  fallbackLng: "en",
  interpolation: { escapeValue: false },
  compatibilityJSON: "v4",
  initAsync: false,
});

// Friday 6 November 2026, 09:31 local time.
const NOW = new Date(2026, 10, 6, 9, 31);

function datesFor(language: Language, now: Date = NOW): DateFormatter {
  const t = instance.getFixedT(language);
  return createDates({
    language,
    t: (key, options) => t(key, options as never) as unknown as string,
    now: () => now,
  });
}

/** An ISO instant of a local wall-clock time, as the server would send it. */
function localIso(
  year: number,
  month: number,
  day: number,
  hours: number,
  minutes: number
): string {
  return new Date(year, month - 1, day, hours, minutes).toISOString();
}

describe("times are on the 24-hour clock", () => {
  it("formats every time row in every language without AM or PM", () => {
    const instants: string[] = [
      localIso(2026, 11, 6, 21, 5),
      localIso(2026, 11, 5, 22, 12),
      localIso(2026, 11, 2, 13, 0),
      localIso(2026, 10, 4, 15, 52),
      localIso(2025, 3, 1, 23, 59),
    ];
    for (const language of LANGUAGES) {
      const dates: DateFormatter = datesFor(language);
      for (const value of instants) {
        const outputs: (string | null)[] = [
          dates.time(value),
          dates.timestamp(value),
          dates.utcRollover(value),
        ];
        for (const output of outputs) {
          expect(output).not.toBeNull();
          expect(output as string).not.toMatch(TWELVE_HOUR_MARKERS);
        }
      }
      expect(dates.time(localIso(2026, 11, 6, 21, 5))).toBe("21:05");
    }
  });
});

describe("timestamp", () => {
  const dates: DateFormatter = datesFor("en");

  it("shows the time alone today", () => {
    expect(dates.timestamp(localIso(2026, 11, 6, 0, 5))).toBe("00:05");
  });

  it("shows Yesterday and the weekday within six days", () => {
    expect(dates.timestamp(localIso(2026, 11, 5, 10, 12))).toBe("Yesterday 10:12");
    expect(dates.timestamp(localIso(2026, 11, 2, 16, 40))).toBe("Monday 16:40");
    expect(dates.timestamp(localIso(2026, 10, 31, 8, 0))).toBe("Saturday 08:00");
  });

  it("shows the date once older, with the year only in another year", () => {
    expect(dates.timestamp(localIso(2026, 10, 30, 9, 52))).toBe("Oct 30, 09:52");
    expect(dates.timestamp(localIso(2025, 11, 4, 9, 52))).toBe("Nov 4, 2025, 09:52");
  });

  it("translates the relative day and weekday", () => {
    expect(datesFor("nl").timestamp(localIso(2026, 11, 5, 10, 12))).toBe("Gisteren 10:12");
    expect(datesFor("tr").timestamp(localIso(2026, 11, 5, 10, 12))).toBe("Dün 10:12");
    expect(datesFor("nl").timestamp(localIso(2026, 11, 2, 16, 40))).toBe("maandag 16:40");
    expect(datesFor("tr").timestamp(localIso(2026, 11, 2, 16, 40))).toBe("Pazartesi 16:40");
  });
});

describe("date-only values", () => {
  it("never shift west of UTC", () => {
    const dates: DateFormatter = datesFor("en");
    expect(dates.shortDate("2026-10-28")).toBe("Oct 28");
    expect(dates.date("2026-11-06")).toBe("Nov 6, 2026");
    expect(dates.longDate("2026-11-04")).toBe("November 4, 2026");
  });

  it("adds the year in another year and joins ranges with an en dash", () => {
    const dates: DateFormatter = datesFor("en");
    expect(dates.shortDate("2027-01-03")).toBe("Jan 3, 2027");
    expect(dates.range("2026-10-20", "2026-10-31")).toBe("Oct 20 – Oct 31");
  });

  it("puts the day first in Dutch and Turkish", () => {
    expect(datesFor("nl").date("2026-11-06")).toBe("6 nov. 2026");
    expect(datesFor("tr").date("2026-11-06")).toBe("6 Kas 2026");
    expect(datesFor("tr").longDate("2026-11-04")).toBe("4 Kasım 2026");
  });

  it("returns null for a value that is not a day", () => {
    expect(datesFor("en").shortDate("2026-02-30")).toBeNull();
  });
});

describe("relative values", () => {
  const dates: DateFormatter = datesFor("en");

  it("counts time ago", () => {
    expect(dates.relativePast(localIso(2026, 11, 6, 9, 31))).toBe("Just now");
    expect(dates.relativePast(localIso(2026, 11, 6, 9, 29))).toBe("2 min ago");
    expect(dates.relativePast(localIso(2026, 11, 6, 7, 20))).toBe("2 h ago");
    expect(dates.relativePast(localIso(2026, 9, 3, 9, 31))).toBe("64 days ago");
  });

  it("gives compact ages and open durations", () => {
    expect(dates.age(localIso(2026, 11, 6, 7, 31))).toBe("2 h");
    expect(dates.openDuration(localIso(2026, 11, 6, 8, 49))).toBe("42 min");
    expect(dates.openDuration(localIso(2026, 11, 6, 7, 26))).toBe("2 h 5 min");
    expect(dates.openDuration(localIso(2026, 11, 6, 7, 31))).toBe("2 h");
    expect(dates.openDuration(localIso(2026, 11, 3, 9, 0))).toBe("3 d");
  });

  it("phrases day counters", () => {
    expect(dates.daysLeft(4)).toBe("4 days left");
    expect(dates.daysLeft(1)).toBe("1 day left");
    expect(dates.daysLeft(-2)).toBe("Ended 2 days ago");
    expect(dates.dueIn(0)).toBe("Due today");
    expect(dates.dueIn(12)).toBe("in 12 days");
    expect(dates.dueIn(-2)).toBe("2 days overdue");
    expect(datesFor("tr").dueIn(-2)).toBe("2 gün gecikti");
  });
});

describe("zones", () => {
  it("shows a UTC rollover in local time with UTC in parentheses", () => {
    expect(datesFor("en").utcRollover("2026-11-20T00:00:00Z")).toBe("Nov 19, 19:00 (00:00 UTC)");
  });

  it("reads the Istanbul day of an instant", () => {
    // 22:30 UTC on 4 November is already 5 November in Istanbul (UTC+3).
    expect(dayInZone("2026-11-04T22:30:00Z", "Europe/Istanbul")).toBe("2026-11-05");
    expect(businessToday(new Date("2026-11-04T22:30:00Z"))).toBe("2026-11-05");
    expect(datesFor("en").instantShortDate("2026-11-04T22:30:00Z", "Europe/Istanbul")).toBe(
      "Nov 5"
    );
  });

  it("counts calendar days and starts the week on Monday", () => {
    expect(daysBetween("2026-11-06", "2026-11-10")).toBe(4);
    expect(daysBetween("2026-11-06", "2026-11-04")).toBe(-2);
    expect(currentWeekRange(NOW)).toEqual({ start: "2026-11-02", end: "2026-11-08" });
    expect(currentWeekRange(new Date(2026, 10, 8, 23, 0))).toEqual({
      start: "2026-11-02",
      end: "2026-11-08",
    });
  });
});
