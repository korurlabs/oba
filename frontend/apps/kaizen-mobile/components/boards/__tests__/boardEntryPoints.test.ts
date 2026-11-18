import { describe, expect, it } from "bun:test";

import {
  DEFAULT_BOARD_VIEW,
  EMPTY_FILTERS,
  parseBoardRoute,
  type BoardRouteState,
  type BoardView,
} from "@/components/boards/boardModel";
import { boardHref } from "@/components/boards/boardRoutes";
import { parseKaizenLink, type KaizenLinkTarget } from "@/lib/kaizenLinks";

/*
 * Each way into a board builds its href with `boardHref`; the board screen
 * reads the view back with `parseBoardRoute`. These tests make the same call
 * each entry point makes and check the view the board then shows.
 */

const HOST = "kaizen.test";

/** The view the board screen shows for an href, as it reads its params. */
function openedView(href: ReturnType<typeof boardHref>): BoardView {
  return parseBoardRoute(href.params).view;
}

/** The view a Kaizen web link opens, through the link screen's board branch. */
function linkView(url: string): BoardView {
  const target: KaizenLinkTarget | null = parseKaizenLink(url, HOST);
  if (target?.kind !== "board") throw new Error(`not a board link: ${url}`);
  return openedView(boardHref("b1", { view: target.view }));
}

describe("the default board view", () => {
  it("is All", () => {
    expect(DEFAULT_BOARD_VIEW).toBe("all");
    expect(parseBoardRoute({ boardId: "b1" }).view).toBe("all");
  });

  it("leaves the view out of a plain board href", () => {
    expect(boardHref("b1")).toEqual({
      pathname: "/boards/board/[boardId]",
      params: { boardId: "b1" },
    });
    expect(boardHref("b1", { view: "all" }).params).toEqual({ boardId: "b1" });
  });
});

describe("entry points that name no view open All", () => {
  it("a board row of the Boards tab, Pinned and Recent included", () => {
    // `useBoardLinks().openBoard(row.id)`
    expect(openedView(boardHref("b1"))).toBe("all");
  });

  it("a child board on Child boards", () => {
    // `ChildBoardsScreen`: `boardHref(row.board.id)`
    expect(openedView(boardHref("child"))).toBe("all");
  });

  it("a board just made on New board", () => {
    // `new-board.tsx`: `boardHref(created.id)`
    expect(openedView(boardHref("new"))).toBe("all");
  });

  it("a bare board link from chat, a daily update or the system", () => {
    expect(linkView(`https://${HOST}/board/alpha`)).toBe("all");
    expect(linkView(`https://${HOST}/en/board/alpha`)).toBe("all");
  });
});

describe("entry points that name a view keep it", () => {
  it("Home's sprint links open the sprint view, as the web's sprint strip", () => {
    // `useHomeLinks().openBoardPage(slug, "sprint")`
    expect(openedView(boardHref("b1", { view: "sprint" }))).toBe("sprint");
  });

  it("Home's deadlines open the roadmap, as the web's deadlines", () => {
    // `useHomeLinks().openBoardPage(slug, "roadmap")`
    expect(openedView(boardHref("b1", { view: "roadmap" }))).toBe("roadmap");
  });

  it("a link to a view's page opens that view", () => {
    expect(linkView(`https://${HOST}/en/board/alpha/all`)).toBe("all");
    expect(linkView(`https://${HOST}/en/board/alpha/sprint`)).toBe("sprint");
    expect(linkView(`https://${HOST}/en/board/alpha/backlog`)).toBe("backlog");
    expect(linkView(`https://${HOST}/en/board/alpha/roadmap`)).toBe("roadmap");
  });

  it("a link to a sprint's own page opens the sprint view", () => {
    expect(linkView(`https://${HOST}/en/board/alpha/sprint-12`)).toBe("sprint");
  });

  it("Filters hands the board back on the view it was opened from", () => {
    // `filters.tsx`: `boardHref(boardId, { ...state, filters: draft })`
    const state: BoardRouteState = { ...parseBoardRoute({ view: "backlog" }) };
    expect(openedView(boardHref("b1", { ...state, filters: EMPTY_FILTERS }))).toBe("backlog");
    expect(openedView(boardHref("b1", { ...parseBoardRoute({}), filters: EMPTY_FILTERS }))).toBe(
      "all"
    );
  });
});
