import { describe, expect, test } from "bun:test";
import {
  hasActiveDocumentTemplateFilter,
  loadDocumentTemplateFilterParams,
  toDocumentTemplateQuery,
} from "./document-template-filters";

describe("toDocumentTemplateQuery", () => {
  test("an empty URL lists the first page, 25 rows, without filters", () => {
    expect(toDocumentTemplateQuery(loadDocumentTemplateFilterParams(""))).toEqual({
      page: 1,
      amount: 25,
    });
  });

  test("the search, type, language and page are read from the URL", () => {
    expect(
      toDocumentTemplateQuery(
        loadDocumentTemplateFilterParams("?search=%20intro%20&type=PROPOSAL&language=TR&page=2")
      )
    ).toEqual({ search: "intro", type: "PROPOSAL", language: "TR", page: 2, amount: 25 });
  });

  test("the legal entity is sent as legalEntityId", () => {
    expect(toDocumentTemplateQuery(loadDocumentTemplateFilterParams("?legalEntity=le-1"))).toEqual({
      legalEntityId: "le-1",
      page: 1,
      amount: 25,
    });
  });

  test("an unknown type or language is left out", () => {
    expect(
      toDocumentTemplateQuery(loadDocumentTemplateFilterParams("?type=LETTER&language=DE"))
    ).toEqual({ page: 1, amount: 25 });
  });

  test("a search longer than 100 characters is cut to 100", () => {
    const query = toDocumentTemplateQuery(
      loadDocumentTemplateFilterParams(`?search=${"a".repeat(150)}`)
    );
    expect(query.search).toBe("a".repeat(100));
  });

  test("a page below 1 is raised to 1", () => {
    expect(
      toDocumentTemplateQuery({
        search: null,
        type: null,
        language: null,
        legalEntity: null,
        page: 0,
      }).page
    ).toBe(1);
  });
});

describe("hasActiveDocumentTemplateFilter", () => {
  test("the page alone or a blank search is no filter", () => {
    expect(hasActiveDocumentTemplateFilter(loadDocumentTemplateFilterParams("?page=3"))).toBe(
      false
    );
    expect(hasActiveDocumentTemplateFilter(loadDocumentTemplateFilterParams("?search=%20"))).toBe(
      false
    );
  });

  test("a search, type, language or legal entity is a filter", () => {
    expect(
      hasActiveDocumentTemplateFilter(loadDocumentTemplateFilterParams("?legalEntity=le-1"))
    ).toBe(true);
    expect(hasActiveDocumentTemplateFilter(loadDocumentTemplateFilterParams("?search=a"))).toBe(
      true
    );
    expect(hasActiveDocumentTemplateFilter(loadDocumentTemplateFilterParams("?type=NDA"))).toBe(
      true
    );
    expect(hasActiveDocumentTemplateFilter(loadDocumentTemplateFilterParams("?language=EN"))).toBe(
      true
    );
  });
});
