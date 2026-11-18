import { describe, expect, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";

import { Violations } from "@/features/changes/components/violations";

import { StateBadge, StateNote } from "./state-badge";

const NAMES = new Map([["user-2", "Ada"]]);

describe("StateNote", () => {
  test("a draft sent back shows the reviewer and the comment", () => {
    const html = renderToStaticMarkup(
      <StateNote
        state={{
          kind: "CHANGES_REQUESTED",
          review: {
            id: "r-1",
            versionId: "d-1",
            reviewerId: "user-2",
            decision: "REJECT",
            comment: "Name the <script> path.",
            createdAt: "2026-10-20T10:00:00Z",
          },
        }}
        memberNames={NAMES}
      />
    );
    expect(html).toContain("Ada sent this change back");
    expect(html).toContain("Name the &lt;script&gt; path.");
  });

  test("a file in review links to its change", () => {
    const html = renderToStaticMarkup(
      <StateNote
        state={{
          kind: "IN_REVIEW",
          entry: {
            versionId: "v-9",
            number: 9,
            status: "IN_REVIEW",
            source: "MANUAL",
            change: "MODIFIED",
            current: false,
            proposedBy: "user-1",
            submittedAt: "2026-10-20T10:00:00Z",
            reviews: [],
          },
        }}
        memberNames={NAMES}
      />
    );
    expect(html).toContain("version 9");
    expect(html).toContain('href="/changes/v-9"');
  });

  test("the other states say nothing beyond their badge", () => {
    expect(renderToStaticMarkup(<StateNote state={{ kind: "DRAFT" }} memberNames={NAMES} />)).toBe(
      ""
    );
    expect(renderToStaticMarkup(<StateBadge state={{ kind: "INHERITED" }} />)).toContain(
      "Inherited"
    );
  });
});

describe("Violations", () => {
  test("the Checks panel gives its own title; the change detail keeps its default", () => {
    const violations = [{ rule: "PATH", path: "AGENTS.md", message: "Name the path." }];
    expect(
      renderToStaticMarkup(<Violations violations={violations} title="Your draft" />)
    ).toContain("Your draft");
    expect(renderToStaticMarkup(<Violations violations={violations} />)).toContain(
      "The change breaks a rule"
    );
  });
});
