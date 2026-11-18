import { describe, expect, test } from "bun:test";

const { NAV_ITEMS } = await import("./kaya-sidebar");

describe("the menu", () => {
  test("Plans sits at /plans right after Sessions", () => {
    const labels = NAV_ITEMS.map((item) => item.label);
    const plans = labels.indexOf("Plans");
    expect(plans).toBe(labels.indexOf("Sessions") + 1);
    expect(NAV_ITEMS[plans]?.href).toBe("/plans");
  });

  test("Lessons sits at /lessons in Activity, right after Plans", () => {
    const labels = NAV_ITEMS.map((item) => item.label);
    const lessons = labels.indexOf("Lessons");
    expect(lessons).toBe(labels.indexOf("Plans") + 1);
    expect(NAV_ITEMS[lessons]?.href).toBe("/lessons");
    expect(NAV_ITEMS[lessons]?.section).toBe("Activity");
  });

  test("the menu has no Branch runs item", () => {
    const labels = NAV_ITEMS.map((item) => item.label);
    expect(labels).not.toContain("Branch runs");
    expect(NAV_ITEMS.map((item) => item.href)).not.toContain("/branch-runs");
  });

  test("Accounts sits at /accounts in Activity, right after Lessons", () => {
    const labels = NAV_ITEMS.map((item) => item.label);
    const accounts = labels.indexOf("Accounts");
    expect(accounts).toBe(labels.indexOf("Lessons") + 1);
    expect(NAV_ITEMS[accounts]?.href).toBe("/accounts");
    expect(NAV_ITEMS[accounts]?.section).toBe("Activity");
  });

  test("each section lists its pages in order", () => {
    const labelsOf = (section: string) =>
      NAV_ITEMS.filter((item) => item.section === section).map((item) => item.label);
    expect(labelsOf("Activity")).toEqual(["Machines", "Sessions", "Plans", "Lessons", "Accounts"]);
    expect(labelsOf("Configuration")).toEqual([
      "Packs",
      "Skills",
      "Allowlist",
      "Rules",
      "Conventions",
      "Guardrails",
      "Path mapping",
      "Changes",
    ]);
  });

  test("Packs and Guardrails link their own pages with icons apart from Allowlist's", () => {
    const byLabel = (label: string) => NAV_ITEMS.find((item) => item.label === label);
    expect(byLabel("Packs")?.href).toBe("/packs");
    expect(byLabel("Guardrails")?.href).toBe("/guardrails");
    expect(byLabel("Guardrails")?.icon).not.toBe(byLabel("Allowlist")?.icon);
  });

  test("Path mapping sits at /path-mapping in Configuration, right after Guardrails", () => {
    const labels = NAV_ITEMS.map((item) => item.label);
    const mapping = labels.indexOf("Path mapping");
    expect(mapping).toBe(labels.indexOf("Guardrails") + 1);
    expect(NAV_ITEMS[mapping]?.href).toBe("/path-mapping");
    expect(NAV_ITEMS[mapping]?.section).toBe("Configuration");
  });
});
