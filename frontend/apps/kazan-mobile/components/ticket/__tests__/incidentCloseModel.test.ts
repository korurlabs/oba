import { describe, expect, it } from "bun:test";

import {
  hasIncidentCloseTexts,
  incidentClosePrefill,
  incidentCloseToSend,
  needsIncidentCloseTexts,
  requiresIncidentClose,
} from "@/components/ticket/incidentCloseModel";

describe("requiresIncidentClose", () => {
  it("asks when an incident enters Done from any other status", () => {
    for (const from of ["TODO", "IN_PROGRESS", "WAITING_FOR_THIRD_PARTY", "IN_REVIEW"]) {
      expect(requiresIncidentClose("INCIDENT", from, "DONE")).toBe(true);
    }
  });

  it("asks when an incident is created as Done", () => {
    expect(requiresIncidentClose("INCIDENT", undefined, "DONE")).toBe(true);
  });

  it("does not ask for an incident that is already Done", () => {
    expect(requiresIncidentClose("INCIDENT", "DONE", "DONE")).toBe(false);
  });

  it("does not ask for a move to any status but Done", () => {
    expect(requiresIncidentClose("INCIDENT", "TODO", "IN_REVIEW")).toBe(false);
    expect(requiresIncidentClose("INCIDENT", "DONE", "IN_PROGRESS")).toBe(false);
  });

  it("does not ask for other ticket types", () => {
    for (const type of ["BUG", "FEATURE", "TASK", "TECH_DEBT", "IMPROVEMENT"]) {
      expect(requiresIncidentClose(type, "IN_REVIEW", "DONE")).toBe(false);
    }
  });
});

describe("needsIncidentCloseTexts", () => {
  it("asks when the incident lacks either text", () => {
    expect(needsIncidentCloseTexts("INCIDENT", "IN_REVIEW", "DONE", null)).toBe(true);
    expect(
      needsIncidentCloseTexts("INCIDENT", "IN_REVIEW", "DONE", {
        rca: "Disk full",
        lessonsLearned: null,
      })
    ).toBe(true);
    expect(
      needsIncidentCloseTexts("INCIDENT", "IN_REVIEW", "DONE", {
        rca: "  ",
        lessonsLearned: "Alert on 80%",
      })
    ).toBe(true);
  });

  it("closes at once when the incident holds both texts", () => {
    expect(
      needsIncidentCloseTexts("INCIDENT", "IN_REVIEW", "DONE", {
        rca: "Disk full",
        lessonsLearned: "Alert on 80%",
      })
    ).toBe(false);
  });

  it("never asks for a change the rule does not cover", () => {
    expect(needsIncidentCloseTexts("BUG", "IN_REVIEW", "DONE", null)).toBe(false);
    expect(needsIncidentCloseTexts("INCIDENT", "TODO", "IN_PROGRESS", null)).toBe(false);
  });
});

describe("hasIncidentCloseTexts", () => {
  it("needs more than white space in both", () => {
    expect(hasIncidentCloseTexts(undefined)).toBe(false);
    expect(hasIncidentCloseTexts({ rca: "x", lessonsLearned: "\n" })).toBe(false);
    expect(hasIncidentCloseTexts({ rca: "x", lessonsLearned: "y" })).toBe(true);
  });
});

describe("incidentClosePrefill", () => {
  it("starts from the stored texts", () => {
    expect(incidentClosePrefill(undefined, { rca: "Disk full", lessonsLearned: null })).toEqual({
      rca: "Disk full",
      lessonsLearned: "",
    });
  });

  it("starts empty for an incident without texts or a new one", () => {
    expect(incidentClosePrefill(undefined, undefined)).toEqual({ rca: "", lessonsLearned: "" });
  });

  it("prefers texts typed for a close that did not land", () => {
    expect(
      incidentClosePrefill(
        { rca: "Typed cause", lessonsLearned: "Typed lesson" },
        { rca: "Stored cause", lessonsLearned: "Stored lesson" }
      )
    ).toEqual({ rca: "Typed cause", lessonsLearned: "Typed lesson" });
  });
});

describe("incidentCloseToSend", () => {
  it("trims both texts", () => {
    expect(incidentCloseToSend({ rca: "  Disk full\n", lessonsLearned: " Alert " })).toEqual({
      rca: "Disk full",
      lessonsLearned: "Alert",
    });
  });

  it("holds back while either text is blank", () => {
    expect(incidentCloseToSend({ rca: "Disk full", lessonsLearned: "   " })).toBeNull();
    expect(incidentCloseToSend({ rca: "", lessonsLearned: "Alert" })).toBeNull();
  });
});
