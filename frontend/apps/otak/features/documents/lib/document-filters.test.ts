import { describe, expect, test } from "bun:test";
import { DOCUMENT_LIMITS, DOCUMENTS_PAGE_SIZE } from "../schemas";
import {
  hasActiveDocumentFilter,
  toDocumentQuery,
  type DocumentFilterParams,
} from "./document-filters";

const NO_FILTERS: DocumentFilterParams = {
  search: null,
  relation: null,
  legalEntity: null,
  language: null,
  type: null,
  page: 1,
};

describe("toDocumentQuery", () => {
  test("an unfiltered list asks for the first page only", () => {
    expect(toDocumentQuery(NO_FILTERS)).toEqual({ page: 1, amount: DOCUMENTS_PAGE_SIZE });
  });

  test("every filter maps to its query parameter", () => {
    expect(
      toDocumentQuery({
        search: " offer ",
        relation: "rel-1",
        legalEntity: "le-1",
        language: "EN",
        type: "CONTRACT",
        page: 3,
      })
    ).toEqual({
      search: "offer",
      relationId: "rel-1",
      legalEntityId: "le-1",
      language: "EN",
      type: "CONTRACT",
      page: 3,
      amount: DOCUMENTS_PAGE_SIZE,
    });
  });

  test("a blank search is left out", () => {
    expect(toDocumentQuery({ ...NO_FILTERS, search: "   " })).not.toHaveProperty("search");
  });

  test("a search longer than the server takes is cut to its limit", () => {
    const query = toDocumentQuery({ ...NO_FILTERS, search: "a".repeat(150) });
    expect(query.search).toHaveLength(DOCUMENT_LIMITS.searchMax);
  });

  test("a page below one asks for the first page", () => {
    expect(toDocumentQuery({ ...NO_FILTERS, page: 0 }).page).toBe(1);
  });
});

describe("hasActiveDocumentFilter", () => {
  test("the page alone is no filter", () => {
    expect(hasActiveDocumentFilter({ ...NO_FILTERS, page: 4 })).toBe(false);
  });

  test("a blank search is no filter", () => {
    expect(hasActiveDocumentFilter({ ...NO_FILTERS, search: "  " })).toBe(false);
  });

  test.each([
    ["search", { search: "offer" }],
    ["relation", { relation: "rel-1" }],
    ["legal entity", { legalEntity: "le-1" }],
    ["language", { language: "TR" as const }],
    ["type", { type: "NDA" as const }],
  ])("a %s narrows the list", (_, filter) => {
    expect(hasActiveDocumentFilter({ ...NO_FILTERS, ...filter })).toBe(true);
  });
});
