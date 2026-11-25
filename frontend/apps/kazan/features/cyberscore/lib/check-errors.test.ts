import { describe, expect, test } from "bun:test";
import { ApiError } from "@oba/shared/api/axios";
import { checkErrorKey, isCheckNotFound } from "./check-errors";

describe("checkErrorKey", () => {
  test.each([
    ["KAZ_STA_020", "duplicateName"],
    ["KAZ_NOT_020", "notFound"],
    ["ORG_SEC_003", "notAllowed"],
    ["GEN_VAL_001", "invalid"],
  ])("%s maps to %s", (code, key) => {
    expect(checkErrorKey(new ApiError("refused", { code }))).toBe(key as "invalid");
  });

  test("an unknown code, no code and a plain error give null", () => {
    expect(checkErrorKey(new ApiError("refused", { code: "OTHER_001" }))).toBeNull();
    expect(checkErrorKey(new ApiError("refused", { status: 500 }))).toBeNull();
    expect(checkErrorKey(new Error("boom"))).toBeNull();
    expect(checkErrorKey(undefined)).toBeNull();
  });
});

describe("isCheckNotFound", () => {
  test("is true only for the not-found code", () => {
    expect(isCheckNotFound(new ApiError("gone", { code: "KAZ_NOT_020", status: 404 }))).toBe(true);
    expect(isCheckNotFound(new ApiError("dup", { code: "KAZ_STA_020" }))).toBe(false);
    expect(isCheckNotFound(new Error("gone"))).toBe(false);
  });
});
