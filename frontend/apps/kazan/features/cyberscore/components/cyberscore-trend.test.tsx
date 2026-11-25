import { describe, expect, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";
import { NextIntlClientProvider } from "next-intl";
import en from "@/locales/en.json";
import type { CyberscoreTrendPoint } from "../schemas";
import { CyberscoreTrend } from "./cyberscore-trend";

function markup(points: CyberscoreTrendPoint[]): string {
  return renderToStaticMarkup(
    <NextIntlClientProvider locale="en" messages={en} timeZone="UTC">
      <CyberscoreTrend points={points} />
    </NextIntlClientProvider>
  );
}

describe("CyberscoreTrend", () => {
  test("with no points shows the empty message and no chart", () => {
    const html = markup([]);
    expect(html).toContain(en.cyberscore.trend.empty);
    expect(html).not.toContain("<svg");
  });

  test("with one point still shows the empty message", () => {
    const html = markup([{ date: "2026-11-24", scorePercentage: 40, scoreLevel: "HIGH" }]);
    expect(html).toContain(en.cyberscore.trend.empty);
    expect(html).not.toContain("<polyline");
  });

  test("with data draws the line and a table row for every day", () => {
    const html = markup([
      { date: "2026-11-23", scorePercentage: 40, scoreLevel: "HIGH" },
      { date: "2026-11-24", scorePercentage: 62, scoreLevel: "LOW" },
      { date: "2026-11-25", scorePercentage: 85, scoreLevel: "MINIMAL" },
    ]);
    expect(html).toContain("<polyline");
    expect(html).toContain(en.cyberscore.trend.tableCaption);
    expect(html).toContain("<td>40%</td>");
    expect(html).toContain("<td>62%</td>");
    expect(html).toContain("<td>85%</td>");
    expect(html).toContain(en.cyberscore.levels.MINIMAL);
    expect(html).not.toContain(en.cyberscore.trend.empty);
  });
});
