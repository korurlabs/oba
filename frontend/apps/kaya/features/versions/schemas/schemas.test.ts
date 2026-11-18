import { describe, expect, test } from "bun:test";

import {
  DRAFT_LIMITS,
  FileChangeSchema,
  draftCreateRequestSchema,
  draftFileSaveRequestSchema,
  draftRebaseRequestSchema,
  fileDiffResponseSchema,
  rebaseConflictSchema,
  utf8Bytes,
  versionDetailResponseSchema,
  versionFileContentResponseSchema,
} from ".";

const VERSION = {
  id: "v-1",
  packId: "k-1",
  number: 2,
  status: "IN_REVIEW",
  baseVersionId: null,
  proposedBy: "u-1",
  source: "CLI",
  reason: null,
  executableChange: false,
  manifestSha256: null,
  submittedAt: null,
  decidedAt: null,
  createdAt: "2026-10-23T09:00:00Z",
  updatedAt: null,
  files: [
    { path: "AGENTS.md", sha256: "a".repeat(64), mode: "FILE", size: 4, symlinkTarget: null },
  ],
};

describe("versions and files", () => {
  test("every version status and source the server sends is known", () => {
    for (const status of ["DRAFT", "IN_REVIEW", "APPROVED", "REJECTED", "SUPERSEDED"]) {
      expect(versionDetailResponseSchema.safeParse({ ...VERSION, status }).success).toBe(true);
    }
    for (const source of ["MANUAL", "CLI", "SEED", "ROLLBACK"]) {
      expect(versionDetailResponseSchema.safeParse({ ...VERSION, source }).success).toBe(true);
    }
    expect(versionDetailResponseSchema.safeParse({ ...VERSION, source: "WEB" }).success).toBe(
      false
    );
  });

  test("the retired AGENT source is refused", () => {
    expect(versionDetailResponseSchema.safeParse({ ...VERSION, source: "AGENT" }).success).toBe(
      false
    );
  });

  test("reads a version with a file, an executable, a symlink and a mounted override", () => {
    const parsed = versionDetailResponseSchema.safeParse({
      ...VERSION,
      files: [
        ...VERSION.files,
        { path: ".agents/hooks/pre-x.sh", sha256: "c".repeat(64), mode: "EXECUTABLE", size: 9 },
        { path: ".claude/skills/x", mode: "SYMLINK", symlinkTarget: "../../.agents/skills/x" },
        { path: "kagi/AGENTS.md", mode: "FILE", overridesPath: "AGENTS.md" },
      ],
    });
    expect(parsed.success).toBe(true);
  });

  test("refuses a file mode the server does not send", () => {
    const files = [{ path: "AGENTS.md", mode: "DIRECTORY" }];
    expect(versionDetailResponseSchema.safeParse({ ...VERSION, files }).success).toBe(false);
  });

  test("a file content is text, base64, or null for a symlink", () => {
    const base = { versionId: "v-1", path: "AGENTS.md", mode: "FILE" };
    expect(
      versionFileContentResponseSchema.safeParse({
        ...base,
        encoding: "UTF_8",
        content: "hi",
        size: 2,
      }).success
    ).toBe(true);
    expect(
      versionFileContentResponseSchema.safeParse({ ...base, encoding: "BASE64", content: "aGk=" })
        .success
    ).toBe(true);
    expect(
      versionFileContentResponseSchema.safeParse({
        ...base,
        mode: "SYMLINK",
        symlinkTarget: "../x",
        encoding: null,
        content: null,
      }).success
    ).toBe(true);
    expect(
      versionFileContentResponseSchema.safeParse({ ...base, encoding: "LATIN_1" }).success
    ).toBe(false);
  });
});

describe("enums and conflicts as the backend sends them", () => {
  test("the file changes are the backend's values", () => {
    expect(FileChangeSchema.options).toEqual(["ADDED", "MODIFIED", "DELETED"]);
  });

  test("a conflict has a path and nullable sides", () => {
    expect(
      rebaseConflictSchema.safeParse({
        path: "AGENTS.md",
        draftChange: null,
        currentChange: "DELETED",
      }).success
    ).toBe(true);
    expect(rebaseConflictSchema.safeParse({ draftChange: "ADDED" }).success).toBe(false);
  });
});

