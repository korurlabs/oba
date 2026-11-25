import { describe, expect, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";
import { NextIntlClientProvider } from "next-intl";
import en from "@/locales/en.json";
import type { Cyberscore } from "../schemas";
import { CyberscoreGauge } from "./cyberscore-gauge";

function markup(score: Cyberscore): string {
  return renderToStaticMarkup(
    <NextIntlClientProvider locale="en" messages={en} timeZone="UTC">
      <CyberscoreGauge score={score} />
    </NextIntlClientProvider>
  );
}

const unscored: Cyberscore = {
  scorePercentage: 0,
  scoreLevel: "UNKNOWN",
  breakdownByCategory: {},
  scorableCheckCount: 0,
  lastCalculatedAt: null,
};

describe("CyberscoreGauge", () => {
  test("with data prints the percentage, the level name and an arc", () => {
    const html = markup({
      scorePercentage: 72.4,
      scoreLevel: "LOW",
      breakdownByCategory: { IDENTITY: 72 },
      scorableCheckCount: 3,
      lastCalculatedAt: new Date("2026-11-25T08:30:00Z"),
    } as unknown as Cyberscore);
    expect(html).toContain("72%");
    expect(html).toContain(en.cyberscore.levels.LOW);
    expect(html).toContain('stroke-dasharray="72 100"');
    expect(html).toContain("stroke-green-500");
    expect(html).not.toContain(en.cyberscore.gauge.notCalculated);
  });

  test("without data prints 0%, the unknown level, no value arc and the not-calculated note", () => {
    const html = markup(unscored);
    expect(html).toContain("0%");
    expect(html).toContain(en.cyberscore.levels.UNKNOWN);
    expect(html).not.toContain("stroke-dasharray");
    expect(html).toContain(en.cyberscore.gauge.notCalculated);
  });

  test("clamps a percentage above 100", () => {
    const html = markup({ ...unscored, scorePercentage: 140, scoreLevel: "MINIMAL" });
    expect(html).toContain("100%");
    expect(html).toContain('stroke-dasharray="100 100"');
  });
});
