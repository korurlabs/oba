import { describe, expect, test } from "bun:test";

import { repositoryUpdateRequestSchema } from ".";

describe("repositoryUpdateRequestSchema", () => {
  test("accepts an empty template, which removes it", () => {
    expect(repositoryUpdateRequestSchema.safeParse({ ticketUrlTemplate: "" }).success).toBe(true);
  });

  test("needs https and {key} in a template", () => {
    expect(
      repositoryUpdateRequestSchema.safeParse({ ticketUrlTemplate: "https://k/{key}" }).success
    ).toBe(true);
    expect(
      repositoryUpdateRequestSchema.safeParse({ ticketUrlTemplate: "http://k/{key}" }).success
    ).toBe(false);
    expect(
      repositoryUpdateRequestSchema.safeParse({ ticketUrlTemplate: "https://k/" }).success
    ).toBe(false);
  });
});
