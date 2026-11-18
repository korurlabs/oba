import { afterEach, beforeEach, describe, expect, it } from "bun:test";
import { createInstance, type i18n as I18n } from "i18next";

import en from "@/i18n/locales/en.json";
import nl from "@/i18n/locales/nl.json";
import tr from "@/i18n/locales/tr.json";
import {
  businessToday,
  createDates,
  currentWeekRange,
  dayInZone,
  type DateFormatter,
} from "@/lib/dates";

// A zone west of UTC, so a date-only value read as UTC would show the day before.
process.env.TZ = "America/New_York";

type Language = "en" | "nl" | "tr";
const LANGUAGES: Language[] = ["en", "nl", "tr"];
const TWELVE_HOUR_MARKERS = /\b(AM|PM|a\.m\.|p\.m\.|ÖÖ|ÖS)\b/i;

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

function local(month: number, day: number, hours: number, minutes: number): string {
  return new Date(2026, month - 1, day, hours, minutes).toISOString();
}

/** Every method of the formatter for one language, with the same inputs. */
function formatRow(language: Language): Record<string, string | null> {
  const dates: DateFormatter = datesFor(language);
  return {
    time: dates.time(local(11, 6, 21, 5)),
    timestampToday: dates.timestamp(local(11, 6, 21, 5)),
    shortDate: dates.shortDate("2026-10-28"),
    shortDateOtherYear: dates.shortDate("2027-01-03"),
    date: dates.date("2026-11-06"),
    longDate: dates.longDate("2026-11-04"),
    range: dates.range("2026-10-20", "2026-10-31"),
    instantDate: dates.instantDate("2026-11-04T22:30:00Z", "Europe/Istanbul"),
    instantShortDate: dates.instantShortDate("2026-11-04T22:30:00Z", "Europe/Istanbul"),
    justNow: dates.relativePast(local(11, 6, 9, 31)),
    minutesAgo: dates.relativePast(local(11, 6, 9, 29)),
    hoursAgo: dates.relativePast(local(11, 6, 7, 20)),
    daysAgo: dates.relativePast(local(10, 3, 9, 31)),
    age: dates.age(local(11, 6, 7, 31)),
    openMinutes: dates.openDuration(local(11, 6, 8, 49)),
    openHoursMinutes: dates.openDuration(local(11, 6, 7, 26)),
    openDays: dates.openDuration(local(11, 3, 9, 0)),
    daysLeft: dates.daysLeft(4),
    daysLeftOne: dates.daysLeft(1),
    ended: dates.daysLeft(-2),
    dueToday: dates.dueIn(0),
    dueIn: dates.dueIn(12),
    dueInOne: dates.dueIn(1),
    overdue: dates.dueIn(-2),
    overdueOne: dates.dueIn(-1),
    utcRollover: dates.utcRollover("2026-11-20T00:00:00Z"),
    utcSameDay: dates.utcRollover("2026-11-06T02:30:00Z"),
  };
}

const EXPECTED: Record<Language, Record<string, string>> = {
  en: {
    time: "21:05",
    timestampToday: "21:05",
    shortDate: "Oct 28",
    shortDateOtherYear: "Jan 3, 2027",
    date: "Nov 6, 2026",
    longDate: "November 4, 2026",
    range: "Oct 20 – Oct 31",
    instantDate: "Nov 5, 2026",
    instantShortDate: "Nov 5",
    justNow: "Just now",
    minutesAgo: "2 min ago",
    hoursAgo: "2 h ago",
    daysAgo: "34 days ago",
    age: "2 h",
    openMinutes: "42 min",
    openHoursMinutes: "2 h 5 min",
    openDays: "3 d",
    daysLeft: "4 days left",
    daysLeftOne: "1 day left",
    ended: "Ended 2 days ago",
    dueToday: "Due today",
    dueIn: "in 12 days",
    dueInOne: "in 1 day",
    overdue: "2 days overdue",
    overdueOne: "1 day overdue",
    utcRollover: "Nov 19, 19:00 (00:00 UTC)",
    utcSameDay: "Nov 5, 21:30 (02:30 UTC)",
  },
  nl: {
    time: "21:05",
    timestampToday: "21:05",
    shortDate: "28 okt.",
    shortDateOtherYear: "3 jan. 2027",
    date: "6 nov. 2026",
    longDate: "4 november 2026",
    range: "20 okt. – 31 okt.",
    instantDate: "5 nov. 2026",
    instantShortDate: "5 nov.",
    justNow: "Zojuist",
    minutesAgo: "2 min geleden",
    hoursAgo: "2 u geleden",
    daysAgo: "34 dagen geleden",
    age: "2 u",
    openMinutes: "42 min",
    openHoursMinutes: "2 u 5 min",
    openDays: "3 d",
    daysLeft: "Nog 4 dagen",
    daysLeftOne: "Nog 1 dag",
    ended: "2 dagen geleden geëindigd",
    dueToday: "Vandaag",
    dueIn: "over 12 dagen",
    dueInOne: "over 1 dag",
    overdue: "2 dagen te laat",
    overdueOne: "1 dag te laat",
    utcRollover: "19 nov., 19:00 (00:00 UTC)",
    utcSameDay: "5 nov., 21:30 (02:30 UTC)",
  },
  tr: {
    time: "21:05",
    timestampToday: "21:05",
    shortDate: "28 Eki",
    shortDateOtherYear: "3 Oca 2027",
    date: "6 Kas 2026",
    longDate: "4 Kasım 2026",
    range: "20 Eki – 31 Eki",
    instantDate: "5 Kas 2026",
    instantShortDate: "5 Kas",
    justNow: "Az önce",
    minutesAgo: "2 dk önce",
    hoursAgo: "2 sa önce",
    daysAgo: "34 gün önce",
    age: "2 sa",
    openMinutes: "42 dk",
    openHoursMinutes: "2 sa 5 dk",
    openDays: "3 g",
    daysLeft: "4 gün kaldı",
    daysLeftOne: "1 gün kaldı",
    ended: "2 gün önce bitti",
    dueToday: "Bugün",
    dueIn: "12 gün içinde",
    dueInOne: "1 gün içinde",
    overdue: "2 gün gecikti",
    overdueOne: "1 gün gecikti",
    utcRollover: "19 Kas, 19:00 (00:00 UTC)",
    utcSameDay: "5 Kas, 21:30 (02:30 UTC)",
  },
};