describe("a draft is created and saved", () => {
  test("a draft is of a pack", () => {
    expect(draftCreateRequestSchema.safeParse({ packId: "k-1" }).success).toBe(true);
    expect(draftCreateRequestSchema.safeParse({}).success).toBe(false);
    expect(draftCreateRequestSchema.safeParse({ packId: "" }).success).toBe(false);
  });

  test("base64 content is limited by its length, UTF-8 content by its bytes", () => {
    const path = ".agents/rules/a.md";
    expect(
      draftFileSaveRequestSchema.safeParse({
        path,
        encoding: "BASE64",
        content: "A".repeat(DRAFT_LIMITS.contentCharsMax),
      }).success
    ).toBe(true);
    expect(
      draftFileSaveRequestSchema.safeParse({
        path,
        encoding: "BASE64",
        content: "A".repeat(DRAFT_LIMITS.contentCharsMax + 1),
      }).success
    ).toBe(false);
    expect(
      draftFileSaveRequestSchema.safeParse({
        path,
        content: "a".repeat(DRAFT_LIMITS.contentBytesMax),
      }).success
    ).toBe(true);
  });

  test("an executable file needs content like a plain one", () => {
    expect(
      draftFileSaveRequestSchema.safeParse({ path: ".agents/hooks/x.sh", executable: true }).success
    ).toBe(false);
    expect(
      draftFileSaveRequestSchema.safeParse({
        path: ".agents/hooks/x.sh",
        executable: true,
        content: "#!/bin/sh\n",
      }).success
    ).toBe(true);
  });
});

describe("draft file save", () => {
  test("a file needs content and sends no mode", () => {
    expect(draftFileSaveRequestSchema.safeParse({ path: "AGENTS.md" }).success).toBe(false);
    expect(draftFileSaveRequestSchema.safeParse({ path: "AGENTS.md", content: "" }).success).toBe(
      true
    );
    const parsed = draftFileSaveRequestSchema.parse({
      path: ".agents/hooks/guard.sh",
      content: "#!/bin/sh",
      executable: true,
      mode: "SYMLINK",
    });
    expect(parsed).toEqual({
      path: ".agents/hooks/guard.sh",
      content: "#!/bin/sh",
      executable: true,
    });
  });

  test("UTF-8 content is limited by its bytes, not its characters", () => {
    const content = "é".repeat(DRAFT_LIMITS.contentBytesMax / 2 + 1);
    expect(utf8Bytes(content)).toBeGreaterThan(DRAFT_LIMITS.contentBytesMax);
    expect(draftFileSaveRequestSchema.safeParse({ path: "a.md", content }).success).toBe(false);
  });

  test("an empty or too long path is refused", () => {
    expect(draftFileSaveRequestSchema.safeParse({ path: " ", content: "x" }).success).toBe(false);
    expect(
      draftFileSaveRequestSchema.safeParse({
        path: "a".repeat(DRAFT_LIMITS.pathMax + 1),
        content: "x",
      }).success
    ).toBe(false);
  });
});

describe("rebase", () => {
  test("a path is resolved one way only", () => {
    expect(draftRebaseRequestSchema.safeParse({}).success).toBe(true);
    expect(
      draftRebaseRequestSchema.safeParse({ keepDraft: ["a"], takeCurrent: ["b"] }).success
    ).toBe(true);
    expect(
      draftRebaseRequestSchema.safeParse({ keepDraft: ["a"], takeCurrent: ["a"] }).success
    ).toBe(false);
  });
});

describe("responses as built", () => {
  test("a diff entry of a symlink has no line diff", () => {
    const parsed = fileDiffResponseSchema.parse({
      path: "CLAUDE.md",
      change: "ADDED",
      oldMode: null,
      newMode: "SYMLINK",
      oldSha256: null,
      newSha256: null,
      oldSymlinkTarget: null,
      newSymlinkTarget: "AGENTS.md",
      binary: false,
      unifiedDiff: null,
    });
    expect(parsed.newSymlinkTarget).toBe("AGENTS.md");
  });
});
