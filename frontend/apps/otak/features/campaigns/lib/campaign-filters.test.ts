import { describe, expect, test } from "bun:test";
import {
  hasActiveCampaignFilter,
  isCampaignTab,
  loadCampaignFilterParams,
  toCampaignQuery,
} from "./campaign-filters";

describe("toCampaignQuery", () => {
  test("an empty URL lists every campaign, with no paging", () => {
    expect(toCampaignQuery(loadCampaignFilterParams(""))).toEqual({});
  });

  test("each status tab asks for its status", () => {
    expect(toCampaignQuery(loadCampaignFilterParams("?tab=draft"))).toEqual({ status: "DRAFT" });
    expect(toCampaignQuery(loadCampaignFilterParams("?tab=scheduled"))).toEqual({
      status: "SCHEDULED",
    });
    expect(toCampaignQuery(loadCampaignFilterParams("?tab=published"))).toEqual({
      status: "PUBLISHED",
    });
    expect(toCampaignQuery(loadCampaignFilterParams("?tab=archived"))).toEqual({
      status: "ARCHIVED",
    });
  });

  test("the channel and a trimmed search are read from the URL", () => {
    expect(
      toCampaignQuery(loadCampaignFilterParams("?tab=all&channel=X&search=%20launch%20"))
    ).toEqual({ channel: "X", search: "launch" });
  });

  test("an unknown tab or channel is ignored", () => {
    expect(toCampaignQuery(loadCampaignFilterParams("?tab=sent&channel=FACEBOOK"))).toEqual({});
  });

  test("a blank search is left out and a long one is cut to 200 characters", () => {
    expect(toCampaignQuery(loadCampaignFilterParams("?search=%20%20"))).toEqual({});
    const long = "s".repeat(250);
    expect(toCampaignQuery(loadCampaignFilterParams(`?search=${long}`)).search).toHaveLength(200);
  });
});

describe("hasActiveCampaignFilter", () => {
  test("the tab alone is not a filter; a channel or a search is", () => {
    expect(hasActiveCampaignFilter(loadCampaignFilterParams("?tab=draft"))).toBe(false);
    expect(hasActiveCampaignFilter(loadCampaignFilterParams("?channel=NEWSLETTER"))).toBe(true);
    expect(hasActiveCampaignFilter(loadCampaignFilterParams("?search=launch"))).toBe(true);
    expect(hasActiveCampaignFilter(loadCampaignFilterParams("?search=%20"))).toBe(false);
  });
});

test("isCampaignTab knows the five tabs", () => {
  expect(["all", "draft", "scheduled", "published", "archived"].every(isCampaignTab)).toBe(true);
  expect(isCampaignTab("sent")).toBe(false);
});
