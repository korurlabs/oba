import { describe, expect, test } from "bun:test";
import { loadOutreachFilterParams, toOutreachQuery } from "./outreach-filters";

describe("toOutreachQuery", () => {
  test("an empty URL lists the first page, 25 rows, of every status", () => {
    expect(toOutreachQuery(loadOutreachFilterParams(""))).toEqual({ page: 1, amount: 25 });
  });

  test("the status and the page are read from the URL", () => {
    expect(toOutreachQuery(loadOutreachFilterParams("?status=FAILED&page=3"))).toEqual({
      status: "FAILED",
      page: 3,
      amount: 25,
    });
  });

  test("an unknown status is left out", () => {
    expect(toOutreachQuery(loadOutreachFilterParams("?status=SENDING"))).toEqual({
      page: 1,
      amount: 25,
    });
  });

  test("a page below 1 is raised to 1", () => {
    expect(toOutreachQuery({ status: null, campaign: null, page: 0 }).page).toBe(1);
    expect(toOutreachQuery({ status: null, campaign: null, page: -4 }).page).toBe(1);
  });

  test("the campaign in the URL keeps the outreaches of that campaign", () => {
    expect(toOutreachQuery(loadOutreachFilterParams("?campaign=c-1&status=SENT"))).toEqual({
      status: "SENT",
      campaignId: "c-1",
      page: 1,
      amount: 25,
    });
  });

  test("an empty campaign is left out", () => {
    expect(toOutreachQuery(loadOutreachFilterParams("?campaign="))).toEqual({
      page: 1,
      amount: 25,
    });
  });
});
