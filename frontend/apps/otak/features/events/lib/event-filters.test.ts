import { describe, expect, test } from "bun:test";
import { loadEventFilterParams, toEventQuery } from "./event-filters";

const TODAY = "2026-11-14";

describe("toEventQuery", () => {
  test("an empty URL lists the upcoming events of today, first page, 25 rows", () => {
    expect(toEventQuery(loadEventFilterParams(""), TODAY)).toEqual({
      period: "UPCOMING",
      today: TODAY,
      page: 1,
      amount: 25,
    });
  });

  test("the past tab asks for PAST with today", () => {
    expect(toEventQuery(loadEventFilterParams("?tab=past&page=2"), TODAY)).toEqual({
      period: "PAST",
      today: TODAY,
      page: 2,
      amount: 25,
    });
  });

  test("an unknown tab falls back to upcoming", () => {
    expect(toEventQuery(loadEventFilterParams("?tab=later"), TODAY).period).toBe("UPCOMING");
  });

  test("the search is trimmed, a blank one left out and a long one cut to 200", () => {
    expect(toEventQuery(loadEventFilterParams("?search=%20igef%20"), TODAY).search).toBe("igef");
    expect(toEventQuery(loadEventFilterParams("?search=%20%20"), TODAY)).not.toHaveProperty(
      "search"
    );
    expect(toEventQuery(loadEventFilterParams(`?search=${"a".repeat(250)}`), TODAY).search).toBe(
      "a".repeat(200)
    );
  });

  test("a page below 1 is raised to 1", () => {
    expect(toEventQuery({ tab: "upcoming", search: null, page: 0 }, TODAY).page).toBe(1);
  });
});
