import { describe, expect, test } from "bun:test";
import {
  hasActiveRelationFilter,
  loadRelationFilterParams,
  toRelationQuery,
} from "./relation-filters";

describe("toRelationQuery", () => {
  test("an empty URL lists the first page of every type, without inactive leads", () => {
    const filters = loadRelationFilterParams("");
    expect(toRelationQuery(filters)).toEqual({ hideInactiveLeads: true, page: 1, amount: 25 });
    expect(hasActiveRelationFilter(filters)).toBe(false);
  });

  test("the type filter sends every chosen type and counts as a filter", () => {
    const filters = loadRelationFilterParams("?type=LEAD,SUPPLIER");
    expect(toRelationQuery(filters)).toEqual({
      type: ["LEAD", "SUPPLIER"],
      hideInactiveLeads: true,
      page: 1,
      amount: 25,
    });
    expect(hasActiveRelationFilter(filters)).toBe(true);
  });

  test("stages are sent without a type and stop hiding inactive leads", () => {
    const filters = loadRelationFilterParams(
      "?stage=LOST,DIRECT&owner=u-1&search=%20acme%20&page=3"
    );
    expect(toRelationQuery(filters)).toEqual({
      stage: ["LOST", "DIRECT"],
      ownerUserId: "u-1",
      search: "acme",
      page: 3,
      amount: 25,
    });
    expect(hasActiveRelationFilter(filters)).toBe(true);
  });

  test("types and stages are sent together as chosen", () => {
    const filters = loadRelationFilterParams("?type=CUSTOMER&stage=CLOSED,NEW");
    expect(toRelationQuery(filters)).toEqual({
      type: ["CUSTOMER"],
      stage: ["CLOSED", "NEW"],
      page: 1,
      amount: 25,
    });
  });

  test("the event filter is sent as eventId and counts as a filter", () => {
    const filters = loadRelationFilterParams("?event=event-1");
    expect(toRelationQuery(filters)).toEqual({
      eventId: "event-1",
      hideInactiveLeads: true,
      page: 1,
      amount: 25,
    });
    expect(hasActiveRelationFilter(filters)).toBe(true);
  });

  test("the campaign filter is sent as campaignId and counts as a filter", () => {
    const filters = loadRelationFilterParams("?campaign=campaign-1");
    expect(toRelationQuery(filters)).toMatchObject({ campaignId: "campaign-1" });
    expect(hasActiveRelationFilter(filters)).toBe(true);
    expect(toRelationQuery(loadRelationFilterParams("")).campaignId).toBeUndefined();
  });

  test("an unknown type or stage is not sent", () => {
    const filters = loadRelationFilterParams("?type=PARTNER&stage=WON");
    expect(toRelationQuery(filters).type).toBeUndefined();
    expect(toRelationQuery(filters).stage).toBeUndefined();
    expect(toRelationQuery(filters).hideInactiveLeads).toBe(true);
  });

  test("the removed tab param is ignored", () => {
    const filters = loadRelationFilterParams("?tab=suppliers");
    expect(toRelationQuery(filters)).toEqual({ hideInactiveLeads: true, page: 1, amount: 25 });
    expect(hasActiveRelationFilter(filters)).toBe(false);
  });
});
