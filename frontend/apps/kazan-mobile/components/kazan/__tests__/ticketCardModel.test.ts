import { describe, expect, it } from "bun:test";
import { createInstance, type i18n as I18n } from "i18next";

import {
  cardFootItems,
  cardHeadItems,
  cardProgress,
  createdByLine,
  createdWhen,
  creatorName,
  otherBoardPrefix,
} from "@/components/kazan/ticketCardModel";
import en from "@/i18n/locales/en.json";
import nl from "@/i18n/locales/nl.json";
import tr from "@/i18n/locales/tr.json";
import { createDates, type DateFormatter, type DateTranslate } from "@/lib/dates";

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

// Monday 9 November 2026, 15:20 local time; every instant below is built in
// the local zone, so the expectations hold in any zone the tests run in.
const NOW = new Date(2026, 10, 9, 15, 20);

function setup(language: "en" | "nl" | "tr"): { dates: DateFormatter; t: DateTranslate } {
  const fixed = instance.getFixedT(language);
  const t: DateTranslate = (key, options) => fixed(key, options as never) as unknown as string;
  return { dates: createDates({ language, t, now: () => NOW }), t };
}

function localInstant(day: number, hours: number, minutes: number): string {
  return new Date(2026, 10, day, hours, minutes).toISOString();
}

describe("cardHeadItems", () => {
  it("reads type, key, then priority and rank at the trailing edge", () => {
    expect(cardHeadItems({ priority: "HIGH", rank: "M" })).toEqual([
      "type",
      "key",
      "spacer",
      "priority",
      "rank",
    ]);
  });

  it("keeps an incident's P1 and rank at the trailing edge", () => {
    expect(cardHeadItems({ priority: "P1", rank: "XS" })).toEqual([
      "type",
      "key",
      "spacer",
      "priority",
      "rank",
    ]);
  });

  it("leaves out an unknown priority and a missing rank", () => {
    expect(cardHeadItems({ priority: "URGENT", rank: null })).toEqual(["type", "key", "spacer"]);
    expect(cardHeadItems({ priority: null, rank: "L" })).toEqual(["type", "key", "spacer", "rank"]);
  });
});

describe("cardFootItems", () => {
  it("leads with the status, then the time slot and progress, assignees last", () => {
    expect(
      cardFootItems({ showStatus: true, timing: true, progress: true, assignees: true })
    ).toEqual(["status", "timing", "progress", "spacer", "assignees"]);
  });

  it("keeps the status when the ticket has nothing else to show", () => {
    expect(
      cardFootItems({ showStatus: true, timing: false, progress: false, assignees: false })
    ).toEqual(["status"]);
  });

  it("leaves the status out only when a caller hides it", () => {
    expect(
      cardFootItems({ showStatus: false, timing: true, progress: false, assignees: false })
    ).toEqual(["timing"]);
    expect(
      cardFootItems({ showStatus: false, timing: false, progress: false, assignees: false })
    ).toEqual([]);
  });
});

describe("cardProgress", () => {
  it("counts the done sub-tickets", () => {
    expect(
      cardProgress([
        { id: "a", status: "DONE" },
        { id: "b", status: "IN_PROGRESS" },
        { id: "c", status: "DONE" },
      ])
    ).toEqual({ done: 2, total: 3 });
  });

  it("is null without sub-tickets", () => {
    expect(cardProgress([])).toBeNull();
    expect(cardProgress(null)).toBeNull();
    expect(cardProgress(undefined)).toBeNull();
  });
});

describe("otherBoardPrefix", () => {
  const board = { id: "child", name: "API", prefix: "API", slug: "api" };

  it("names a child board's ticket by its board prefix", () => {
    expect(otherBoardPrefix({ board }, "parent")).toBe("API");
  });

  it("is null on the ticket's own board, without a board id or without a board", () => {
    expect(otherBoardPrefix({ board }, "child")).toBeNull();
    expect(otherBoardPrefix({ board }, undefined)).toBeNull();
    expect(otherBoardPrefix({ board: null }, "parent")).toBeNull();
  });
});

describe("createdWhen", () => {
  const { dates, t } = setup("en");

  it("spells out today with the 24-hour time", () => {
    expect(createdWhen(localInstant(9, 9, 40), dates, t)).toBe("Today 09:40");
  });

  it("names yesterday, then the weekday, then the date", () => {
    expect(createdWhen(localInstant(8, 22, 5), dates, t)).toBe("Yesterday 22:05");
    expect(createdWhen(localInstant(5, 13, 0), dates, t)).toBe("Thursday 13:00");
    expect(createdWhen(localInstant(1, 8, 15), dates, t)).toBe("Nov 1, 08:15");
  });

  it("writes today in each app language", () => {
    const dutch = setup("nl");
    const turkish = setup("tr");
    expect(createdWhen(localInstant(9, 9, 40), dutch.dates, dutch.t)).toBe("Vandaag 09:40");
    expect(createdWhen(localInstant(9, 9, 40), turkish.dates, turkish.t)).toBe("Bugün 09:40");
  });
});

describe("creatorName and createdByLine", () => {
  const { dates, t } = setup("en");
  const createdAt: string = localInstant(9, 9, 40);

  it("names the member who created the ticket", () => {
    const ticket = {
      createdAt,
      createdBy: { userId: "u-1", email: "ayse@example.com", firstName: "Ayşe", lastName: "Kaya" },
      createdBySystemActor: null,
    };
    expect(creatorName(ticket)).toBe("Ayşe Kaya");
    expect(createdByLine(ticket, dates, t)).toBe("Created by Ayşe Kaya · Today 09:40");
  });

  it("falls back to the email, then to the integration that created it", () => {
    expect(creatorName({ createdBy: { userId: "u-1", email: "ops@example.com" } })).toBe(
      "ops@example.com"
    );
    expect(
      createdByLine(
        {
          createdAt,
          createdBy: null,
          createdBySystemActor: { id: "s-1", name: "Alertmanager", type: "ALERT_ROUTE" },
        },
        dates,
        t
      )
    ).toBe("Created by Alertmanager · Today 09:40");
  });

  it("shows no line when the list does not name a creator", () => {
    expect(creatorName({})).toBeNull();
    expect(
      createdByLine({ createdAt, createdBy: null, createdBySystemActor: null }, dates, t)
    ).toBeNull();
  });

  it("keeps the name when the time cannot be read", () => {
    const originalWarn = console.warn;
    console.warn = () => undefined;
    try {
      expect(
        createdByLine(
          { createdAt: "not a time", createdBy: { userId: "u-1", firstName: "Ayşe" } },
          dates,
          t
        )
      ).toBe("Created by Ayşe");
    } finally {
      console.warn = originalWarn;
    }
  });
});
