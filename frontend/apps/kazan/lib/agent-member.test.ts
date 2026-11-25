import { describe, expect, test } from "bun:test";
import { ApiError } from "@oba/shared/api/axios";
import {
  AGENT_ACTION_REFUSED_CODE,
  AGENT_TARGET_REFUSED_CODE,
  agentRefusalMessageKey,
  isAgent,
} from "./agent-member";

function refusal(code: string, errorValues: Record<string, unknown> = {}): ApiError {
  return new ApiError("refused", { status: 422, code, errorValues });
}

describe("isAgent", () => {
  test("is true only for kind AGENT", () => {
    expect(isAgent({ kind: "AGENT" })).toBe(true);
    expect(isAgent({ kind: "HUMAN" })).toBe(false);
    expect(isAgent({})).toBe(false);
  });
});

describe("agentRefusalMessageKey", () => {
  test("names the board role refusal", () => {
    const error = refusal(AGENT_TARGET_REFUSED_CODE, { action: "BOARD_ROLE", userId: "a" });
    expect(agentRefusalMessageKey(error)).toBe("agents.refusals.boardRole");
  });

  test("names the reviewer refusal", () => {
    const error = refusal(AGENT_TARGET_REFUSED_CODE, { action: "TICKET_REVIEWER" });
    expect(agentRefusalMessageKey(error)).toBe("agents.refusals.ticketReviewer");
  });

  test("names the caller refusals for done and delete", () => {
    expect(
      agentRefusalMessageKey(refusal(AGENT_ACTION_REFUSED_CODE, { action: "TICKET_DONE" }))
    ).toBe("agents.refusals.ticketDone");
    expect(
      agentRefusalMessageKey(refusal(AGENT_ACTION_REFUSED_CODE, { action: "TICKET_DELETE" }))
    ).toBe("agents.refusals.ticketDelete");
  });

  test("falls back to the generic text of each code for another action", () => {
    expect(
      agentRefusalMessageKey(refusal(AGENT_TARGET_REFUSED_CODE, { action: "MEMBER_ROLE" }))
    ).toBe("agents.refusals.targetRefused");
    expect(agentRefusalMessageKey(refusal(AGENT_ACTION_REFUSED_CODE, { action: "toString" }))).toBe(
      "agents.refusals.actionRefused"
    );
    expect(agentRefusalMessageKey(refusal(AGENT_TARGET_REFUSED_CODE))).toBe(
      "agents.refusals.targetRefused"
    );
  });

  test("is null for any other error", () => {
    expect(agentRefusalMessageKey(refusal("APP_VAL_002"))).toBeNull();
    expect(agentRefusalMessageKey(new Error("network"))).toBeNull();
  });
});
