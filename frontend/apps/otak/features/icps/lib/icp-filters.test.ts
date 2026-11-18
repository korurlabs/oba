import { describe, expect, test } from "bun:test";
import { loadIcpFilterParams, toIcpQuery } from "./icp-filters";

describe("toIcpQuery", () => {
  test("an empty URL lists the first page, 25 rows, without a search", () => {
    expect(toIcpQuery(loadIcpFilterParams(""))).toEqual({ page: 1, amount: 25 });
  });

  test("the search is trimmed and the page read from the URL", () => {
    expect(toIcpQuery(loadIcpFilterParams("?search=%20textile%20&page=3"))).toEqual({
      search: "textile",
      page: 3,
      amount: 25,
    });
  });

  test("a blank search is left out", () => {
    expect(toIcpQuery(loadIcpFilterParams("?search=%20%20"))).toEqual({ page: 1, amount: 25 });
  });

  test("a search longer than 100 characters is cut to 100", () => {
    const query = toIcpQuery(loadIcpFilterParams(`?search=${"a".repeat(150)}`));
    expect(query.search).toBe("a".repeat(100));
  });

  test("a page below 1 is raised to 1", () => {
    expect(toIcpQuery({ search: null, page: 0 }).page).toBe(1);
    expect(toIcpQuery({ search: null, page: -4 }).page).toBe(1);
  });
});
