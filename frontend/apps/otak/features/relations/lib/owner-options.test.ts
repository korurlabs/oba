import { describe, expect, test } from "bun:test";
import type { RelationOwnerCandidate } from "../schemas";
import { hasOtakAccess, ownerCandidateOptions } from "./owner-options";

const NO_ACCESS = "No Otak access";

function candidate(
  userId: string,
  otakAccess: boolean | undefined,
  names: Partial<Pick<RelationOwnerCandidate, "firstName" | "lastName" | "email">> = {}
): RelationOwnerCandidate {
  return {
    userId,
    email: `${userId}@example.com`,
    firstName: null,
    lastName: null,
    role: "MEMBER",
    otakAccess,
    ...names,
  };
}

describe("hasOtakAccess", () => {
  test("only true grants access; a missing field means no access", () => {
    expect(hasOtakAccess(candidate("a", true))).toBe(true);
    expect(hasOtakAccess(candidate("b", false))).toBe(false);
    expect(hasOtakAccess(candidate("c", undefined))).toBe(false);
  });
});

describe("ownerCandidateOptions", () => {
  test("lists every candidate in the server's order; one without access cannot be picked", () => {
    const options = ownerCandidateOptions(
      [
        candidate("u1", true, { firstName: "Ada", lastName: "Lovelace" }),
        candidate("u2", false, { firstName: "Bob" }),
        candidate("u3", undefined),
      ],
      { noAccessLabel: NO_ACCESS }
    );

    expect(options).toEqual([
      { value: "u1", label: "Ada Lovelace" },
      { value: "u2", label: "Bob", disabled: true, disabledLabel: NO_ACCESS },
      { value: "u3", label: "u3@example.com", disabled: true, disabledLabel: NO_ACCESS },
    ]);
  });

  test("the stored owner without access stays pickable, so an edit can keep them", () => {
    const options = ownerCandidateOptions([candidate("u2", false, { firstName: "Bob" })], {
      currentOwner: { id: "u2", name: "Bob" },
      noAccessLabel: NO_ACCESS,
    });

    expect(options).toEqual([{ value: "u2", label: "Bob" }]);
  });

  test("a stored owner who is no longer listed is added at the end", () => {
    const options = ownerCandidateOptions([candidate("u1", true, { firstName: "Ada" })], {
      currentOwner: { id: "gone", name: "Former Owner" },
      noAccessLabel: NO_ACCESS,
    });

    expect(options).toEqual([
      { value: "u1", label: "Ada" },
      { value: "gone", label: "Former Owner" },
    ]);
  });

  test("no candidates yet gives no options", () => {
    expect(ownerCandidateOptions(undefined, { noAccessLabel: NO_ACCESS })).toEqual([]);
  });
});
