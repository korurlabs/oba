import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import { mkdir, mkdtemp, rm, symlink, writeFile } from "fs/promises";
import { tmpdir } from "os";
import path from "path";

import { readUpload } from "./read-upload";

let root: string;
let uploads: string;

beforeEach(async () => {
  root = await mkdtemp(path.join(tmpdir(), "kazan-uploads-"));
  uploads = path.join(root, "uploads");
  await mkdir(path.join(uploads, "tickets"), { recursive: true });
  await writeFile(path.join(uploads, "tickets", "report.pdf"), "pdf-bytes");
  await writeFile(path.join(uploads, "my file.txt"), "spaced");
  await writeFile(path.join(root, "secret.txt"), "outside");
});

afterEach(async () => {
  await rm(root, { recursive: true, force: true });
});

describe("readUpload", () => {
  test("reads a file below the uploads directory", async () => {
    const result = await readUpload(uploads, ["tickets", "report.pdf"]);

    expect(result.kind).toBe("found");
    if (result.kind === "found") {
      expect(result.data.toString()).toBe("pdf-bytes");
      expect(result.fileName).toBe("report.pdf");
    }
  });

  test("decodes percent-encoded segments", async () => {
    const result = await readUpload(uploads, ["my%20file.txt"]);

    expect(result.kind).toBe("found");
  });

  test("reports a missing file as not found", async () => {
    expect((await readUpload(uploads, ["tickets", "gone.pdf"])).kind).toBe("not-found");
    expect((await readUpload(uploads, ["missing", "x.pdf"])).kind).toBe("not-found");
  });

  test("reports a directory as not found", async () => {
    expect((await readUpload(uploads, ["tickets"])).kind).toBe("not-found");
  });

  test("reports a missing uploads directory as not found", async () => {
    const result = await readUpload(path.join(root, "absent"), ["a.txt"]);

    expect(result.kind).toBe("not-found");
  });

  test.each([
    [["..", "secret.txt"]],
    [["%2e%2e", "secret.txt"]],
    [["tickets", "..", "..", "secret.txt"]],
    [["..%2Fsecret.txt"]],
    [["..%5Csecret.txt"]],
    [["%2Fetc%2Fpasswd"]],
    [["tickets", "."]],
    [[""]],
    [["report%00.pdf"]],
    [["%E0%A4%A"]],
    [[]],
  ])("rejects the traversal or malformed path %p", async (segments) => {
    const result = await readUpload(uploads, segments);

    expect(result.kind).toBe("invalid");
  });

  test("refuses a symbolic link to a file outside the uploads directory", async () => {
    await symlink(path.join(root, "secret.txt"), path.join(uploads, "link.txt"));

    const result = await readUpload(uploads, ["link.txt"]);

    expect(result.kind).toBe("invalid");
  });

  test("refuses a symbolic link even when it points inside the uploads directory", async () => {
    await symlink(path.join(uploads, "tickets", "report.pdf"), path.join(uploads, "alias.pdf"));

    const result = await readUpload(uploads, ["alias.pdf"]);

    expect(result.kind).toBe("invalid");
  });

  test("refuses a symbolic link in a directory component", async () => {
    await symlink(root, path.join(uploads, "escape"));

    const result = await readUpload(uploads, ["escape", "secret.txt"]);

    expect(result.kind).toBe("invalid");
  });

  test("serves files when the uploads directory itself is reached through a link", async () => {
    const linkedBase = path.join(root, "linked-uploads");
    await symlink(uploads, linkedBase);

    const result = await readUpload(linkedBase, ["tickets", "report.pdf"]);

    expect(result.kind).toBe("found");
  });
});
