import { describe, expect, test } from "bun:test";

import { repositorySettingsResponseSchema, repositoryUpdateRequestSchema } from ".";

describe("settings responses", () => {
  test("reads a repository", () => {
    const repository = {
      id: "r",
      name: "app",
      remoteUrl: "git@github.com:acme/app.git",
      status: "ARCHIVED",
      createdAt: "t",
    };
    expect(repositorySettingsResponseSchema.safeParse(repository).success).toBe(true);
    expect(
      repositorySettingsResponseSchema.safeParse({ ...repository, status: "DELETED" }).success
    ).toBe(false);
  });
});

describe("settings requests", () => {
  test("a repository name has 1 to 100 characters after trimming", () => {
    expect(repositoryUpdateRequestSchema.safeParse({ name: "  " }).success).toBe(false);
    expect(repositoryUpdateRequestSchema.safeParse({ name: "a".repeat(100) }).success).toBe(true);
    expect(repositoryUpdateRequestSchema.safeParse({ name: "a".repeat(101) }).success).toBe(false);
  });

  test("a template has at most 500 characters", () => {
    const template = `https://k/{key}${"a".repeat(486)}`;
    expect(template).toHaveLength(501);
    expect(repositoryUpdateRequestSchema.safeParse({ ticketUrlTemplate: template }).success).toBe(
      false
    );
  });
});
