import { constants } from "fs";
import { open, realpath } from "fs/promises";
import path from "path";

export type UploadReadResult =
  | { kind: "found"; data: Buffer; fileName: string }
  | { kind: "invalid" }
  | { kind: "not-found" };

const NOT_FOUND_CODES = new Set(["ENOENT", "ENOTDIR"]);

function errorCode(error: unknown): string | undefined {
  if (typeof error === "object" && error !== null && "code" in error) {
    const { code } = error as { code: unknown };
    return typeof code === "string" ? code : undefined;
  }
  return undefined;
}

/**
 * Decodes the URL segments and rejects any that could leave the uploads
 * directory or name something other than a plain path component: dot
 * segments, embedded separators, NUL bytes and malformed percent escapes.
 */
function decodeSegments(segments: string[]): string[] | null {
  if (segments.length === 0) {
    return null;
  }
  const decoded: string[] = [];
  for (const segment of segments) {
    let value: string;
    try {
      value = decodeURIComponent(segment);
    } catch (error) {
      if (error instanceof URIError) {
        return null;
      }
      throw error;
    }
    if (
      value === "" ||
      value === "." ||
      value === ".." ||
      value.includes("/") ||
      value.includes("\\") ||
      value.includes("\0")
    ) {
      return null;
    }
    decoded.push(value);
  }
  return decoded;
}

function isInside(baseDir: string, candidate: string): boolean {
  const relative = path.relative(baseDir, candidate);
  return relative !== "" && !relative.startsWith("..") && !path.isAbsolute(relative);
}

/**
 * Reads one file below `baseDir`. The file is opened once and every check
 * (regular file, real location) and the read itself work on that handle, so
 * the file that was checked is the file that is served. Symbolic links are
 * refused anywhere below `baseDir`, so a link placed in the uploads directory
 * cannot expose a file outside it.
 */
export async function readUpload(baseDir: string, segments: string[]): Promise<UploadReadResult> {
  const decoded = decodeSegments(segments);
  if (decoded === null) {
    return { kind: "invalid" };
  }

  let realBase: string;
  try {
    realBase = await realpath(baseDir);
  } catch (error) {
    if (NOT_FOUND_CODES.has(errorCode(error) ?? "")) {
      return { kind: "not-found" };
    }
    throw error;
  }

  const candidate = path.resolve(realBase, ...decoded);
  if (!isInside(realBase, candidate)) {
    return { kind: "invalid" };
  }

  let handle;
  try {
    // O_NOFOLLOW makes the open fail with ELOOP when the last component is a
    // symbolic link instead of following it.
    handle = await open(candidate, constants.O_RDONLY | constants.O_NOFOLLOW);
  } catch (error) {
    const code = errorCode(error);
    if (code === "ELOOP") {
      return { kind: "invalid" };
    }
    if (NOT_FOUND_CODES.has(code ?? "")) {
      return { kind: "not-found" };
    }
    throw error;
  }

  try {
    // A symbolic link in a directory component is still followed by open, so
    // the opened path must resolve to itself to prove no link was crossed.
    let resolved: string;
    try {
      resolved = await realpath(candidate);
    } catch (error) {
      // The file was removed or replaced after it was opened.
      if (NOT_FOUND_CODES.has(errorCode(error) ?? "")) {
        return { kind: "not-found" };
      }
      throw error;
    }
    if (resolved !== candidate) {
      return { kind: "invalid" };
    }
    const stats = await handle.stat();
    if (!stats.isFile()) {
      return { kind: "not-found" };
    }
    const data = await handle.readFile();
    return { kind: "found", data, fileName: path.basename(candidate) };
  } finally {
    await handle.close();
  }
}
