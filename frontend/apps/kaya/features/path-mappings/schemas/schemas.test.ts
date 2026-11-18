import { describe, expect, test } from "bun:test";

import {
  pathMappingQueryRequestSchema,
  pathMappingResponseSchema,
  toolMappingResponseSchema,
} from ".";
import { PATH_MAPPING_ROWS } from "./path-mapping-rows.fixture";
import { TOOL_MAPPINGS, withStates } from "./tool-mappings.fixture";

describe("path mapping schemas", () => {
  test("every row of the server's answer parses, with and without a count", () => {
    const parsed = pathMappingResponseSchema.array().parse(PATH_MAPPING_ROWS);
    expect(parsed).toHaveLength(21);
    expect(parsed.filter((row) => row.kind === "TOOL_LINK").map((row) => row.link)).toEqual([
      "ROOT_AGENTS_FILE",
      "CLAUDE_RULES",
      "CLAUDE_TEMPLATES",
    ]);
    expect(pathMappingResponseSchema.parse({ ...PATH_MAPPING_ROWS[13], count: 3 }).count).toBe(3);
  });

  test("a format the client does not know is refused", () => {
    const result = pathMappingResponseSchema.safeParse({
      ...PATH_MAPPING_ROWS[0],
      formats: ["TOML"],
    });
    expect(result.success).toBe(false);
  });

  test("the request takes no version and at most 20", () => {
    expect(pathMappingQueryRequestSchema.safeParse({ versionId: [] }).success).toBe(true);
    const many = Array.from({ length: 21 }, (_, index: number) => `v-${index}`);
    expect(pathMappingQueryRequestSchema.safeParse({ versionId: many }).success).toBe(false);
  });

  test("the tool view parses for every tool, with and without states", () => {
    const parsed = toolMappingResponseSchema.array().parse(TOOL_MAPPINGS);
    expect(parsed.map((entry) => entry.tool)).toEqual([
      "CLAUDE_CODE",
      "CODEX",
      "CURSOR",
      "GEMINI_CLI",
    ]);
    expect(parsed[0]?.reads[1]).toMatchObject({
      readPath: ".claude/rules/<path>.md",
      linkPath: ".claude/rules",
      linkTarget: "../.agents/rules",
      state: null,
    });
    const counted = withStates(TOOL_MAPPINGS[0]!, [{ state: "LINKED", count: 1 }]);
    expect(toolMappingResponseSchema.parse(counted).reads[0]).toMatchObject({
      state: "LINKED",
      count: 1,
    });
  });

  test("a state or loading the client does not know is refused", () => {
    const claude = TOOL_MAPPINGS[0]!;
    const state = withStates(claude, [{ state: "PARTLY" as never, count: 1 }]);
    expect(toolMappingResponseSchema.safeParse(state).success).toBe(false);
    const loading = {
      ...claude,
      reads: [{ ...claude.reads[0]!, loading: "LATER" }],
    };
    expect(toolMappingResponseSchema.safeParse(loading).success).toBe(false);
  });
});
