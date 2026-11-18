import { describe, expect, test } from "bun:test";
import { ApiError } from "@oba/shared/api/axios";
import { relationErrorText, relationFieldErrors } from "./relation-error";

const t = (key: string): string => `t:${key}`;

function apiError(code: string, status = 404): ApiError {
  return new ApiError("server message", { status, code });
}

describe("relation errors of the ICP link", () => {
  test("OTK_NOT_004 shows the ICP-not-found text", () => {
    expect(relationErrorText(apiError("OTK_NOT_004"), t, "toast.saveFailed")).toBe(
      "t:errors.icpNotFound"
    );
  });

  test("OTK_NOT_004 is shown on the icpId field", () => {
    expect(relationFieldErrors(apiError("OTK_NOT_004"), t)).toEqual({
      icpId: "t:errors.icpNotFound",
    });
  });

  test("OTK_STA_001 stays on the owner field", () => {
    expect(relationFieldErrors(apiError("OTK_STA_001"), t)).toEqual({
      ownerUserId: "t:errors.ownerNotMember",
    });
  });
});

describe("relation errors of the event link", () => {
  test("OTK_NOT_009 shows the event-not-found text", () => {
    expect(relationErrorText(apiError("OTK_NOT_009"), t, "toast.saveError")).toBe(
      "t:errors.eventNotFound"
    );
  });

  test("OTK_NOT_009 is shown on the eventId field", () => {
    expect(relationFieldErrors(apiError("OTK_NOT_009"), t)).toEqual({
      eventId: "t:errors.eventNotFound",
    });
  });
});

describe("relation errors of an owner without Otak access", () => {
  const refusal = (): ApiError => apiError("APP_VAL_002", 422);

  test("APP_VAL_002 shows the no-Otak-access text, not the server message", () => {
    expect(relationErrorText(refusal(), t, "toast.saveError")).toBe(
      "t:errors.ownerWithoutOtakAccess"
    );
  });

  test("APP_VAL_002 is shown on the owner field", () => {
    expect(relationFieldErrors(refusal(), t)).toEqual({
      ownerUserId: "t:errors.ownerWithoutOtakAccess",
    });
  });
});
