import { describe, expect, test } from "bun:test";

import type {
  Owner,
  PackRepositoryResponse,
  RepositoryPackEntryResponse,
} from "@/features/scope/schemas";
import type { RepositoryResponse } from "@/features/sessions/schemas";
import type { VersionResponse } from "@/features/versions/schemas";
import { KayaApiError } from "@/lib/api/errors";

import {
  attachRows,
  attachableRepositories,
  removeRows,
  renameFailure,
  shownPackId,
  versionRows,
} from "./pack-data";

function entry(id: string, rootPath: string | null, required = false): RepositoryPackEntryResponse {
  return { id, name: id, required, rootPath, createdAt: "2026-10-28T00:00:00Z" };
}

function owner(id: string, name: string, parentPackId: string | null = null): Owner {
  return {
    id,
    name,
    parentPackId,
    currentVersionId: null,
    required: false,
    mountPaths: [],
    repositoryIds: [],
  };
}

function repository(id: string, name: string, status = "ACTIVE"): RepositoryResponse {
  return { id, name, remoteUrl: `git@x:${name}.git`, status };
}

function composing(id: string): PackRepositoryResponse {
  return { id, name: id, remoteUrl: "", status: "ACTIVE", rootPath: null, latestReleaseNumber: 1 };
}

function version(
  id: string,
  number: number,
  status: VersionResponse["status"],
  proposedBy: string
): VersionResponse {
  return {
    id,
    packId: "p-1",
    number,
    status,
    proposedBy,
    source: "MANUAL",
    executableChange: false,
    createdAt: `2026-10-2${number}T10:00:00Z`,
  };
}

describe("the pack the page shows", () => {
  test("the URL's pack wins, even an unknown one", () => {
    expect(shownPackId("p-x", [owner("p-a", "a")])).toBe("p-x");
  });

  test("without one, the first root pack by name", () => {
    expect(shownPackId(null, [owner("p-b", "beta"), owner("p-a", "alpha")])).toBe("p-a");
  });

  test("null when there is no pack", () => {
    expect(shownPackId(null, [])).toBeNull();
  });
});

describe("attaching and removing a pack on a repository's list", () => {
  const entries = [
    entry("guard", null, true),
    entry("village", null),
    entry("kagi", "projects/kagi"),
  ];

  test("attach appends the pack with its folder and drops the required packs", () => {
    expect(attachRows(entries, "docs", " tools/docs ")).toEqual([
      { key: "village", packId: "village", folder: "" },
      { key: "kagi", packId: "kagi", folder: "projects/kagi" },
      { key: "attach-docs", packId: "docs", folder: "tools/docs" },
    ]);
  });

  test("attach at the root keeps an empty folder", () => {
    expect(attachRows([], "docs", "")).toEqual([
      { key: "attach-docs", packId: "docs", folder: "" },
    ]);
  });

  test("attach of a pack the list holds leaves the list unchanged", () => {
    expect(attachRows(entries, "kagi", "other")).toEqual([
      { key: "village", packId: "village", folder: "" },
      { key: "kagi", packId: "kagi", folder: "projects/kagi" },
    ]);
  });

  test("remove keeps the other own packs in order with their folders", () => {
    expect(removeRows(entries, "village")).toEqual([
      { key: "kagi", packId: "kagi", folder: "projects/kagi" },
    ]);
  });

  test("remove of a pack the list does not hold changes nothing", () => {
    expect(removeRows(entries, "docs").map((row) => row.packId)).toEqual(["village", "kagi"]);
  });
});

describe("repositories offered for attach", () => {
  test("leaves out archived ones and those composing the pack, by name", () => {
    const repositories = [
      repository("r-3", "zeta"),
      repository("r-1", "alpha"),
      repository("r-2", "beta", "ARCHIVED"),
      repository("r-4", "gamma"),
    ];
    expect(
      attachableRepositories(repositories, [composing("r-4")]).map((entry) => entry.id)
    ).toEqual(["r-1", "r-3"]);
  });
});

describe("version rows", () => {
  test("keep the server order, label the status and name the proposer when known", () => {
    const rows = versionRows(
      [version("v-3", 3, "DRAFT", "u-1"), version("v-2", 2, "APPROVED", "u-gone")],
      new Map([["u-1", "Ada"]])
    );
    expect(rows).toEqual([
      {
        id: "v-3",
        number: 3,
        statusLabel: "Open",
        statusVariant: "muted",
        createdAt: "2026-10-23T10:00:00Z",
        by: "Ada",
      },
      {
        id: "v-2",
        number: 2,
        statusLabel: "Approved",
        statusVariant: "success",
        createdAt: "2026-10-22T10:00:00Z",
        by: "u-gone",
      },
    ]);
  });

  test("every status has a label", () => {
    const labels = versionRows(
      [
        version("a", 1, "IN_REVIEW", "u"),
        version("b", 2, "SUPERSEDED", "u"),
        version("c", 3, "REJECTED", "u"),
      ],
      new Map()
    ).map((row) => row.statusLabel);
    expect(labels).toEqual(["In review", "Superseded", "Rejected"]);
  });
});

describe("renameFailure", () => {
  test("a pack in use reads as plain words and carries the tip", () => {
    const error = new KayaApiError("server text", 409, "KAY_CFG_035", {
      packId: "p1",
      condition: "COMPOSED",
    });
    expect(renameFailure(error)).toEqual({
      message: "This pack is already used, so its name can no longer change.",
      fixed: true,
    });
  });

  test("a taken name says so without the tip", () => {
    expect(renameFailure(new KayaApiError("server text", 409, "KAY_CFG_008"))).toEqual({
      message: "That name is already taken.",
      fixed: false,
    });
  });

  test("another 409 keeps its own text and gets no tip", () => {
    expect(renameFailure(new KayaApiError("Something else", 409, "KAY_CFG_999"))).toEqual({
      message: "Something else",
      fixed: false,
    });
  });

  test("other refusals go through describeError", () => {
    expect(renameFailure(new KayaApiError("server text", 404, "KAY_NF_001"))).toEqual({
      message: "It no longer exists in this organization.",
      fixed: false,
    });
    expect(renameFailure(new Error("boom"))).toEqual({ message: "boom", fixed: false });
  });
});
