import { describe, expect, test } from "bun:test";
import { ApiError } from "@oba/shared/api/axios";
import { outreachErrorText, outreachFieldErrors } from "./outreach-error";

const t = (key: string): string => `t:${key}`;

function apiError(code: string | undefined, errorValues: Record<string, unknown> = {}): ApiError {
  return new ApiError("server message", { status: 422, code, errorValues });
}

describe("outreachErrorText", () => {
  test.each([
    ["OTK_NOT_001", "t:errors.relationNotFound"],
    ["OTK_NOT_002", "t:errors.contactNotFound"],
    ["OTK_NOT_005", "t:errors.notFound"],
    ["OTK_NOT_006", "t:errors.senderNotFound"],
    ["OTK_NOT_010", "t:errors.templateNotFound"],
    ["OTK_NOT_012", "t:errors.campaignNotFound"],
    ["OTK_STA_011", "t:errors.notEditable"],
    ["OTK_VAL_004", "t:errors.sharedMailboxNotFound"],
    ["OTK_VAL_007", "t:errors.contactWithoutAddress"],
    ["OTK_VAL_008", "t:errors.attachmentsTooLarge"],
    ["STG_VAL_005", "t:errors.attachmentDuplicate"],
    ["OTK_VAL_014", "t:errors.actionNotForType"],
    ["OTK_VAL_015", "t:errors.contactWithoutPhone"],
    ["NTF_UPS_001", "t:errors.sendFailed"],
    ["AZR_UPS_002", "t:errors.storageFailed"],
    ["ORG_SEC_003", "t:errors.forbidden"],
    ["GEN_VAL_001", "t:errors.validation"],
  ])("%s shows its translated text", (code, text) => {
    expect(outreachErrorText(apiError(code), t, "toast.saveError")).toBe(text);
  });

  test.each(["OTK_STA_007", "OTK_NOT_008"])(
    "the code %s has no outreach text of its own",
    (code) => {
      expect(outreachErrorText(apiError(code), t, "toast.saveError")).toBe("server message");
    }
  );

  test("an error without a message falls back to the fallback key", () => {
    expect(outreachErrorText(new Error(""), t, "toast.saveError")).toBe("t:toast.saveError");
    expect(outreachErrorText("boom", t, "toast.saveError")).toBe("t:toast.saveError");
  });
});

describe("outreachFieldErrors", () => {
  test("a 422 puts the nested mail fields on the form's own fields", () => {
    expect(
      outreachFieldErrors(
        apiError("GEN_VAL_001", {
          title: "Title is required",
          "mail.subject": "Subject is required",
          "mail.body": "Body is required",
          count: 3,
        }),
        t
      )
    ).toEqual({
      title: "Title is required",
      subject: "Subject is required",
      body: "Body is required",
    });
  });

  test("a campaign that no longer exists is put on the campaign field", () => {
    expect(outreachFieldErrors(apiError("OTK_NOT_012"), t)).toEqual({
      campaignId: "t:errors.campaignNotFound",
    });
  });

  test("any other error names no field", () => {
    expect(outreachFieldErrors(apiError("OTK_STA_011"), t)).toEqual({});
    expect(outreachFieldErrors(new Error("x"), t)).toEqual({});
  });
});
