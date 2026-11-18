import { describe, expect, test } from "bun:test";

import { accessLoss, accessLossOf, reportAccessLoss, subscribeToAccessLoss } from "./access";
import { KayaApiError } from "./errors";

describe("accessLossOf", () => {
  test("a disabled user is a lost access", () => {
    expect(
      accessLossOf(new KayaApiError("x", 403, "GEN_SEC_002", { userStatus: "DISABLED" }))
    ).toBe("disabled");
  });

  test("a missing membership is a lost membership", () => {
    expect(accessLossOf(new KayaApiError("x", 403, "GEN_SEC_002", { organizationId: "o" }))).toBe(
      "not-member"
    );
  });

  test("a GEN_SEC_002 without a user status is a lost membership", () => {
    expect(accessLossOf(new KayaApiError("x", 403, "GEN_SEC_002"))).toBe("not-member");
  });

  test("a write to another member's draft is an ordinary error", () => {
    expect(
      accessLossOf(
        new KayaApiError("x", 403, "GEN_SEC_002", { versionId: "v-1", proposedBy: "user-ada" })
      )
    ).toBeNull();
  });

  test("a refused action of one user is an ordinary error", () => {
    expect(
      accessLossOf(new KayaApiError("x", 403, "GEN_SEC_003", { machineId: "a", userId: "b" }))
    ).toBeNull();
    expect(accessLossOf(new KayaApiError("x", 403, "GEN_SEC_003", {}))).toBeNull();
  });

  test("an app access refusal is a lost app, not a lost membership", () => {
    expect(accessLossOf(new KayaApiError("x", 403, "APP_SEC_001"))).toBe("no-app");
  });

  test("an APP_SEC_001 that is not a 403 is an ordinary error", () => {
    expect(accessLossOf(new KayaApiError("x", 500, "APP_SEC_001"))).toBeNull();
  });

  test("a 401 means signed out, whatever its code", () => {
    expect(accessLossOf(new KayaApiError("x", 401, "GEN_AUT_001"))).toBe("signed-out");
    expect(accessLossOf(new KayaApiError("x", 401, null))).toBe("signed-out");
  });

  test("a disabled or missing membership or organization means not a member", () => {
    expect(accessLossOf(new KayaApiError("x", 403, "KAY_SEC_002"))).toBe("not-member");
    expect(accessLossOf(new KayaApiError("x", 403, "ORG_SEC_002"))).toBe("not-member");
    expect(accessLossOf(new KayaApiError("x", 403, "ORG_SEC_005"))).toBe("not-member");
    expect(accessLossOf(new KayaApiError("x", 404, "ORG_NOT_001"))).toBe("not-member");
    expect(accessLossOf(new KayaApiError("x", 404, "ORG_NOT_002"))).toBe("not-member");
  });

  test("a platform role refusal is an ordinary error", () => {
    expect(accessLossOf(new KayaApiError("x", 403, "ORG_SEC_003"))).toBeNull();
  });

  test("a membership code under another status is no loss", () => {
    expect(accessLossOf(new KayaApiError("x", 404, "KAY_SEC_002"))).toBeNull();
    expect(accessLossOf(new KayaApiError("x", 500, "ORG_SEC_002"))).toBeNull();
  });

  test("a user status wins over an organization id on GEN_SEC_002", () => {
    expect(
      accessLossOf(
        new KayaApiError("x", 403, "GEN_SEC_002", { userStatus: "DISABLED", organizationId: "o" })
      )
    ).toBe("disabled");
  });

  test("not-found, validation, server and network failures are no loss", () => {
    expect(accessLossOf(new KayaApiError("x", 404, "KAY_NF_001"))).toBeNull();
    expect(accessLossOf(new KayaApiError("x", 409, "KAY_VAL_006"))).toBeNull();
    expect(accessLossOf(new KayaApiError("x", 500, null))).toBeNull();
    expect(accessLossOf(new KayaApiError("x", null, null))).toBeNull();
  });

  test("anything that is no KayaApiError is no loss", () => {
    expect(accessLossOf(new Error("401"))).toBeNull();
    expect(accessLossOf(null)).toBeNull();
    expect(accessLossOf({ status: 401 })).toBeNull();
  });
});

describe("reportAccessLoss", () => {
  // The loss is module state with no reset, so the steps run in order in one test.
  test("records a loss once, tells its listeners and ignores ordinary errors", () => {
    let calls = 0;
    const unsubscribe = subscribeToAccessLoss(() => {
      calls += 1;
    });
    expect(accessLoss()).toBeNull();

    reportAccessLoss(new KayaApiError("x", 403, "GEN_SEC_003"));
    reportAccessLoss(new Error("network"));
    expect(accessLoss()).toBeNull();
    expect(calls).toBe(0);

    reportAccessLoss(new KayaApiError("x", 403, "KAY_SEC_002"));
    expect(accessLoss()).toBe("not-member");
    expect(calls).toBe(1);

    // The same loss again is not news.
    reportAccessLoss(new KayaApiError("x", 403, "ORG_SEC_002"));
    expect(calls).toBe(1);

    // A different loss replaces it.
    reportAccessLoss(new KayaApiError("x", 401, "GEN_AUT_001"));
    expect(accessLoss()).toBe("signed-out");
    expect(calls).toBe(2);

    // An ordinary error afterwards leaves the recorded loss alone.
    reportAccessLoss(new KayaApiError("x", 404, "KAY_NF_001"));
    expect(accessLoss()).toBe("signed-out");

    unsubscribe();
    reportAccessLoss(new KayaApiError("x", 403, "GEN_SEC_002", { userStatus: "DISABLED" }));
    expect(accessLoss()).toBe("disabled");
    expect(calls).toBe(2);
  });
});
