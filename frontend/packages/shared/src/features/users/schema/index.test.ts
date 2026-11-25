import { describe, expect, test } from "bun:test";
import { grantedAppSchema, userKindFieldsSchema, userOrganizationSchema } from "./index";

const base = {
  id: "7d1f4a0e-6a3b-4c0e-9a55-2f0c1b8d9e10",
  name: "Korur",
  role: "MEMBER",
  organizationStatus: "ACTIVE",
};

describe("userOrganizationSchema apps", () => {
  test("parses an app string this client does not know", () => {
    const parsed = userOrganizationSchema.safeParse({ ...base, apps: ["KAZAN", "LATER_APP"] });

    expect(parsed.success).toBe(true);
    expect(parsed.data?.apps).toEqual(["KAZAN", "LATER_APP"]);
  });

  test("parses a membership without apps", () => {
    const parsed = userOrganizationSchema.safeParse(base);

    expect(parsed.success).toBe(true);
    expect(parsed.data?.apps).toBeUndefined();
  });

  test("refuses apps that is not a list of strings", () => {
    expect(userOrganizationSchema.safeParse({ ...base, apps: "KAZAN" }).success).toBe(false);
  });
});

describe("grantedAppSchema", () => {
  test("accepts the grantable apps and refuses any other value", () => {
    expect(grantedAppSchema.safeParse("KAZAN").success).toBe(true);
    expect(grantedAppSchema.safeParse("KAYA").success).toBe(true);
    expect(grantedAppSchema.safeParse("KALKAN").success).toBe(true);
    expect(grantedAppSchema.safeParse("OTAK").success).toBe(true);
    expect(grantedAppSchema.safeParse("ADMIN").success).toBe(false);
    expect(grantedAppSchema.safeParse("ACCOUNT").success).toBe(false);
  });
});

describe("userKindFieldsSchema", () => {
  test("parses a person with null agent fields", () => {
    const parsed = userKindFieldsSchema.safeParse({
      kind: "HUMAN",
      agentRole: null,
      agentFlow: null,
      agentProvider: null,
    });

    expect(parsed.success).toBe(true);
  });

  test("parses an agent with its role, flow and provider", () => {
    const parsed = userKindFieldsSchema.safeParse({
      kind: "AGENT",
      agentRole: "CYBERSECURITY_ENGINEER",
      agentFlow: "ROUTINE",
      agentProvider: "ANTHROPIC",
    });

    expect(parsed.success).toBe(true);
    expect(parsed.data?.agentRole).toBe("CYBERSECURITY_ENGINEER");
  });

  test("refuses a kind the API does not send", () => {
    expect(
      userKindFieldsSchema.safeParse({
        kind: "ROBOT",
        agentRole: null,
        agentFlow: null,
        agentProvider: null,
      }).success,
    ).toBe(false);
  });
});
