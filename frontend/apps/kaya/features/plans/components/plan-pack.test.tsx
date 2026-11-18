import { describe, expect, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";

import { NO_PACK_TITLE, PlanPack } from "./plan-pack";

describe("PlanPack", () => {
  test("a matched pack links to its page in the pack scope", () => {
    const html = renderToStaticMarkup(<PlanPack pack={{ id: "p-kagi", name: "kagi" }} spec />);
    expect(html).toContain('href="/rules?scope=pack%3Ap-kagi"');
    expect(html).toContain(">kagi</a>");
  });

  test("a spec without a pack says why; a session plan just reads None", () => {
    expect(renderToStaticMarkup(<PlanPack pack={null} spec />)).toContain(
      `title="${NO_PACK_TITLE}"`
    );
    const session = renderToStaticMarkup(<PlanPack pack={null} spec={false} />);
    expect(session).toContain(">None</span>");
    expect(session).not.toContain("title=");
  });
});
