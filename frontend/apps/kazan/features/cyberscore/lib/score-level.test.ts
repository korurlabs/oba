import { describe, expect, test } from "bun:test";
import { CyberscoreLevel, CyberscoreLevelSchema } from "../schemas";
import {
  SCORE_LEVEL_BADGE_CLASSES,
  SCORE_LEVEL_FILL_CLASSES,
  SCORE_LEVEL_STROKE_CLASSES,
  SCORE_LEVEL_TEXT_CLASSES,
  clampPercentage,
  levelFromPercentage,
} from "./score-level";

describe("levelFromPercentage", () => {
  test.each([
    [0, "CRITICAL"],
    [20, "CRITICAL"],
    [21, "HIGH"],
    [40, "HIGH"],
    [41, "MEDIUM"],
    [60, "MEDIUM"],
    [61, "LOW"],
    [80, "LOW"],
    [81, "MINIMAL"],
    [100, "MINIMAL"],
  ])("%d is %s", (percentage, expected) => {
    expect(levelFromPercentage(percentage)).toBe(expected as CyberscoreLevel);
  });

  test("never returns UNKNOWN for any whole percentage", () => {
    for (let value = 0; value <= 100; value++) {
      expect(levelFromPercentage(value)).not.toBe(CyberscoreLevel.UNKNOWN);
    }
  });

  test("rounds before it bands, like the server's whole percentage", () => {
    expect(levelFromPercentage(20.4)).toBe(CyberscoreLevel.CRITICAL);
    expect(levelFromPercentage(20.6)).toBe(CyberscoreLevel.HIGH);
  });

  test("out of range and non-finite values fall to the ends", () => {
    expect(levelFromPercentage(-5)).toBe(CyberscoreLevel.CRITICAL);
    expect(levelFromPercentage(250)).toBe(CyberscoreLevel.MINIMAL);
    expect(levelFromPercentage(Number.NaN)).toBe(CyberscoreLevel.CRITICAL);
  });
});

describe("clampPercentage", () => {
  test("clamps and rounds", () => {
    expect(clampPercentage(-1)).toBe(0);
    expect(clampPercentage(100.4)).toBe(100);
    expect(clampPercentage(101)).toBe(100);
    expect(clampPercentage(49.5)).toBe(50);
    expect(clampPercentage(Number.POSITIVE_INFINITY)).toBe(0);
  });
});

describe("level ink maps", () => {
  test.each([
    ["badge", SCORE_LEVEL_BADGE_CLASSES],
    ["text", SCORE_LEVEL_TEXT_CLASSES],
    ["stroke", SCORE_LEVEL_STROKE_CLASSES],
    ["fill", SCORE_LEVEL_FILL_CLASSES],
  ])("%s map has an ink for every level", (_name, map) => {
    for (const level of CyberscoreLevelSchema.options) {
      expect(map[level]).toBeTruthy();
    }
  });

  test("risk levels are red, amber or green and UNKNOWN is muted", () => {
    expect(SCORE_LEVEL_TEXT_CLASSES.CRITICAL).toContain("red");
    expect(SCORE_LEVEL_TEXT_CLASSES.HIGH).toContain("red");
    expect(SCORE_LEVEL_TEXT_CLASSES.MEDIUM).toContain("amber");
    expect(SCORE_LEVEL_TEXT_CLASSES.LOW).toContain("green");
    expect(SCORE_LEVEL_TEXT_CLASSES.MINIMAL).toContain("green");
    expect(SCORE_LEVEL_TEXT_CLASSES.UNKNOWN).toContain("muted");
  });
});
