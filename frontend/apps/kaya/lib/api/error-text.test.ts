import { describe, expect, test } from "bun:test";

import { describeError } from "./error-text";
import { KayaApiError } from "./errors";

describe("describeError", () => {
  test("the retired git email refusal falls back to the server's message", () => {
    const error = new KayaApiError("Server words", 403, "KAY_SEC_007", {
      gitAuthorEmail: "ada@example.com",
    });
    expect(describeError(error)).toBe("Server words");
  });

  test("adds the field messages of a validation refusal", () => {
    const error = new KayaApiError("x", 422, "GEN_VAL_001", { reason: "must be 10 to 1000" });
    expect(describeError(error)).toBe("Kaya refused the values entered. must be 10 to 1000");
  });

  test("falls back to the server's message for an unknown code", () => {
    expect(describeError(new KayaApiError("Kaya answered 500", 500, null))).toBe(
      "Kaya answered 500"
    );
    expect(describeError(new Error("boom"))).toBe("boom");
  });
});

describe("describeError, every refusal a person can cause", () => {
  const cases: ReadonlyArray<readonly [string, number, string]> = [
    ["GEN_SEC_003", 403, "Your role does not allow this."],
    ["GEN_SEC_002", 403, "You no longer have access to this organization."],
    ["ORG_SEC_002", 403, "You no longer have access to this organization."],
    ["ORG_SEC_003", 403, "Your role does not allow this."],
    ["ORG_SEC_005", 403, "This organization is disabled."],
    ["KAY_CFG_008", 409, "A pack with this name already exists in the organization."],
    ["KAY_CFG_010", 409, "A pack with this name already exists in the organization."],
    ["KAY_CFG_032", 409, "The parent changed again. Review its current version first."],
    ["KAY_CFG_033", 409, "The pack already exists with another parent."],
    ["KAY_CFG_035", 409, "This pack is already used, so its name can no longer change."],
    ["KAY_NF_001", 404, "It no longer exists in this organization."],
    ["KAY_VAL_006", 409, "This user already has an active machine with that name."],
    ["KAY_VAL_011", 409, "The machine is retired. Register it again to get a new token."],
  ];

  for (const [code, status, text] of cases) {
    test(`${code} reads as plain words`, () => {
      expect(describeError(new KayaApiError("server text", status, code))).toBe(text);
    });
  }

  test("the retired merge codes fall back to the server's message", () => {
    expect(describeError(new KayaApiError("Server words", 409, "KAY_VAL_007"))).toBe(
      "Server words"
    );
    expect(describeError(new KayaApiError("Server words", 409, "KAY_VAL_010"))).toBe(
      "Server words"
    );
  });

  test("a validation refusal without field messages gives the general sentence alone", () => {
    expect(describeError(new KayaApiError("x", 422, "GEN_VAL_001", { max: 5 }))).toBe(
      "Kaya refused the values entered."
    );
  });

  test("a GEN_SEC_003 naming a required role is a role refusal, one naming only the user is self-only", () => {
    expect(
      describeError(
        new KayaApiError("x", 403, "GEN_SEC_003", { requiredRole: "ADMIN", userId: "u" })
      )
    ).toBe("Your role does not allow this.");
    expect(
      describeError(new KayaApiError("x", 403, "GEN_SEC_003", { machineId: "g", userId: "u" }))
    ).toBe("Only the user themselves can do this.");
  });

  test("a refused configuration lists each violation, with a text for the symlink and case rules", () => {
    const error = new KayaApiError("x", 422, "KAY_CFG_001", {
      violations: [
        { path: ".agents/hooks/pre-bash.sh", rule: "HOOK_SYMLINK", message: "server words" },
        {
          path: "CLAUDE.md",
          rule: "FILE_TOO_LARGE",
          message: "The file has 9 bytes; the limit is 8",
        },
        { path: null, rule: "PACK_DUPLICATE", message: "" },
      ],
    });
    expect(describeError(error)).toBe(
      "Kaya refused the configuration. .agents/hooks/pre-bash.sh: A hook must be a regular file, so that every change to the code it runs needs an owner's approval. CLAUDE.md: The file has 9 bytes; the limit is 8. PACK_DUPLICATE."
    );
    for (const rule of [
      "SYMLINK_CONTENT_REQUIRED",
      "SYMLINK_TARGET_NOT_ALLOWED",
      "PATH_THROUGH_SYMLINK",
      "PATH_CASE_CONFLICT",
    ]) {
      const text = describeError(
        new KayaApiError("x", 409, "KAY_CFG_003", {
          violations: [{ path: "p", rule, message: "m" }],
        })
      );
      expect(text.startsWith("Two packs give the same path. p: ")).toBe(true);
      expect(text.endsWith(": m.")).toBe(false);
    }
  });

  test("KAY_CFG_026 appends the reason the server gives for the mount folder", () => {
    const general =
      "The mount folder is not a valid repository folder, or it lies inside or around another mount's folder.";
    expect(describeError(new KayaApiError("x", 422, "KAY_CFG_026"))).toBe(general);
    expect(describeError(new KayaApiError("x", 422, "KAY_CFG_026", { rootPath: "" }))).toBe(
      general
    );
    expect(describeError(new KayaApiError("x", 422, "KAY_CFG_026", { rootPath: 7 }))).toBe(general);
    expect(
      describeError(
        new KayaApiError("x", 422, "KAY_CFG_026", { rootPath: "apps/web is inside apps" })
      )
    ).toBe(`${general} apps/web is inside apps.`);
    expect(
      describeError(
        new KayaApiError("x", 422, "KAY_CFG_026", { rootPath: "Use a shorter folder." })
      )
    ).toBe(`${general} Use a shorter folder.`);
  });

  test("KAY_CFG_034 lists each rule that does not stay under the mount folder, like KAY_CFG_001", () => {
    const general =
      "A rule of a pack mounted in a folder needs paths frontmatter whose every pattern lies under that folder.";
    expect(describeError(new KayaApiError("x", 422, "KAY_CFG_034"))).toBe(general);
    expect(
      describeError(
        new KayaApiError("x", 422, "KAY_CFG_034", {
          violations: [
            {
              path: ".agents/rules/kagi/api.md",
              rule: "MOUNT_RULE_PATHS_OUTSIDE",
              message: "pattern **/*.java spans folders",
            },
            "not a violation",
            { path: null, rule: "MOUNT_RULE_NO_PATHS", message: "" },
          ],
        })
      )
    ).toBe(
      `${general} .agents/rules/kagi/api.md: pattern **/*.java spans folders. MOUNT_RULE_NO_PATHS.`
    );
  });

  test("KAY_CFG_031 lists the locked items a child pack tried to override", () => {
    const text = describeError(
      new KayaApiError("x", 409, "KAY_CFG_031", {
        violations: [{ path: ".agents/rules/core.md", rule: "ITEM_LOCKED", message: "locked" }],
      })
    );
    expect(text).toBe(
      "The item is locked by a required pack or is a guardrail, so a child pack cannot override it. .agents/rules/core.md: locked."
    );
  });

  test("the seed refusals say what is missing or too large", () => {
    expect(
      describeError(new KayaApiError("x", 422, "KAY_CFG_009", { missing: ["a".repeat(64)] }))
    ).toBe("Some content the seed names was not uploaded. Run the seed again. 1 file is missing.");
    expect(describeError(new KayaApiError("x", 422, "KAY_CFG_009"))).toBe(
      "Some content the seed names was not uploaded. Run the seed again."
    );
    expect(
      describeError(
        new KayaApiError("x", 413, "KAY_CFG_011", { limitFiles: 20000, fileCount: 20001 })
      )
    ).toBe(
      "The seed names more files or more content than one run takes. It names 20001 files; the limit is 20000."
    );
    expect(
      describeError(
        new KayaApiError("x", 413, "KAY_CFG_011", { limitBytes: 67108864, contentBytes: 67108865 })
      )
    ).toBe(
      "The seed names more files or more content than one run takes. It holds 67108865 bytes of content; the limit is 67108864."
    );
  });

  test("a non-Error value is shown as text", () => {
    expect(describeError("plain")).toBe("plain");
  });
});

