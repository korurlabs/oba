import { describe, expect, test } from "bun:test";
import {
  hasActiveOutreachFilter,
  loadOutreachFilterParams,
  toOutreachQuery,
  type OutreachFilterParams,
} from "./outreach-filters";

const NO_FILTERS: OutreachFilterParams = {
  search: null,
  channel: null,
  status: null,
  sender: null,
  campaign: null,
  page: 1,
};

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
    expect(toOutreachQuery({ ...NO_FILTERS, page: 0 }).page).toBe(1);
    expect(toOutreachQuery({ ...NO_FILTERS, page: -4 }).page).toBe(1);
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

  test("the channel and the sender go out as channel and mailSenderId", () => {
    expect(toOutreachQuery(loadOutreachFilterParams("?channel=WHATSAPP&sender=s-1"))).toEqual({
      channel: "WHATSAPP",
      mailSenderId: "s-1",
      page: 1,
      amount: 25,
    });
  });

  test("an unknown channel is left out", () => {
    expect(toOutreachQuery(loadOutreachFilterParams("?channel=SMS"))).toEqual({
      page: 1,
      amount: 25,
    });
  });

  test("the search is trimmed, and a blank one is left out", () => {
    expect(toOutreachQuery(loadOutreachFilterParams("?search=%20acme%20")).search).toBe("acme");
    expect(toOutreachQuery(loadOutreachFilterParams("?search=%20%20"))).toEqual({
      page: 1,
      amount: 25,
    });
  });

  test("a search longer than the server takes is cut to 200 characters", () => {
    expect(toOutreachQuery({ ...NO_FILTERS, search: "a".repeat(250) }).search).toHaveLength(200);
  });
});

describe("hasActiveOutreachFilter", () => {
  test("the page alone is no filter", () => {
    expect(hasActiveOutreachFilter({ ...NO_FILTERS, page: 4 })).toBe(false);
  });

  test("a blank search is no filter", () => {
    expect(hasActiveOutreachFilter({ ...NO_FILTERS, search: "  " })).toBe(false);
  });

  test("each filter counts", () => {
    expect(hasActiveOutreachFilter({ ...NO_FILTERS, search: "acme" })).toBe(true);
    expect(hasActiveOutreachFilter({ ...NO_FILTERS, channel: "CALL" })).toBe(true);
    expect(hasActiveOutreachFilter({ ...NO_FILTERS, status: "DRAFT" })).toBe(true);
    expect(hasActiveOutreachFilter({ ...NO_FILTERS, sender: "s-1" })).toBe(true);
    expect(hasActiveOutreachFilter({ ...NO_FILTERS, campaign: "c-1" })).toBe(true);
  });
});
