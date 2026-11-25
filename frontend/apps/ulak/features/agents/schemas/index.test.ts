import { describe, expect, test } from "bun:test";
import { agentFormSchema, agentRoutineFormSchema } from "./index";
import { routineFieldsOf } from "../lib/routine-fields";
import {
  toAgentCreateRequest,
  toAgentRoutineRequest,
  toAgentUpdateRequest,
} from "../lib/agent-request";

const t = (key: string) => key;

const VALID = {
  name: "  Pentest agent ",
  flow: "ROUTINE",
  provider: "ANTHROPIC",
  role: "CYBERSECURITY_ENGINEER",
  apps: ["KAZAN"],
};

function messagesOf(result: { success: boolean; error?: { issues: { message: string }[] } }) {
  return result.error?.issues.map((issue) => issue.message) ?? [];
}

describe("agentFormSchema", () => {
  const schema = agentFormSchema(t);

  test("accepts a complete agent", () => {
    expect(schema.safeParse(VALID).success).toBe(true);
  });

  test("refuses a blank name and one over 100 characters", () => {
    expect(messagesOf(schema.safeParse({ ...VALID, name: "   " }))).toEqual(["form.nameRequired"]);
    expect(messagesOf(schema.safeParse({ ...VALID, name: "x".repeat(101) }))).toEqual([
      "form.nameMaxLength",
    ]);
    expect(schema.safeParse({ ...VALID, name: "x".repeat(100) }).success).toBe(true);
  });

  test("needs a flow, a provider and a role", () => {
    expect(messagesOf(schema.safeParse({ ...VALID, flow: "" }))).toEqual(["form.flowRequired"]);
    expect(messagesOf(schema.safeParse({ ...VALID, provider: "" }))).toEqual([
      "form.providerRequired",
    ]);
    expect(messagesOf(schema.safeParse({ ...VALID, role: undefined }))).toEqual([
      "form.roleRequired",
    ]);
  });

  test("needs at least one grantable app", () => {
    expect(messagesOf(schema.safeParse({ ...VALID, apps: [] }))).toEqual(["form.appsRequired"]);
    expect(schema.safeParse({ ...VALID, apps: ["ULAK"] }).success).toBe(false);
  });
});

describe("agentRoutineFormSchema", () => {
  const anthropic = agentRoutineFormSchema(t, routineFieldsOf("ANTHROPIC").idPattern);

  test("accepts an Anthropic trig_ id and a secret", () => {
    expect(
      anthropic.safeParse({ routineId: "trig_01AbCdEf", routineSecret: "sk-ant-oat01-x" }).success
    ).toBe(true);
  });

  test("refuses an id in another format for Anthropic", () => {
    for (const routineId of ["01AbCdEf", "trig_", "trig_ab-cd", `trig_${"a".repeat(61)}`]) {
      expect(messagesOf(anthropic.safeParse({ routineId, routineSecret: "s" }))).toContain(
        "routine.idFormat"
      );
    }
  });

  test("a provider without a known format leaves the id format to the API", () => {
    const generic = agentRoutineFormSchema(t, routineFieldsOf("OPENAI").idPattern);
    expect(generic.safeParse({ routineId: "anything", routineSecret: "s" }).success).toBe(true);
  });

  test("needs both values within the API's limits", () => {
    expect(messagesOf(anthropic.safeParse({ routineId: "", routineSecret: "" }))).toEqual(
      expect.arrayContaining(["routine.idRequired", "routine.secretRequired"])
    );
    expect(
      messagesOf(anthropic.safeParse({ routineId: "trig_a", routineSecret: "s".repeat(513) }))
    ).toEqual(["routine.secretMaxLength"]);
  });
});

describe("routineFieldsOf", () => {
  test("labels Anthropic's values as it does", () => {
    expect(routineFieldsOf("ANTHROPIC")).toMatchObject({
      idLabelKey: "anthropicIdLabel",
      secretLabelKey: "anthropicSecretLabel",
      idPlaceholder: "trig_...",
    });
    expect(routineFieldsOf("XAI").idLabelKey).toBe("idLabel");
  });
});

describe("agent requests", () => {
  const values = agentFormSchema(t).parse(VALID);

  test("create sends every field with the name trimmed", () => {
    expect(toAgentCreateRequest(values)).toEqual({
      name: "Pentest agent",
      flow: "ROUTINE",
      provider: "ANTHROPIC",
      role: "CYBERSECURITY_ENGINEER",
      apps: ["KAZAN"],
    });
  });

  test("update leaves out the fixed flow and provider", () => {
    expect(toAgentUpdateRequest(values)).toEqual({
      name: "Pentest agent",
      role: "CYBERSECURITY_ENGINEER",
      apps: ["KAZAN"],
    });
  });

  test("the routine is sent trimmed", () => {
    expect(toAgentRoutineRequest({ routineId: " trig_a ", routineSecret: " s " })).toEqual({
      routineId: "trig_a",
      routineSecret: "s",
    });
  });
});
