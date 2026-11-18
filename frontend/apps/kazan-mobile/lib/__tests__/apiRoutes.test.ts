import { describe, expect, it } from "bun:test";

import { API_ROUTES } from "@/lib/apiRoutes";

describe("API_ROUTES", () => {
  it("encodes every path parameter", () => {
    expect(API_ROUTES.push.device("a/b")).toBe("/push/devices/a%2Fb");
    expect(API_ROUTES.tickets.byKey("KZN-1 x")).toBe("/kazan/tickets/by-key/KZN-1%20x");
    expect(API_ROUTES.tickets.byId("b1", "t?1")).toBe("/kazan/boards/b1/tickets/t%3F1");
    expect(API_ROUTES.pullRequests.githubPullRequest("r1", 42)).toBe(
      "/kazan/github/repositories/r1/pull-requests/42"
    );
    expect(API_ROUTES.dailyUpdates.nudge("u1")).toBe("/kazan/updates/missing/u1/nudge");
    expect(API_ROUTES.boards.members("b/1")).toBe("/kazan/boards/b%2F1/members");
    expect(API_ROUTES.boards.backlog("b 1")).toBe("/kazan/boards/b%201/backlog");
    expect(API_ROUTES.boards.roadmap("b1")).toBe("/kazan/boards/b1/roadmap");
    expect(API_ROUTES.boards.labels("b1")).toBe("/kazan/boards/b1/labels");
    expect(API_ROUTES.boards.projects("b1")).toBe("/kazan/boards/b1/projects");
    expect(API_ROUTES.boards.milestones("b?1")).toBe("/kazan/boards/b%3F1/milestones");
    expect(API_ROUTES.storage.attachment("a/1")).toBe("/storage/attachments/a%2F1");
  });

  it("builds the storage paths the web app calls", () => {
    expect(API_ROUTES.storage.attachments).toBe("/storage/attachments");
    expect(API_ROUTES.storage.attachmentsExists).toBe("/storage/attachments/exists");
  });

  it("builds the board settings paths the web app calls", () => {
    expect(API_ROUTES.boards.memberCandidates("b/1")).toBe(
      "/kazan/boards/b%2F1/members/candidates"
    );
    expect(API_ROUTES.boards.member("b1", "u/1")).toBe("/kazan/boards/b1/members/u%2F1");
    expect(API_ROUTES.boards.children("b1")).toBe("/kazan/boards/b1/children");
    expect(API_ROUTES.boards.label("b1", "l1")).toBe("/kazan/boards/b1/labels/l1");
    expect(API_ROUTES.boards.project("b1", "p 1")).toBe("/kazan/boards/b1/projects/p%201");
    expect(API_ROUTES.boards.milestone("b1", "m1")).toBe("/kazan/boards/b1/milestones/m1");
    expect(API_ROUTES.boards.milestoneClose("b1", "m1")).toBe(
      "/kazan/boards/b1/milestones/m1/close"
    );
    expect(API_ROUTES.boards.milestoneReopen("b1", "m1")).toBe(
      "/kazan/boards/b1/milestones/m1/reopen"
    );
  });
});
