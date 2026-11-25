import { describe, expect, test } from "bun:test";
import {
  hasActiveRelationFilter,
  loadRelationFilterParams,
  toRelationQuery,
} from "./relation-filters";

describe("toRelationQuery", () => {
  test("an empty URL lists the first page of leads, 25 rows", () => {
    expect(toRelationQuery(loadRelationFilterParams(""))).toEqual({
      type: "LEAD",
      page: 1,
      amount: 25,
    });
  });

  test("the stage filter applies to leads", () => {
    const filters = loadRelationFilterParams(
      "?stage=LOST,PARKED&owner=u-1&search=%20acme%20&page=3"
    );
    expect(toRelationQuery(filters)).toEqual({
      type: "LEAD",
      stage: ["LOST", "PARKED"],
      ownerUserId: "u-1",
      search: "acme",
      page: 3,
      amount: 25,
    });
  });

  test("the stage filter applies to customers with the customer stages", () => {
    const filters = loadRelationFilterParams("?tab=customers&stage=CLOSED,DIRECT");
    expect(toRelationQuery(filters)).toEqual({
      type: "CUSTOMER",
      stage: ["CLOSED", "DIRECT"],
      page: 1,
      amount: 25,
    });
    expect(hasActiveRelationFilter(filters)).toBe(true);
  });

  test("a stage of another tab is not sent and does not count as a filter", () => {
    const customers = loadRelationFilterParams("?tab=customers&stage=NEW");
    expect(toRelationQuery(customers)).toEqual({ type: "CUSTOMER", page: 1, amount: 25 });
    expect(hasActiveRelationFilter(customers)).toBe(false);
    const leads = loadRelationFilterParams("?stage=CLOSED,WARM");
    expect(toRelationQuery(leads).stage).toEqual(["WARM"]);
  });

  test("the event filter is sent as eventId on every tab and counts as a filter", () => {
    const filters = loadRelationFilterParams("?tab=suppliers&event=event-1");
    expect(toRelationQuery(filters)).toEqual({
      type: "SUPPLIER",
      eventId: "event-1",
      page: 1,
      amount: 25,
    });
    expect(hasActiveRelationFilter(filters)).toBe(true);
    expect(hasActiveRelationFilter(loadRelationFilterParams("?tab=suppliers"))).toBe(false);
  });

  test("the campaign filter is sent as campaignId and counts as a filter", () => {
    const filters = loadRelationFilterParams("?tab=leads&campaign=campaign-1");
    expect(toRelationQuery(filters)).toMatchObject({ type: "LEAD", campaignId: "campaign-1" });
    expect(hasActiveRelationFilter(filters)).toBe(true);
    expect(toRelationQuery(loadRelationFilterParams("?tab=leads")).campaignId).toBeUndefined();
  });

  test("suppliers send no stage", () => {
    const filters = loadRelationFilterParams("?tab=suppliers&stage=DIRECT");
    expect(toRelationQuery(filters)).toEqual({ type: "SUPPLIER", page: 1, amount: 25 });
  });

  test("an unknown tab or stage falls back to the defaults", () => {
    const filters = loadRelationFilterParams("?tab=partners&stage=WON");
    expect(toRelationQuery(filters).type).toBe("LEAD");
    expect(toRelationQuery(filters).stage).toBeUndefined();
  });
});
