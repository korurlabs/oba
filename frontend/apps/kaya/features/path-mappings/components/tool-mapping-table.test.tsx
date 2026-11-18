import { describe, expect, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";

import { TOOL_MAPPINGS, withStates } from "../schemas/tool-mappings.fixture";
import { ToolMappingTable } from "./tool-mapping-table";

const CLAUDE = TOOL_MAPPINGS[0]!;

/** The rows of the first table, which holds what the scope delivers. */
function readRows(html: string): string[] {
  const table = html.slice(html.indexOf("<tbody"), html.indexOf("</table>"));
  return table
    .split("<tr")
    .slice(1)
    .filter((row: string) => !row.includes('scope="rowgroup"'));
}

function groupHeadings(html: string): string[] {
  return [...html.matchAll(/<th[^>]*scope="rowgroup"[^>]*><span[^>]*>([^<]*)</g)].map(
    (match) => match[1] ?? ""
  );
}

describe("ToolMappingTable", () => {
  test("has the three columns and groups the reads under At start, When used and When referenced", () => {
    const html = renderToStaticMarkup(<ToolMappingTable reads={CLAUDE.reads} scope={null} />);
    const head = html.slice(html.indexOf("<thead"), html.indexOf("</thead>"));
    expect(head).toContain(">Path</th>");
    expect(head).toContain(">Delivered from</th>");
    expect(head).toContain("In scope");
    expect(head.match(/<th /g)).toHaveLength(3);
    expect(head).not.toContain("Loads");
    expect(head).not.toContain("Through");
    expect(groupHeadings(html)).toEqual(["At start", "When used", "When referenced"]);
    expect(html).toContain('aria-label="What does When referenced mean?"');
    expect(html.match(/<tbody/g)).toHaveLength(3);
  });

  test("lists the tool's reads in the server's order, each path in code type with its info tip", () => {
    const html = renderToStaticMarkup(<ToolMappingTable reads={CLAUDE.reads} scope={null} />);
    const rows = readRows(html);
    expect(rows).toHaveLength(CLAUDE.reads.length);
    expect(rows[0]).toContain(">AGENTS.md</code>");
    expect(rows[1]).toContain(".claude/rules/&lt;path&gt;.md</code>");
    expect(rows[1]).toContain('aria-label="About .claude/rules/&lt;path&gt;.md"');
    expect(rows[1]).not.toContain("a rule with paths in its frontmatter");
    expect(rows[2]).not.toContain('aria-label="About');
  });

  test("the source links to its page with the kind beneath, and the link chain is not a column", () => {
    const html = renderToStaticMarkup(
      <ToolMappingTable reads={CLAUDE.reads} scope="repository:r-1" />
    );
    const rules = readRows(html)[1] ?? "";
    expect(rules).toContain('href="/rules?scope=repository%3Ar-1&amp;tab=rules"');
    expect(rules).toContain(".agents/rules/&lt;path&gt;.md</code></a>");
    expect(rules).toContain(">Rule</span>");
    expect(rules).toMatch(/<a [^>]*class="underline underline-offset-2"/);
    expect(rules).not.toContain("../.agents/rules");
    expect(html).not.toContain("Read directly");
  });

  test("a regular root AGENTS.md in state DIRECT shows the file itself as its source", () => {
    const counted = withStates(CLAUDE, [{ state: "DIRECT", count: 1 }]);
    const html = renderToStaticMarkup(
      <ToolMappingTable reads={counted.reads} scope="repository:r-1" />
    );
    const root = readRows(html)[0] ?? "";
    expect(root).not.toContain(".agents/AGENTS.md");
    expect(root).toContain('href="/rules?scope=repository%3Ar-1&amp;tab=agents-md"');
    expect(root).toContain('aria-label="AGENTS.md, edited on Rules: AGENTS.md"');
    expect(root).toContain(">1 file</span>");
    expect(root).toContain(">Direct</span>");
  });

  test("without a scope every read shows a quiet dash and nothing is set aside", () => {
    const html = renderToStaticMarkup(<ToolMappingTable reads={CLAUDE.reads} scope={null} />);
    for (const row of readRows(html)) {
      expect(row).toContain('<span class="text-muted-foreground">-</span></td>');
    }
    expect(html).not.toContain("not delivered by this scope");
  });

  test("a delivered read shows its count and state badge; a read with nothing moves to a closed group", () => {
    const counted = withStates(CLAUDE, [
      { state: "LINKED", count: 1 },
      { state: "LINK_MISSING", count: 12 },
      { state: "DIRECT", count: 1 },
      { state: "NOT_DELIVERED", count: 0 },
    ]);
    const html = renderToStaticMarkup(
      <ToolMappingTable reads={counted.reads} scope="repository:r-1" />
    );
    const rows = readRows(html);
    expect(rows).toHaveLength(CLAUDE.reads.length - 1);
    expect(rows[0]).toContain(">Linked</span>");
    expect(rows[0]).toContain(">1 file</span>");
    expect(rows[1]).toContain(">Link missing</span>");
    expect(rows[1]).toContain(">12 files</span>");
    expect(rows[2]).toContain(">Direct</span>");
    expect(rows[3]).toContain(".claude/agents/&lt;path&gt;.md");
    expect(html).toContain("1 not delivered by this scope");
    expect(html).toContain('aria-expanded="false"');
    expect(html).not.toContain(".claude/skills/&lt;name&gt;/SKILL.md");
    expect(html).not.toContain("None in scope");
    expect(html).not.toContain("0 files");
  });

  test.each([["a pack", "pack:p-1"]])(
    "in %s scope a link-missing read comes from the repository, not a gap",
    (_, scope) => {
      const counted = withStates(CLAUDE, [
        { state: "LINKED", count: 1 },
        { state: "LINK_MISSING", count: 12 },
      ]);
      const html = renderToStaticMarkup(<ToolMappingTable reads={counted.reads} scope={scope} />);
      const rules = readRows(html)[1] ?? "";
      expect(rules).toContain(">From the repository</span>");
      expect(rules).toContain(">12 files</span>");
      expect(rules).not.toContain("Link missing");
      expect(readRows(html)[0]).toContain(">Linked</span>");
    }
  );
});
