import { describe, expect, it } from "bun:test";

import {
  SIGN_IN_FAILED_CODE,
  SignInCancelledError,
  SignInError,
  isSignInCancelled,
  oauthErrorCode,
  toSignInError,
} from "@/contexts/signInErrors";

describe("signInErrors", () => {
  it("takes the OAuth code of an expo-auth-session error", () => {
    expect(oauthErrorCode({ code: "invalid_grant" })).toBe("invalid_grant");
    expect(oauthErrorCode({ code: "ERR_SOMETHING" })).toBe(SIGN_IN_FAILED_CODE);
    expect(oauthErrorCode(new Error("offline"))).toBe(SIGN_IN_FAILED_CODE);
    expect(oauthErrorCode(undefined)).toBe(SIGN_IN_FAILED_CODE);
  });

  it("passes SignInError and cancellation through and wraps anything else", () => {
    const failure = new SignInError("access_denied", "denied");
    const cancelled = new SignInCancelledError();
    expect(toSignInError(failure)).toBe(failure);
    expect(toSignInError(cancelled)).toBe(cancelled);
    expect(isSignInCancelled(toSignInError(cancelled))).toBe(true);
    const wrapped = toSignInError(Object.assign(new Error("bad"), { code: "invalid_request" }));
    expect(wrapped).toBeInstanceOf(SignInError);
    expect((wrapped as SignInError).code).toBe("invalid_request");
  });
});
