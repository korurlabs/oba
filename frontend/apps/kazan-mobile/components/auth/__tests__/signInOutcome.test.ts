import { describe, expect, it } from "bun:test";

import { classifySignInFailure } from "@/components/auth/signInOutcome";
import { SIGN_IN_FAILED_CODE, SignInCancelledError, SignInError } from "@/contexts/signInErrors";

describe("classifySignInFailure", () => {
  it("treats a closed login page as a cancel, which shows no error", () => {
    expect(classifySignInFailure(new SignInCancelledError())).toEqual({ kind: "cancelled" });
  });

  it("keeps the OAuth error code of a failed exchange", () => {
    expect(classifySignInFailure(new SignInError("invalid_grant", "Code not valid"))).toEqual({
      kind: "failed",
      code: "invalid_grant",
    });
  });

  it("falls back to the generic code for a blank code or an unexpected error", () => {
    const blank = classifySignInFailure(new SignInError(" ", "no code"));
    const unexpected = classifySignInFailure(new Error("boom"));
    expect(blank).toEqual({ kind: "failed", code: SIGN_IN_FAILED_CODE });
    expect(unexpected).toEqual({ kind: "failed", code: SIGN_IN_FAILED_CODE });
  });
});
