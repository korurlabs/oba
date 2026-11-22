import { describe, expect, test } from "bun:test";
import { PICKER_SIZE } from "../schemas";
import { relationPickerQuery, templatePickerQuery } from "./picker-queries";

describe("relationPickerQuery", () => {
  test("an empty search asks for the first relations", () => {
    expect(relationPickerQuery("  ")).toEqual({ page: 1, amount: PICKER_SIZE });
  });

  test("a search is trimmed", () => {
    expect(relationPickerQuery(" acme ")).toEqual({ search: "acme", page: 1, amount: PICKER_SIZE });
  });
});

describe("templatePickerQuery", () => {
  test("without a legal entity every template is searched", () => {
    expect(templatePickerQuery("offer", null)).toEqual({
      search: "offer",
      page: 1,
      amount: PICKER_SIZE,
    });
  });

  test("a legal entity narrows the templates", () => {
    expect(templatePickerQuery("", "le-1")).toEqual({
      legalEntityId: "le-1",
      page: 1,
      amount: PICKER_SIZE,
    });
  });
});
