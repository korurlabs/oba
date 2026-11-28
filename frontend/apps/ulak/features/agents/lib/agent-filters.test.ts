import { describe, expect, test } from "bun:test";
import type { Agent } from "../schemas";
import { filterAgents, hasActiveAgentFilter } from "./agent-filters";

const pentest = {
  id: "a-1",
  name: "Pentest agent",
  flow: "ROUTINE",
  provider: "ANTHROPIC",
  status: "ACTIVE",
} as Agent;
const sales = {
  id: "a-2",
  name: "Sales agent",
  flow: "API",
  provider: "OPENAI",
  status: "DISABLED",
} as Agent;

const none = { search: null, status: null, flow: null, provider: null };

describe("filterAgents", () => {
  test("without a search or filter every agent shows", () => {
    expect(filterAgents([pentest, sales], none)).toEqual([pentest, sales]);
    expect(hasActiveAgentFilter(none)).toBe(false);
  });

  test("the search matches the name", () => {
    expect(filterAgents([pentest, sales], { ...none, search: "PENTEST" })).toEqual([pentest]);
  });

  test("status, flow and provider each narrow the list", () => {
    expect(filterAgents([pentest, sales], { ...none, status: "DISABLED" })).toEqual([sales]);
    expect(filterAgents([pentest, sales], { ...none, flow: "ROUTINE" })).toEqual([pentest]);
    expect(filterAgents([pentest, sales], { ...none, provider: "OPENAI" })).toEqual([sales]);
    expect(filterAgents([pentest, sales], { ...none, flow: "API", provider: "ANTHROPIC" })).toEqual(
      []
    );
    expect(hasActiveAgentFilter({ ...none, provider: "XAI" })).toBe(true);
  });
});
