import { describe, expect, test } from "bun:test";

import { skillListResponseSchema, skillQueryRequestSchema } from ".";

const SKILL = {
  id: "sk-1",
  versionId: "v-1",
  path: "kaizen",
  name: "kaizen",
  description: "Ticket work.",
  sizeBytes: 420,
};

describe("skills", () => {
  test("reads a skill; the description is optional", () => {
    expect(skillListResponseSchema.safeParse(SKILL).success).toBe(true);
    expect(skillListResponseSchema.safeParse({ ...SKILL, description: null }).success).toBe(true);
    expect(skillListResponseSchema.safeParse({ ...SKILL, sizeBytes: "420" }).success).toBe(false);
  });

  test("a query names one to 20 versions", () => {
    expect(skillQueryRequestSchema.safeParse({ versionId: ["v-1"] }).success).toBe(true);
    expect(skillQueryRequestSchema.safeParse({ versionId: [] }).success).toBe(false);
    const many = Array.from({ length: 21 }, (_, index: number) => `v-${index}`);
    expect(skillQueryRequestSchema.safeParse({ versionId: many }).success).toBe(false);
  });
});
