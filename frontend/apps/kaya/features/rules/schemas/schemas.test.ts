import { describe, expect, test } from "bun:test";

import {
  agentsFileListResponseSchema,
  hookListResponseSchema,
  roleListResponseSchema,
  ruleKindQueryRequestSchema,
  ruleListResponseSchema,
  subagentListResponseSchema,
  workflowListResponseSchema,
} from ".";

const RULE = {
  id: "r-1",
  versionId: "v-1",
  path: "00-core.md",
  description: "Core rules",
  paths: [],
  alwaysOn: true,
  overridesPath: null,
  conventions: [{ conventionPath: "docs/private/conventions/general/a.md" }],
  sizeBytes: 900,
};

describe("rule kind lists", () => {
  test("reads a rule; description and override are optional", () => {
    expect(ruleListResponseSchema.safeParse(RULE).success).toBe(true);
    expect(
      ruleListResponseSchema.safeParse({ ...RULE, description: null, overridesPath: undefined })
        .success
    ).toBe(true);
    expect(ruleListResponseSchema.safeParse({ ...RULE, alwaysOn: "yes" }).success).toBe(false);
  });

  test("an AGENTS.md row names its placement and refuses an unknown one", () => {
    const row = { id: "a-1", versionId: "v-1", placement: "MOUNT_ROOT", sizeBytes: 10 };
    expect(agentsFileListResponseSchema.safeParse(row).success).toBe(true);
    expect(agentsFileListResponseSchema.safeParse({ ...row, placement: "FOLDER" }).success).toBe(
      false
    );
  });

  test("subagents, roles, workflows and hooks read their typed fields", () => {
    expect(
      subagentListResponseSchema.safeParse({
        id: "s-1",
        versionId: "v-1",
        path: "coder.md",
        name: "coder",
        tools: null,
        maxTurns: 50,
        sizeBytes: 1,
      }).success
    ).toBe(true);
    expect(
      roleListResponseSchema.safeParse({ id: "o-1", versionId: "v-1", path: "a.md", sizeBytes: 1 })
        .success
    ).toBe(true);
    expect(
      workflowListResponseSchema.safeParse({
        id: "w-1",
        versionId: "v-1",
        path: null,
        sizeBytes: 1,
      }).success
    ).toBe(true);
    expect(
      hookListResponseSchema.safeParse({
        id: "h-1",
        versionId: "v-1",
        path: "a.sh",
        executable: true,
        sizeBytes: 1,
      }).success
    ).toBe(true);
  });

  test("a query names one to 20 versions", () => {
    expect(ruleKindQueryRequestSchema.safeParse({ versionId: ["v-1"] }).success).toBe(true);
    expect(ruleKindQueryRequestSchema.safeParse({ versionId: [] }).success).toBe(false);
    const many = Array.from({ length: 21 }, (_, index: number) => `v-${index}`);
    expect(ruleKindQueryRequestSchema.safeParse({ versionId: many }).success).toBe(false);
  });
});
