import { describe, expect, test } from "bun:test";
import { ApiError } from "@oba/shared/api/axios";
import { mailSenderErrorText, mailSenderFieldErrors } from "./mail-sender-error";

const t = (key: string): string => `t:${key}`;

function apiError(code: string | undefined, errorValues: Record<string, unknown> = {}): ApiError {
  return new ApiError("server message", { status: 422, code, errorValues });
}

describe("mailSenderErrorText", () => {
  test.each([
    ["OTK_NOT_006", "t:errors.notFound"],
    ["OTK_STA_008", "t:errors.mailAddressTaken"],
    ["OTK_VAL_002", "t:errors.accountNotInTenant"],
    ["OTK_VAL_003", "t:errors.accountWithoutMailbox"],
    ["OTK_VAL_004", "t:errors.sharedMailboxNotFound"],
    ["NTF_UPS_001", "t:errors.microsoftUnreachable"],
    ["ORG_SEC_003", "t:errors.forbidden"],
    ["GEN_VAL_001", "t:errors.validation"],
  ])("%s shows its translated text", (code, text) => {
    expect(mailSenderErrorText(apiError(code), t, "toast.saveError")).toBe(text);
  });

  test("an unmapped code shows the server's message", () => {
    expect(mailSenderErrorText(apiError("OTK_XXX_999"), t, "toast.saveError")).toBe(
      "server message"
    );
  });

  test("an error without a message falls back to the fallback key", () => {
    expect(mailSenderErrorText(new Error(""), t, "toast.saveError")).toBe("t:toast.saveError");
    expect(mailSenderErrorText("boom", t, "toast.saveError")).toBe("t:toast.saveError");
  });
});

describe("mailSenderFieldErrors", () => {
  test("a taken address is shown on the mail address field", () => {
    expect(mailSenderFieldErrors(apiError("OTK_STA_008"), t)).toEqual({
      mailAddress: "t:errors.mailAddressTaken",
    });
  });

  test("an address with no account in the tenant is shown on the mail address field", () => {
    expect(mailSenderFieldErrors(apiError("OTK_VAL_002", { mailAddress: "a@example.com" }), t)).toEqual({
      mailAddress: "t:errors.accountNotInTenant",
    });
  });

  test("an account without a mailbox is shown on the mail address field with its own text", () => {
    expect(mailSenderFieldErrors(apiError("OTK_VAL_003", { mailAddress: "a@example.com" }), t)).toEqual({
      mailAddress: "t:errors.accountWithoutMailbox",
    });
  });

  test("an address that names no shared mailbox is shown on the mail address field", () => {
    expect(mailSenderFieldErrors(apiError("OTK_VAL_004", { mailAddress: "nobody@example.com" }), t)).toEqual({
      mailAddress: "t:errors.sharedMailboxNotFound",
    });
  });

  test("a 422 on the type is shown on the type field", () => {
    expect(mailSenderFieldErrors(apiError("GEN_VAL_001", { type: "Type is required" }), t)).toEqual({
      type: "Type is required",
    });
  });

  test("a 422 puts each error value on its field and drops non-text values", () => {
    expect(
      mailSenderFieldErrors(
        apiError("GEN_VAL_001", { mailAddress: "must be a well-formed email address", count: 3 }),
        t
      )
    ).toEqual({ mailAddress: "must be a well-formed email address" });
  });

  test("any other error names no field", () => {
    expect(mailSenderFieldErrors(apiError("NTF_UPS_001"), t)).toEqual({});
    expect(mailSenderFieldErrors(new Error("x"), t)).toEqual({});
  });
});
