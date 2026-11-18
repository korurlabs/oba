import { describe, expect, test } from "bun:test";
import {
  hasActiveMailTemplateFilter,
  loadMailTemplateFilterParams,
  toMailTemplateQuery,
} from "./mail-template-filters";

describe("toMailTemplateQuery", () => {
  test("an empty URL lists the first page, 25 rows, without filters", () => {
    expect(toMailTemplateQuery(loadMailTemplateFilterParams(""))).toEqual({ page: 1, amount: 25 });
  });

  test("the search, language and page are read from the URL; a type is ignored", () => {
    expect(
      toMailTemplateQuery(
        loadMailTemplateFilterParams("?search=%20intro%20&language=TR&page=2&type=PROPOSAL")
      )
    ).toEqual({ search: "intro", language: "TR", page: 2, amount: 25 });
  });

  test("a search longer than 100 characters is cut to 100", () => {
    const query = toMailTemplateQuery(loadMailTemplateFilterParams(`?search=${"a".repeat(150)}`));
    expect(query.search).toBe("a".repeat(100));
  });
});

describe("hasActiveMailTemplateFilter", () => {
  test("the page alone is no filter, a language is", () => {
    expect(hasActiveMailTemplateFilter(loadMailTemplateFilterParams("?page=3"))).toBe(false);
    expect(hasActiveMailTemplateFilter(loadMailTemplateFilterParams("?language=NL"))).toBe(true);
  });
});
