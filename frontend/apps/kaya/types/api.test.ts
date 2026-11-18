import { describe, expect, test } from "bun:test";
import { z } from "zod";

import { apiErrorSchema, createApiResponseSchema, paginationSchema } from "./api";

const PAGE = {
  totalElements: 51,
  totalPages: 3,
  currentPage: 1,
  pageSize: 25,
  first: true,
  last: false,
  empty: false,
};

describe("createApiResponseSchema", () => {
  const schema = createApiResponseSchema(z.array(z.object({ id: z.string() })));

  test("reads a paged answer", () => {
    const parsed = schema.parse({ success: true, data: [{ id: "a" }], pagination: PAGE });
    expect(parsed.data).toEqual([{ id: "a" }]);
    expect(parsed.pagination?.totalPages).toBe(3);
  });

  test("reads a refusal with its code and values, and no data", () => {
    const parsed = schema.parse({
      success: false,
      message: "Email held",
      data: null,
      error: { code: "KAY_VAL_008", message: "no", errorValues: { email: "ada@example.com" } },
    });
    expect(parsed.error?.code).toBe("KAY_VAL_008");
    expect(parsed.error?.errorValues).toEqual({ email: "ada@example.com" });
    expect(parsed.data).toBeNull();
  });

  test("refuses data of another shape and a missing success flag", () => {
    expect(schema.safeParse({ success: true, data: [{ id: 1 }] }).success).toBe(false);
    expect(schema.safeParse({ data: [] }).success).toBe(false);
  });
});

describe("apiErrorSchema and paginationSchema", () => {
  test("an error needs only its code", () => {
    expect(apiErrorSchema.safeParse({ code: "KAY_NF_001" }).success).toBe(true);
    expect(apiErrorSchema.safeParse({ message: "x" }).success).toBe(false);
  });

  test("a page block needs every field", () => {
    expect(paginationSchema.safeParse(PAGE).success).toBe(true);
    expect(paginationSchema.safeParse({ ...PAGE, empty: undefined }).success).toBe(false);
  });
});