describe("describeError, config changes and reviews", () => {
  const cases: ReadonlyArray<readonly [string, number, string]> = [
    ["KAY_CFG_014", 409, "This change is no longer open"],
    ["KAY_CFG_015", 409, "Another change of this pack is already in review"],
    ["KAY_CFG_016", 409, "Nothing would change"],
    ["KAY_CFG_017", 409, "This change is no longer in review"],
    ["KAY_CFG_018", 409, "Only a released version, approved or superseded, can be restored."],
    ["KAY_CFG_019", 422, "A proposal changes the files of one pack only"],
    ["KAY_CFG_020", 409, "This change was reviewed, so it is kept with its reviews"],
    ["KAY_CFG_004", 403, "You may not review this change."],
  ];

  test.each(cases)("%s", (code, status, text) => {
    expect(describeError(new KayaApiError("server text", status, code))).toStartWith(text);
  });

  test("a rebase refusal without conflicts asks for a rebase", () => {
    const error = new KayaApiError("x", 409, "KAY_CFG_002", {
      currentVersionId: "v-9",
      conflicts: [],
    });
    expect(describeError(error)).toBe(
      "The pack has a newer version than this change is based on, so the change must be rebased first."
    );
  });

  test("the rebase conflict report names each path and both sides", () => {
    const error = new KayaApiError("x", 409, "KAY_CFG_002", {
      currentVersionId: "v-9",
      conflicts: [
        { path: ".agents/rules/core.md", draftChange: "MODIFIED", currentChange: "DELETED" },
        { path: ".claude/settings.json", draftChange: "ADDED", currentChange: "ADDED" },
        { path: "AGENTS.md", draftChange: null, currentChange: "MODIFIED" },
        { draftChange: "MODIFIED" },
      ],
    });
    const text = describeError(error);
    expect(text).toContain("These paths changed on both sides;");
    expect(text).toContain(
      ".agents/rules/core.md: your edits changed it, the current version deleted it."
    );
    expect(text).toContain(
      ".claude/settings.json: your edits added it, the current version added it."
    );
    expect(text).toContain(
      "AGENTS.md: your edits leave it as delivered, the current version changed it."
    );
  });

  test("a review refusal says whether the proposer or the role stopped it", () => {
    const refusal = (values: Record<string, unknown>) =>
      describeError(new KayaApiError("x", 403, "KAY_CFG_004", { versionId: "v-1", ...values }));
    expect(refusal({ reason: "PROPOSER", requiredRole: "ADMIN" })).toBe(
      "You proposed this change, so another admin or owner must review it."
    );
    expect(refusal({ reason: "ROLE", requiredRole: "OWNER" })).toContain(
      "only an owner approves it"
    );
    expect(refusal({ reason: "ROLE", requiredRole: "ADMIN" })).toBe(
      "Only an admin or owner reviews changes."
    );
  });

  test("a proposal over several packs lists the paths of each", () => {
    const error = new KayaApiError("x", 422, "KAY_CFG_019", {
      owners: { "k-1": [".agents/rules/a.md", ".agents/rules/b.md"], "p-1": ["AGENTS.md"] },
    });
    expect(describeError(error)).toEndWith(
      "Propose each group on its own: .agents/rules/a.md, .agents/rules/b.md; AGENTS.md."
    );
  });

  test("a write to another member's draft names the proposer rule", () => {
    const error = new KayaApiError("x", 403, "GEN_SEC_002", {
      versionId: "d-1",
      proposedBy: "user-ada",
    });
    expect(describeError(error)).toBe("Only the member who started this change can edit it.");
  });
});
