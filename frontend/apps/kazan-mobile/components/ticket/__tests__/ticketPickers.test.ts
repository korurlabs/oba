import { describe, expect, it } from "bun:test";

import { TICKET_TYPES } from "@/components/kazan/ticketMetaModel";
import {
  choiceToApply,
  disabledReasons,
  priorityPicker,
  rankPicker,
  statusPicker,
  typePicker,
  type ChoicePicker,
} from "@/components/ticket/ticketPickers";
import type { TranslationKey } from "@/i18n";

const t = (key: TranslationKey): string => key;
const ignore = (): void => undefined;

describe("statusPicker", () => {
  it("offers every status as pickable on a ticket whose status can change", () => {
    const picker: ChoicePicker = statusPicker(t, "IN_PROGRESS", ignore);
    expect(picker.kind).toBe("status");
    expect(picker.current).toBe("IN_PROGRESS");
    expect(picker.options.every((option) => !option.disabled)).toBe(true);
    expect(disabledReasons(picker.options)).toEqual([]);
  });

  it("leaves only Todo pickable when locked, with the backlog reason once", () => {
    const picker: ChoicePicker = statusPicker(t, "TODO", ignore, true);
    const enabled: string[] = picker.options
      .filter((option) => !option.disabled)
      .map((option) => option.value);
    expect(enabled).toEqual(["TODO"]);
    expect(picker.options.length).toBeGreaterThan(1);
    expect(disabledReasons(picker.options)).toEqual(["ticketStatusBacklogTodo"]);
  });
});

describe("priorityPicker", () => {
  it("lists P1 to P3 for an incident", () => {
    const picker: ChoicePicker = priorityPicker(t, "INCIDENT", "P2", ignore);
    expect(picker.kind).toBe("priority");
    expect(picker.options.map((option) => option.value)).toEqual(["P1", "P2", "P3"]);
  });

  it("lists HIGHEST to LOWEST for every other type", () => {
    const picker: ChoicePicker = priorityPicker(t, "BUG", null, ignore);
    expect(picker.current).toBeNull();
    expect(picker.options[0]?.value).toBe("HIGHEST");
    expect(picker.options.at(-1)?.value).toBe("LOWEST");
  });
});

describe("rankPicker", () => {
  const withCount = (key: TranslationKey, options?: Record<string, unknown>): string =>
    options ? `${key}:${String(options.count)}` : key;

  it("lists every rank, smallest first, with its effort and points", () => {
    const picker: ChoicePicker = rankPicker(withCount, "M", ignore);
    expect(picker.kind).toBe("rank");
    expect(picker.current).toBe("M");
    expect(picker.options.map((option) => option.value)).toEqual([
      "XS",
      "S",
      "M",
      "L",
      "XL",
      "XXL",
    ]);
    expect(picker.options[0]).toEqual({
      value: "XS",
      detail: "homeInfoRankXs",
      trailing: "homePoints:1",
    });
    expect(picker.options.at(-1)?.trailing).toBe("homePoints:13");
  });

  it("checks no rank on an unranked ticket and offers no way to clear one", () => {
    const picker: ChoicePicker = rankPicker(withCount, null, ignore);
    expect(picker.current).toBeNull();
    expect(choiceToApply(picker, "S")).toBe("S");
    expect(picker.options).toHaveLength(6);
  });
});

describe("typePicker", () => {
  it("lists every type with the current one", () => {
    const picker: ChoicePicker = typePicker(t, "FEATURE", ignore);
    expect(picker.kind).toBe("type");
    expect(picker.current).toBe("FEATURE");
    expect(picker.options.map((option) => option.value)).toEqual([...TICKET_TYPES]);
  });
});

describe("choiceToApply", () => {
  const locked: ChoicePicker = statusPicker(t, "TODO", ignore, true);
  const open: ChoicePicker = statusPicker(t, "TODO", ignore);

  it("hands over an enabled value that differs from the current one", () => {
    expect(choiceToApply(open, "DONE")).toBe("DONE");
  });

  it("only closes on the current value", () => {
    expect(choiceToApply(open, "TODO")).toBeNull();
  });

  it("only closes on a disabled value", () => {
    expect(choiceToApply(locked, "DONE")).toBeNull();
  });

  it("only closes on a value the picker does not list", () => {
    expect(choiceToApply(open, "ARCHIVED")).toBeNull();
  });
});

describe("disabledReasons", () => {
  it("keeps each reason once, in list order, and skips enabled options", () => {
    expect(
      disabledReasons([
        { value: "a", disabled: true, reason: "first" },
        { value: "b", reason: "unused" },
        { value: "c", disabled: true, reason: "second" },
        { value: "d", disabled: true, reason: "first" },
        { value: "e", disabled: true },
      ])
    ).toEqual(["first", "second"]);
  });
});
