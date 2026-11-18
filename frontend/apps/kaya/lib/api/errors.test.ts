import { describe, expect, test } from "bun:test";
import { AxiosError, type AxiosResponse } from "axios";

import { ERROR_CODES, KayaApiError, toKayaApiError } from "./errors";

function refused(status: number, body: unknown): AxiosError {
  const response = { status, data: body } as AxiosResponse;
  return new AxiosError("Request failed", "ERR_BAD_REQUEST", undefined, undefined, response);
}

describe("KayaApiError.isNotMember", () => {
  test("is true for a 403 of a missing membership or an unknown organization", () => {
    expect(new KayaApiError("x", 403, ERROR_CODES.NOT_MEMBER).isNotMember).toBe(true);
  });

  test("is true for a 403 of a disabled membership or organization", () => {
    expect(new KayaApiError("x", 403, ERROR_CODES.KAYA_MEMBERSHIP_DISABLED).isNotMember).toBe(true);
    expect(new KayaApiError("x", 403, ERROR_CODES.MEMBERSHIP_DISABLED).isNotMember).toBe(true);
    expect(new KayaApiError("x", 403, ERROR_CODES.ORGANIZATION_DISABLED).isNotMember).toBe(true);
  });

  test("is true for a 404 of a missing membership or organization", () => {
    expect(new KayaApiError("x", 404, ERROR_CODES.MEMBERSHIP_NOT_FOUND).isNotMember).toBe(true);
    expect(new KayaApiError("x", 404, ERROR_CODES.ORGANIZATION_NOT_FOUND).isNotMember).toBe(true);
  });

  test("is false for the same codes under another status and for other 403 codes", () => {
    expect(new KayaApiError("x", 401, ERROR_CODES.MEMBERSHIP_DISABLED).isNotMember).toBe(false);
    expect(new KayaApiError("x", 403, ERROR_CODES.MEMBERSHIP_NOT_FOUND).isNotMember).toBe(false);
    expect(new KayaApiError("x", 404, ERROR_CODES.ORGANIZATION_DISABLED).isNotMember).toBe(false);
    expect(new KayaApiError("x", 400, ERROR_CODES.NOT_MEMBER).isNotMember).toBe(false);
    expect(new KayaApiError("x", 403, "ORG_SEC_003").isNotMember).toBe(false);
    expect(new KayaApiError("x", 403, "GEN_SEC_003").isNotMember).toBe(false);
    expect(new KayaApiError("x", 403, null).isNotMember).toBe(false);
  });
});

describe("toKayaApiError", () => {
  test("keeps the envelope's code, message and values", () => {
    const error = toKayaApiError(
      refused(409, {
        success: false,
        error: { code: "KAY_VAL_008", message: "Refused", errorValues: { email: "ada@example.com" } },
      })
    );
    expect(error.status).toBe(409);
    expect(error.code).toBe("KAY_VAL_008");
    expect(error.message).toBe("Refused");
    expect(error.errorValues).toEqual({ email: "ada@example.com" });
  });

  test("names the code when the envelope has no message", () => {
    const error = toKayaApiError(refused(404, { success: false, error: { code: "KAY_NF_001" } }));
    expect(error.message).toBe("Kaya answered 404 (KAY_NF_001)");
    expect(error.errorValues).toEqual({});
  });

  test("falls back to the status when the body is no envelope", () => {
    const error = toKayaApiError(refused(502, "<html>bad gateway</html>"));
    expect(error.status).toBe(502);
    expect(error.code).toBeNull();
    expect(error.message).toBe("Kaya answered 502");
  });

  test("reports a call that never got an answer", () => {
    const error = toKayaApiError(new AxiosError("timeout of 10000ms exceeded", "ECONNABORTED"));
    expect(error.status).toBeNull();
    expect(error.message).toBe("Kaya could not be reached: timeout of 10000ms exceeded");
  });

  test("returns a KayaApiError unchanged and wraps anything else", () => {
    const own = new KayaApiError("x", 403, "GEN_SEC_003");
    expect(toKayaApiError(own)).toBe(own);
    expect(toKayaApiError(new Error("boom")).message).toBe("boom");
    expect(toKayaApiError("text").message).toBe("text");
  });
});
