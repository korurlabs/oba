import { describe, expect, it } from "bun:test";

import {
  cappedRoleRead,
  destructiveIndices,
  disabledIndices,
  orderActions,
  roleMenuLacksRole,
  roleMenuVisible,
  selectedLabel,
  type RoleReadState,
} from "@/components/kazan/actionSheetModel";

const noop = () => {};

describe("orderActions", () => {
  it("moves destructive actions last and keeps the rest in order", () => {
    const ordered = orderActions([
      { label: "Delete", destructive: true, onPress: noop },
      { label: "Edit", onPress: noop },
      { label: "Archive", destructive: true, onPress: noop },
      { label: "Copy link", onPress: noop },
    ]);
    expect(ordered.map((action) => action.label)).toEqual([
      "Edit",
      "Copy link",
      "Delete",
      "Archive",
    ]);
  });

  it("never mutates the input", () => {
    const actions = [
      { label: "Delete", destructive: true, onPress: noop },
      { label: "Edit", onPress: noop },
    ];
    orderActions(actions);
    expect(actions[0].label).toBe("Delete");
  });
});

describe("indices", () => {
  it("finds the destructive and disabled positions", () => {
    const actions = [
      { label: "Edit", disabled: true },
      { label: "Move" },
      { label: "Delete", destructive: true },
    ];
    expect(destructiveIndices(actions)).toEqual([2]);
    expect(disabledIndices(actions)).toEqual([0]);
  });
});

describe("selectedLabel", () => {
  it("names the current choice", () => {
    expect(
      selectedLabel([
        { label: "English", onPress: noop },
        { label: "Nederlands", selected: true, onPress: noop },
      ])
    ).toBe("Nederlands");
  });

  it("is null for a menu without a current choice", () => {
    expect(selectedLabel([{ label: "Edit", onPress: noop }])).toBeNull();
  });
});

describe("roleMenuVisible", () => {
  const loading = { isPending: true, isStalled: false };
  const settled = { isPending: false, isStalled: false };
  const stalled = { isPending: true, isStalled: true };

  it("stays closed while nothing asks for the menu", () => {
    expect(roleMenuVisible(false, false, settled)).toBe(false);
    expect(roleMenuVisible(false, true, settled)).toBe(false);
  });

  it("waits while the role is being read, so no role action is missing on open", () => {
    expect(roleMenuVisible(true, false, loading)).toBe(false);
  });

  it("opens once the read has settled, with or without a role", () => {
    expect(roleMenuVisible(true, false, settled)).toBe(true);
  });

  it("opens without waiting for a read that is offline or retrying", () => {
    expect(roleMenuVisible(true, false, stalled)).toBe(true);
  });

  it("stays open when a stalled read resumes loading", () => {
    expect(roleMenuVisible(true, true, loading)).toBe(true);
  });
});

describe("roleMenuLacksRole", () => {
  const read = (patch: Partial<RoleReadState>): RoleReadState => ({
    role: null,
    isPending: false,
    isStalled: false,
    error: null,
    ...patch,
  });

  it("is false for a settled read, member or not", () => {
    expect(roleMenuLacksRole(read({ role: "ADMIN" }))).toBe(false);
    expect(roleMenuLacksRole(read({}))).toBe(false);
  });

  it("is true when the read failed or stalls without a role", () => {
    expect(roleMenuLacksRole(read({ error: new Error("boom") }))).toBe(true);
    expect(roleMenuLacksRole(read({ isPending: true, isStalled: true }))).toBe(true);
  });

  it("is false while the read is plainly loading", () => {
    expect(roleMenuLacksRole(read({ isPending: true }))).toBe(false);
  });

  it("is false when a failed refresh still holds the earlier role", () => {
    expect(roleMenuLacksRole(read({ role: "OWNER", error: new Error("boom") }))).toBe(false);
  });
});

describe("cappedRoleRead", () => {
  const read = (patch: Partial<RoleReadState>): RoleReadState => ({
    role: null,
    isPending: true,
    isStalled: false,
    error: null,
    ...patch,
  });

  it("keeps a slow read loading while the menu has not waited long", () => {
    const capped = cappedRoleRead(read({}), false);
    expect(capped.isStalled).toBe(false);
    expect(roleMenuVisible(true, false, capped)).toBe(false);
  });

  it("opens the menu without the role once a slow read passes the wait cap", () => {
    const capped = cappedRoleRead(read({}), true);
    expect(capped.isStalled).toBe(true);
    expect(roleMenuVisible(true, false, capped)).toBe(true);
    expect(roleMenuLacksRole(capped)).toBe(true);
  });

  it("leaves a settled read alone after the cap", () => {
    const capped = cappedRoleRead(read({ isPending: false, role: "MEMBER" }), true);
    expect(capped.isStalled).toBe(false);
    expect(roleMenuLacksRole(capped)).toBe(false);
  });

  it("keeps the role of a read that settled right at the cap", () => {
    const capped = cappedRoleRead(read({ isPending: false, role: "ADMIN" }), true);
    expect(roleMenuVisible(true, false, capped)).toBe(true);
    expect(roleMenuLacksRole(capped)).toBe(false);
  });

  it("keeps the other fields of the read", () => {
    const refetch = () => {};
    const capped = cappedRoleRead({ ...read({}), refetch }, true);
    expect(capped.refetch).toBe(refetch);
  });
});
