import { describe, expect, it } from "bun:test";

import { VISIBLE_ASSIGNEES, type AssigneeOption } from "@/components/boards/boardForms";
import {
  assigneeChoices,
  filterConfirmState,
  foldForSearch,
  matchesPerson,
  showsAssigneeSearch,
} from "@/components/boards/filters/filterSheetModel";

function people(count: number): AssigneeOption[] {
  return Array.from({ length: count }, (_, index) => ({
    id: `u${index}`,
    name: `Person ${String(index).padStart(2, "0")}`,
    isMe: index === 0,
  }));
}

describe("foldForSearch", () => {
  it("ignores case, accents and the Turkish dotted and dotless i", () => {
    expect(foldForSearch("Ayşe Işık")).toBe("ayse isik");
    expect(foldForSearch("İlker")).toBe("ilker");
    expect(foldForSearch("  Zoë ")).toBe("zoe");
  });
});

describe("matchesPerson", () => {
  it("matches every query word anywhere in the name", () => {
    expect(matchesPerson("Bartholomew Vanderbilt-Montgomery", "vander bart")).toBe(true);
    expect(matchesPerson("Ayşe Kaya", "ayse")).toBe(true);
    expect(matchesPerson("Ayşe Kaya", "mert")).toBe(false);
  });

  it("matches everyone on an empty query", () => {
    expect(matchesPerson("Ayşe Kaya", "   ")).toBe(true);
  });
});

describe("showsAssigneeSearch", () => {
  it("shows the search only when the chips would overflow", () => {
    expect(showsAssigneeSearch(VISIBLE_ASSIGNEES)).toBe(false);
    expect(showsAssigneeSearch(VISIBLE_ASSIGNEES + 1)).toBe(true);
  });
});

describe("assigneeChoices", () => {
  const options: AssigneeOption[] = people(VISIBLE_ASSIGNEES + 4);

  it("shows the first chips and counts the rest without a query", () => {
    const result = assigneeChoices(options, [], { expanded: false, query: "" });
    expect(result.shown).toHaveLength(VISIBLE_ASSIGNEES);
    expect(result.shown[0].isMe).toBe(true);
    expect(result.hidden).toBe(4);
  });

  it("keeps a selected person visible past the first chips", () => {
    const last: AssigneeOption = options[options.length - 1];
    const result = assigneeChoices(options, [last.id], { expanded: false, query: "" });
    expect(result.shown.map((option) => option.id)).toContain(last.id);
    expect(result.hidden).toBe(3);
  });

  it("shows every match and no overflow chip with a query", () => {
    const result = assigneeChoices(options, [], { expanded: false, query: "person 1" });
    expect(result.shown.map((option) => option.name)).toEqual([
      "Person 01",
      "Person 10",
      "Person 11",
    ]);
    expect(result.hidden).toBe(0);
  });

  it("shows nothing when no one matches", () => {
    const result = assigneeChoices(options, [], { expanded: true, query: "zzz" });
    expect(result.shown).toEqual([]);
    expect(result.hidden).toBe(0);
  });
});

describe("filterConfirmState", () => {
  it("blocks the confirm only at a known count of 0", () => {
    expect(filterConfirmState(0)).toEqual({ disabled: true, noMatch: true });
    expect(filterConfirmState(null)).toEqual({ disabled: false, noMatch: false });
    expect(filterConfirmState(6)).toEqual({ disabled: false, noMatch: false });
  });
});
