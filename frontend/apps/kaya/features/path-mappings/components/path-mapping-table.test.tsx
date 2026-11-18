import { describe, expect, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";

import type { PathMappingResponse } from "../schemas";
import { PATH_MAPPING_ROWS } from "../schemas/path-mapping-rows.fixture";
import { PathMappingTable } from "./path-mapping-table";

function bodyRows(html: string): string[] {
  const body = html.slice(html.indexOf("<tbody"));
  return body.split("<tr").slice(1);
}

function headers(html: string): string[] {
  return [...html.matchAll(/<th [^>]*>(.*?)<\/th>/g)].map((match) => match[1] ?? "");
}

function withCounts(counts: Record<number, number>, others: number | null): PathMappingResponse[] {
  return PATH_MAPPING_ROWS.map((row: PathMappingResponse, index: number) => ({
    ...row,
    count: counts[index] ?? others,
  }));
}

describe("PathMappingTable", () => {
  test("has the three columns Path, Delivered from and In scope", () => {
    const html = renderToStaticMarkup(<PathMappingTable rows={PATH_MAPPING_ROWS} scope={null} />);
    const heads = headers(html);
    expect(heads).toHaveLength(3);
    expect(heads[0]).toBe("Path");
    expect(heads[1]).toBe("Delivered from");
    expect(heads[2]).toContain("In scope");
    expect(heads[2]).toContain('aria-label="What does In scope show?"');
    expect(html).not.toContain("In a mounted pack");
    expect(html).not.toContain("Owner review");
  });

  test("without a scope lists the 21 rows in the server's order, each pattern in code type", () => {
    const html = renderToStaticMarkup(<PathMappingTable rows={PATH_MAPPING_ROWS} scope={null} />);
    const rows = bodyRows(html);
    expect(rows).toHaveLength(21);
    expect(rows[0]).toContain(">AGENTS.md</code>");
    expect(rows[13]).toContain("Skill: script");
    expect(rows[20]).toContain(".claude/templates</code>");
    expect(html).not.toContain("not delivered by this scope");
  });

  test("every row has an info tip on its path, the note no longer shows as a sub-line", () => {
    const html = renderToStaticMarkup(<PathMappingTable rows={PATH_MAPPING_ROWS} scope={null} />);
    const rows = bodyRows(html);
    expect(rows[0]).toContain('aria-label="About AGENTS.md"');
    expect(rows[13]).toContain(
      'aria-label="About .agents/skills/&lt;name&gt;/scripts/&lt;path&gt;"'
    );
    expect(html).not.toContain("also any file of the skill with the executable bit");
  });

  test("the tool links show their kind and no page", () => {
    const rows = withCounts({ 19: 2 }, null);
    const html = renderToStaticMarkup(<PathMappingTable rows={rows} scope="repository:r-1" />);
    const links = bodyRows(html).slice(18, 21);
    expect(links.map((row: string) => row.includes(">Tool link</span>"))).toEqual([
      true,
      true,
      true,
    ]);
    expect(links[1]).toContain(".claude/rules</code>");
    expect(links[1]).not.toContain("<a ");
    expect(links[1]).toContain(">2 files</span>");
  });

  test("a null count reads -, and the page link still carries the row's tab", () => {
    const html = renderToStaticMarkup(<PathMappingTable rows={PATH_MAPPING_ROWS} scope={null} />);
    const hook = bodyRows(html)[8] ?? "";
    expect(hook).toContain('<span class="text-muted-foreground">-</span>');
    expect(hook).toContain('href="/rules?tab=hooks"');
    expect(hook).toContain(">Rules: Hooks</a>");
    expect(hook).toContain(">Hook</span>");
  });

  test("a count reads as files and links to its page with the scope and the row's tab", () => {
    const rows = withCounts({ 8: 3 }, null);
    const html = renderToStaticMarkup(<PathMappingTable rows={rows} scope="repository:r-1" />);
    const hook = bodyRows(html)[8] ?? "";
    expect(hook).toContain('href="/rules?scope=repository%3Ar-1&amp;tab=hooks"');
    expect(hook).toMatch(/aria-label="3 Hook files on Rules: Hooks"[^>]*>3 files<\/a>/);
    const links = hook.match(/<a [^>]*>/g) ?? [];
    expect(links).toHaveLength(2);
    for (const link of links) {
      expect(link).toContain('class="underline underline-offset-2"');
    }
  });

  test("rows with no file in the scope move into a closed group, without 0 files", () => {
    const rows = withCounts({ 8: 3, 3: 22 }, 0);
    const html = renderToStaticMarkup(<PathMappingTable rows={rows} scope="repository:r-1" />);
    const body = html.slice(0, html.indexOf("</table>"));
    expect(bodyRows(body)).toHaveLength(2);
    expect(bodyRows(body)[0]).toContain(">22 files</a>");
    expect(html).toContain('aria-expanded="false"');
    expect(html).toContain("19 not delivered by this scope");
    expect(html.match(/<table/g)).toHaveLength(1);
    expect(html).not.toContain("0 files");
  });

  test("the root AGENTS.md stays in view when the scope delivers it as the tool link", () => {
    const rows = withCounts({ 18: 1 }, 0);
    const html = renderToStaticMarkup(<PathMappingTable rows={rows} scope="repository:r-1" />);
    const delivered = bodyRows(html.slice(0, html.indexOf("</table>")));
    expect(delivered).toHaveLength(2);
    expect(delivered[0]).toContain("AGENTS.md: repository root");
    expect(delivered[1]).toContain(">1 file</span>");
    expect(html).toContain("19 not delivered by this scope");
  });

  test("a scope that delivers nothing says so above the closed group", () => {
    const html = renderToStaticMarkup(
      <PathMappingTable rows={withCounts({}, 0)} scope="repository:r-1" />
    );
    expect(html).toContain("This scope delivers none of these paths.");
    expect(html).toContain("21 not delivered by this scope");
    expect(html).not.toContain("<table");
  });
});
