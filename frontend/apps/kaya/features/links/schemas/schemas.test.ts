import { describe, expect, test } from "bun:test";

import { createApiResponseSchema } from "@/types/api";

import {
  dependencyQueryRequestSchema,
  dependencyResponseSchema,
  linkQueryRequestSchema,
  linkResponseSchema,
} from ".";

const LINK = {
  sourcePath: ".agents/rules/11-backend-rules.md",
  targetPath: "docs/private/conventions/backend/32-soft-delete-convention.md",
  line: 14,
  status: "FOUND",
};

describe("links", () => {
  test("a link of the server parses in a list envelope", () => {
    const envelope = createApiResponseSchema(linkResponseSchema.array());
    expect(envelope.safeParse({ success: true, data: [LINK] }).success).toBe(true);
  });

  test("a status other than FOUND or BROKEN, or a missing line, fails", () => {
    expect(linkResponseSchema.safeParse({ ...LINK, status: "MISSING" }).success).toBe(false);
    expect(linkResponseSchema.safeParse({ ...LINK, line: undefined }).success).toBe(false);
  });

  test("a query names a version, a repository or both", () => {
    expect(linkQueryRequestSchema.safeParse({ versionId: "v-1" }).success).toBe(true);
    expect(linkQueryRequestSchema.safeParse({ repositoryId: "r-1" }).success).toBe(true);
    expect(
      linkQueryRequestSchema.safeParse({ repositoryId: "r-1", versionId: "d-1" }).success
    ).toBe(true);
    expect(linkQueryRequestSchema.safeParse({}).success).toBe(false);
  });
});

describe("dependencies", () => {
  const RESPONSE = {
    path: ".agents/skills/kaizen/workflows/sprint.md",
    kind: "WORKFLOW",
    linksTo: [],
    usedBy: [
      {
        path: ".agents/skills/kaizen/SKILL.md",
        kind: "SKILL",
        relation: "SKILL_PART",
        status: "FOUND",
        versionId: "v-1",
      },
      {
        path: "docs/missing.md",
        kind: null,
        relation: "MARKDOWN_LINK",
        status: "BROKEN",
        versionId: null,
      },
    ],
  };

  test("the server's answer parses, a broken entry without kind or version included", () => {
    expect(dependencyResponseSchema.safeParse(RESPONSE).success).toBe(true);
  });

  test("an unknown relation fails", () => {
    const usedBy = [{ ...RESPONSE.usedBy[0], relation: "IMPORT" }];
    expect(dependencyResponseSchema.safeParse({ ...RESPONSE, usedBy }).success).toBe(false);
  });

  test("a read names 1 to 20 versions and a path", () => {
    expect(
      dependencyQueryRequestSchema.safeParse({ versionId: ["v-1"], path: "a.md" }).success
    ).toBe(true);
    expect(dependencyQueryRequestSchema.safeParse({ versionId: [], path: "a.md" }).success).toBe(
      false
    );
    const many = Array.from({ length: 21 }, (_, index: number) => `v-${index}`);
    expect(dependencyQueryRequestSchema.safeParse({ versionId: many, path: "a.md" }).success).toBe(
      false
    );
  });
});