describe("every formatter row in every language", () => {
  for (const language of LANGUAGES) {
    it(`formats the ${language} row as the format table says`, () => {
      expect(formatRow(language)).toEqual(EXPECTED[language]);
    });
  }
});

describe("a device set to the 12-hour clock", () => {
  const original = Intl.DateTimeFormat.prototype.resolvedOptions;

  beforeEach(() => {
    Intl.DateTimeFormat.prototype.resolvedOptions = function resolvedOptions(
      this: Intl.DateTimeFormat
    ): Intl.ResolvedDateTimeFormatOptions {
      return { ...original.call(this), hour12: true, hourCycle: "h12" };
    };
  });

  afterEach(() => {
    Intl.DateTimeFormat.prototype.resolvedOptions = original;
  });

  it("still shows 24-hour times with no AM or PM in any language", () => {
    for (const language of LANGUAGES) {
      const dates: DateFormatter = datesFor(language);
      const afternoon: string = local(11, 6, 15, 52);
      const outputs: (string | null)[] = [
        dates.time(afternoon),
        dates.timestamp(afternoon),
        dates.timestamp(local(11, 5, 15, 52)),
        dates.timestamp(local(10, 4, 15, 52)),
        dates.utcRollover("2026-11-20T15:00:00Z"),
      ];
      for (const output of outputs) {
        expect(output).not.toBeNull();
        expect(output as string).not.toMatch(TWELVE_HOUR_MARKERS);
      }
      expect(dates.time(afternoon)).toBe("15:52");
    }
  });
});

describe("the Istanbul business day", () => {
  it("rolls over at midnight Istanbul time, not at midnight UTC", () => {
    expect(dayInZone("2026-11-04T20:59:59Z", "Europe/Istanbul")).toBe("2026-11-04");
    expect(dayInZone("2026-11-04T21:00:00Z", "Europe/Istanbul")).toBe("2026-11-05");
    expect(businessToday(new Date("2026-11-04T20:59:59Z"))).toBe("2026-11-04");
    expect(businessToday(new Date("2026-11-04T21:00:00Z"))).toBe("2026-11-05");
  });

  it("shows the Istanbul day in every language", () => {
    expect(datesFor("nl").instantShortDate("2026-11-04T21:30:00Z", "Europe/Istanbul")).toBe(
      "5 nov."
    );
    expect(datesFor("tr").instantShortDate("2026-11-04T20:30:00Z", "Europe/Istanbul")).toBe(
      "4 Kas"
    );
  });
});

describe("the week starts on Monday", () => {
  it("runs Monday to Sunday for every day of the week", () => {
    for (let day = 2; day <= 8; day += 1) {
      expect(currentWeekRange(new Date(2026, 10, day, 12, 0))).toEqual({
        start: "2026-11-02",
        end: "2026-11-08",
      });
    }
    expect(currentWeekRange(new Date(2026, 10, 9, 0, 5))).toEqual({
      start: "2026-11-09",
      end: "2026-11-15",
    });
  });
});
