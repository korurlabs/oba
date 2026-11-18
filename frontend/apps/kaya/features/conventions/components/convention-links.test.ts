import { describe, expect, test } from "bun:test";

import {
  conventionDetailHref,
  conventionNewHref,
  conventionsListHref,
  legacyConventionRedirect,
} from "./convention-links";

const PATH = "docs/private/conventions/backend/03-service-cqrs-convention.md";
const CURRENT = new URLSearchParams(
  "scope=pack%3Ap-1&release=4&draft=d-1&inherited=true&file=x&at=v-2&view=split&panel=links&compare=v-3&folder=y"
);
const KEPT = "scope=pack%3Ap-1&release=4&inherited=true";

describe("convention addresses", () => {
  test("a detail address encodes the path as one segment and keeps scope, release and inherited, and drops a draft", () => {
    expect(conventionDetailHref(PATH, CURRENT)).toBe(
      `/conventions/${encodeURIComponent(PATH)}?${KEPT}`
    );
  });

  test("a detail address drops file, at, view, panel, compare and folder", () => {
    const href = conventionDetailHref(PATH, CURRENT);
    for (const dropped of ["file=", "at=", "view=", "panel=", "compare=", "folder="]) {
      expect(href).not.toContain(dropped);
    }
  });

  test("the list keeps the scope only, and is bare without one", () => {
    expect(conventionsListHref(CURRENT)).toBe(`/conventions?${KEPT}`);
    expect(conventionsListHref(new URLSearchParams())).toBe("/conventions");
  });

  test("the open folders travel to a doc and back, and the way back opens the doc's folder", () => {
    const open = new URLSearchParams("scope=s&open=security");
    expect(conventionDetailHref(PATH, open)).toBe(
      `/conventions/${encodeURIComponent(PATH)}?scope=s&open=security`
    );
    expect(conventionsListHref(open, "docs/private/conventions/backend/api/")).toBe(
      `/conventions?scope=s&open=${encodeURIComponent("backend,backend/api,security")}`
    );
  });

  test("a new doc starts in the folder it names, else with no folder", () => {
    expect(conventionNewHref(CURRENT)).toBe(`/conventions/new?${KEPT}`);
    expect(conventionNewHref(new URLSearchParams(), "docs/private/conventions/backend/")).toBe(
      `/conventions/new?folder=${encodeURIComponent("docs/private/conventions/backend/")}`
    );
  });
});

describe("legacyConventionRedirect", () => {
  test("a link that names a doc leads to its page with every other parameter kept", () => {
    expect(
      legacyConventionRedirect(new URLSearchParams(`scope=r%3A1&file=${PATH}&panel=links`))
    ).toBe(`/conventions/${encodeURIComponent(PATH)}?scope=r%3A1&panel=links`);
    expect(legacyConventionRedirect(new URLSearchParams(`file=${PATH}`))).toBe(
      `/conventions/${encodeURIComponent(PATH)}`
    );
  });

  test("a link without a doc stays on the list", () => {
    expect(legacyConventionRedirect(new URLSearchParams("scope=r%3A1"))).toBeNull();
    expect(legacyConventionRedirect(new URLSearchParams("file="))).toBeNull();
  });
});
