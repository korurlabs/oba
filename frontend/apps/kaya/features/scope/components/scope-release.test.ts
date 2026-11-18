import { describe, expect, test } from "bun:test";

import type { RepositoryResponse } from "@/features/sessions/schemas";

import {
  releaseHref,
  releaseToShow,
  requestedRelease,
  resolveScope,
  scopeOptions,
} from "./scope-data";

function repository(id: string, name: string, status: string): RepositoryResponse {
  return { id, name, remoteUrl: `git@example.com:${id}.git`, status };
}

const options = scopeOptions(
  [repository("active", "Alpha", "ACTIVE"), repository("archived", "Beta", "ARCHIVED")],
  []
);

/** The release the page shows for a URL, resolved as the scope hook does. */
function releaseForUrl(value: string | null, release: number | null): number | null {
  return requestedRelease(value, resolveScope(value, options), release);
}

describe("release of a repository scope", () => {
  test("the URL's release wins over the newest", () => {
    expect(releaseToShow(3, 9)).toBe(3);
  });

  test("no release, or one that is not a positive whole number, shows the newest", () => {
    expect(releaseToShow(null, 9)).toBe(9);
    expect(releaseToShow(0, 9)).toBe(9);
    expect(releaseToShow(-2, 9)).toBe(9);
    expect(releaseToShow(null, 0)).toBe(0);
  });

  test("a session links one release in the Rules view", () => {
    expect(releaseHref("repo-1", 7)).toBe("/rules?scope=repository%3Arepo-1&release=7");
  });

  test("the URL's release applies to the repository the URL named", () => {
    expect(releaseForUrl("repository:active", 4)).toBe(4);
  });

  test("a release for an archived repository is ignored, not shown for the fallback", () => {
    expect(resolveScope("repository:archived", options)).toEqual({
      kind: "REPOSITORY",
      repositoryId: "active",
    });
    expect(releaseForUrl("repository:archived", 4)).toBeNull();
  });

  test("a release for an unknown repository, or with no scope in the URL, is ignored", () => {
    expect(releaseForUrl("repository:missing", 4)).toBeNull();
    expect(releaseForUrl(null, 4)).toBeNull();
  });

  test("a release is never applied to a pack, nor when it is not a positive whole number", () => {
    expect(requestedRelease("pack:p", { kind: "PACK", packId: "p" }, 4)).toBeNull();
    expect(releaseForUrl("repository:active", 0)).toBeNull();
    expect(releaseForUrl("repository:active", null)).toBeNull();
  });
});
