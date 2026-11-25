import { describe, expect, test } from "bun:test";
import { splitCheckName } from "./check-name";

describe("splitCheckName", () => {
  test.each([
    ["ID-01 MFA is not enforced", "ID-01", "MFA is not enforced"],
    ["DSC-12 Pin actions to a commit", "DSC-12", "Pin actions to a commit"],
    ["  CP-3   Spaces around  ", "CP-3", "Spaces around"],
  ])("%s has the reference %s", (name, reference, title) => {
    expect(splitCheckName(name)).toEqual({ reference, title });
  });

  test.each([
    "MFA on admin accounts",
    "id-01 lower case is not a reference",
    "ID-01",
    "TOOLONGREF-01 title",
    "Wi-Fi 6 rollout",
  ])("%s keeps its whole name as the title", (name) => {
    expect(splitCheckName(name)).toEqual({ reference: null, title: name });
  });
});
