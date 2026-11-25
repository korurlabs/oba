import { describe, expect, test } from "bun:test";
import {
  hasActiveCampaignFilter,
  loadCampaignFilterParams,
  toCampaignQuery,
} from "./campaign-filters";

describe("toCampaignQuery", () => {
  test("an empty URL lists every campaign, with no paging", () => {
    expect(toCampaignQuery(loadCampaignFilterParams(""))).toEqual({});
  });

  test("a trimmed search is read from the URL", () => {
    expect(toCampaignQuery(loadCampaignFilterParams("?search=%20wacht%20"))).toEqual({
      search: "wacht",
    });
  });

  test("a blank search is left out and a long one is kept whole", () => {
    expect(toCampaignQuery(loadCampaignFilterParams("?search=%20%20"))).toEqual({});
    const long = "s".repeat(250);
    expect(toCampaignQuery(loadCampaignFilterParams(`?search=${long}`)).search).toHaveLength(250);
  });
});

test("hasActiveCampaignFilter is true for a search that is not blank", () => {
  expect(hasActiveCampaignFilter(loadCampaignFilterParams(""))).toBe(false);
  expect(hasActiveCampaignFilter(loadCampaignFilterParams("?search=%20"))).toBe(false);
  expect(hasActiveCampaignFilter(loadCampaignFilterParams("?search=wacht"))).toBe(true);
});
