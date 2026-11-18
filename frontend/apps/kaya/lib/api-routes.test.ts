import { describe, expect, test } from "bun:test";

import { API_ROUTES } from "./api-routes";

describe("versioned config routes", () => {
  test("packs, effective items, pack lists and releases", () => {
    expect(API_ROUTES.packs.list).toBe("/kaya/packs");
    expect(API_ROUTES.packs.effective("p-1")).toBe("/kaya/packs/p-1/effective");
    expect(API_ROUTES.repositoryPacks.list("r-1")).toBe("/kaya/repositories/r-1/packs");
    expect(API_ROUTES.releases.list("r-1")).toBe("/kaya/repositories/r-1/releases");
    expect(API_ROUTES.releases.detail("r-1", 12)).toBe("/kaya/repositories/r-1/releases/12");
  });

  test("versions, drafts and changes", () => {
    expect(API_ROUTES.versions.detail("v-1")).toBe("/kaya/versions/v-1");
    expect(API_ROUTES.versions.file("v-1")).toBe("/kaya/versions/v-1/file");
    expect(API_ROUTES.versions.diff("v-1")).toBe("/kaya/versions/v-1/diff");
    expect(API_ROUTES.versions.restore("v-1")).toBe("/kaya/versions/v-1/restore");
    expect(API_ROUTES.drafts.create).toBe("/kaya/drafts");
    expect(API_ROUTES.drafts.detail("d-1")).toBe("/kaya/drafts/d-1");
    expect(API_ROUTES.drafts.files("d-1")).toBe("/kaya/drafts/d-1/files");
    expect(API_ROUTES.drafts.rebase("d-1")).toBe("/kaya/drafts/d-1/rebase");
    expect(API_ROUTES.drafts.overrides("d-1")).toBe("/kaya/drafts/d-1/overrides");
    expect(API_ROUTES.changes.list).toBe("/kaya/changes");
    expect(API_ROUTES.changes.detail("v-1")).toBe("/kaya/changes/v-1");
    expect(API_ROUTES.changes.submit("v-1")).toBe("/kaya/changes/v-1/submit");
    expect(API_ROUTES.changes.reviews("v-1")).toBe("/kaya/changes/v-1/reviews");
    expect(API_ROUTES.changes.history).toBe("/kaya/changes/history");
  });

  test("kind lists and links", () => {
    expect(API_ROUTES.rules.list).toBe("/kaya/rules");
    expect(API_ROUTES.agentsFiles.list).toBe("/kaya/agents-files");
    expect(API_ROUTES.roles.list).toBe("/kaya/roles");
    expect(API_ROUTES.subagents.list).toBe("/kaya/subagents");
    expect(API_ROUTES.workflows.list).toBe("/kaya/workflows");
    expect(API_ROUTES.templates.list).toBe("/kaya/templates");
    expect(API_ROUTES.hooks.list).toBe("/kaya/hooks");
    expect(API_ROUTES.conventions.list).toBe("/kaya/conventions");
    expect(API_ROUTES.skills.list).toBe("/kaya/skills");
    expect(API_ROUTES.settings.list).toBe("/kaya/settings");
    expect(API_ROUTES.pathMappings.list).toBe("/kaya/path-mappings");
    expect(API_ROUTES.pathMappings.tools).toBe("/kaya/path-mappings/tools");
    expect(API_ROUTES.links.list).toBe("/kaya/links");
    expect(API_ROUTES.links.dependencies).toBe("/kaya/links/dependencies");
  });

  test("workflow detail", () => {
    expect(API_ROUTES.workflows.detail("w-1")).toBe("/kaya/workflows/w-1");
  });

  test("an id is encoded so it cannot add a path segment", () => {
    expect(API_ROUTES.versions.detail("../x")).toBe("/kaya/versions/..%2Fx");
    expect(API_ROUTES.releases.list("a/b")).toBe("/kaya/repositories/a%2Fb/releases");
    expect(API_ROUTES.drafts.detail("a?b")).toBe("/kaya/drafts/a%3Fb");
  });
});

describe("plan routes", () => {
  test("list, filters, detail, versions, diff and feedback", () => {
    expect(API_ROUTES.plans.list).toBe("/kaya/plans");
    expect(API_ROUTES.plans.filters).toBe("/kaya/plans/filters");
    expect(API_ROUTES.plans.detail("p-1")).toBe("/kaya/plans/p-1");
    expect(API_ROUTES.plans.version("p-1", 2)).toBe("/kaya/plans/p-1/versions/2");
    expect(API_ROUTES.plans.diff("p-1", 2)).toBe("/kaya/plans/p-1/versions/2/diff");
    expect(API_ROUTES.plans.feedback("p-1")).toBe("/kaya/plans/p-1/feedback");
    expect(API_ROUTES.plans.feedbackItem("p-1", "f-1")).toBe("/kaya/plans/p-1/feedback/f-1");
  });

  test("an id is encoded so it cannot add a path segment", () => {
    expect(API_ROUTES.plans.detail("../x")).toBe("/kaya/plans/..%2Fx");
    expect(API_ROUTES.plans.feedbackItem("a/b", "c?d")).toBe("/kaya/plans/a%2Fb/feedback/c%3Fd");
  });
});

describe("platform routes", () => {
  test("the user and the organization's members keep the platform paths", () => {
    expect(API_ROUTES.users.me).toBe("/users/me");
    expect(API_ROUTES.organizations.members("o-1")).toBe("/organizations/o-1/members");
    expect(API_ROUTES.organizations.members("a/b")).toBe("/organizations/a%2Fb/members");
  });
});
