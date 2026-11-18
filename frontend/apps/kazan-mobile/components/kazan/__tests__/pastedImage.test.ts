import { beforeEach, describe, expect, it, mock } from "bun:test";

/*
 * expo-file-system, expo-image and expo-crypto are native modules; the
 * fakes record what the helpers copy and write and report fixed sizes.
 */

const CACHE_URI = "file:///cache";

const copies: { from: string; to: string }[] = [];
const writes: { uri: string; content: string; encoding: string | undefined }[] = [];
const sizes: Map<string, number> = new Map();
const failingCopies: Set<string> = new Set();
let dimensions: { width: number; height: number } | Error = { width: 640, height: 480 };
let uuids: string[] = [];

class FakeFile {
  readonly uri: string;

  constructor(...parts: (string | { uri: string })[]) {
    this.uri = parts
      .map((part: string | { uri: string }) => (typeof part === "string" ? part : part.uri))
      .join("/");
  }

  get name(): string {
    return this.uri.slice(this.uri.lastIndexOf("/") + 1);
  }

  get size(): number {
    return sizes.get(this.uri) ?? 0;
  }

  copy(target: FakeFile): void {
    if (failingCopies.has(this.uri)) throw new Error(`cannot copy ${this.uri}`);
    copies.push({ from: this.uri, to: target.uri });
    sizes.set(target.uri, sizes.get(this.uri) ?? 0);
  }

  write(content: string, options?: { encoding?: string }): void {
    writes.push({ uri: this.uri, content, encoding: options?.encoding });
    sizes.set(this.uri, content.length);
  }
}

mock.module("expo-file-system", () => ({
  File: FakeFile,
  Directory: class {},
  Paths: { cache: { uri: CACHE_URI } },
}));

mock.module("expo-image", () => ({
  Image: {
    loadAsync: async () => {
      if (dimensions instanceof Error) throw dimensions;
      return dimensions;
    },
  },
}));

mock.module("expo-crypto", () => ({ randomUUID: () => uuids.shift() ?? "uuid-x" }));

const {
  parseBase64Image,
  pastedImageExtension,
  pastedImageFile,
  pastedImageFiles,
  pastedImageFromBase64,
  pastedImageName,
  pastedImageType,
} = await import("@/components/kazan/pastedImage");

beforeEach(() => {
  copies.length = 0;
  writes.length = 0;
  sizes.clear();
  failingCopies.clear();
  dimensions = { width: 640, height: 480 };
  uuids = ["uuid-1", "uuid-2", "uuid-3"];
});

describe("pasted image names and types", () => {
  it("takes the extension from the last path segment, lower case, without a query", () => {
    expect(pastedImageExtension("file:///tmp/ABC.PNG")).toBe("png");
    expect(pastedImageExtension("file:///tmp/a.b/photo.jpg?x=1#y")).toBe("jpg");
    expect(pastedImageExtension("file:///tmp/noextension")).toBe("");
    expect(pastedImageExtension("file:///tmp/odd.p n g")).toBe("");
  });

  it("maps each extension the paste writes to its media type", () => {
    expect(pastedImageType("png")).toBe("image/png");
    expect(pastedImageType("jpg")).toBe("image/jpeg");
    expect(pastedImageType("JPEG")).toBe("image/jpeg");
    expect(pastedImageType("gif")).toBe("image/gif");
    expect(pastedImageType("webp")).toBe("image/webp");
    expect(pastedImageType("heic")).toBe("image/heic");
    expect(pastedImageType("tiff")).toBe("image/tiff");
    expect(pastedImageType("exe")).toBeNull();
    expect(pastedImageType("")).toBeNull();
  });

  it("names a pasted file pasted-{key}.{ext}", () => {
    expect(pastedImageName("uuid-1", "png")).toBe("pasted-uuid-1.png");
    expect(pastedImageName("uuid-1", "")).toBe("pasted-uuid-1");
  });

  it("splits a data URI into its type and bare base64", () => {
    expect(parseBase64Image("data:image/jpeg;base64,QUJD", null)).toEqual({
      mimeType: "image/jpeg",
      base64: "QUJD",
    });
    expect(parseBase64Image("data:image/PNG;charset=x;base64,QUJD", "image/jpeg")).toEqual({
      mimeType: "image/png",
      base64: "QUJD",
    });
    expect(parseBase64Image("QUJD", "image/PNG")).toEqual({
      mimeType: "image/png",
      base64: "QUJD",
    });
    expect(parseBase64Image("QUJD", null)).toEqual({ mimeType: null, base64: "QUJD" });
  });
});

describe("pastedImageFile", () => {
  it("copies the pasted file into the cache under its own name and describes it", async () => {
    sizes.set("file:///tmp/1D2E.png", 2048);

    const file = await pastedImageFile("file:///tmp/1D2E.png");

    expect(copies).toEqual([
      { from: "file:///tmp/1D2E.png", to: `${CACHE_URI}/pasted-uuid-1.png` },
    ]);
    expect(file).toEqual({
      key: "uuid-1",
      uri: `${CACHE_URI}/pasted-uuid-1.png`,
      name: "pasted-uuid-1.png",
      mimeType: "image/png",
      size: 2048,
      width: 640,
      height: 480,
    });
  });

  it("keeps the file with unknown dimensions when the image cannot be decoded", async () => {
    dimensions = new Error("decode failed");

    const file = await pastedImageFile("file:///tmp/a.jpg");

    expect(file.mimeType).toBe("image/jpeg");
    expect(file.width).toBeNull();
    expect(file.height).toBeNull();
  });

  it("throws when the copy fails", async () => {
    failingCopies.add("file:///tmp/gone.png");

    await expect(pastedImageFile("file:///tmp/gone.png")).rejects.toThrow("cannot copy");
  });
});

describe("pastedImageFiles", () => {
  it("copies every pasted image in order and counts the ones that fail", async () => {
    failingCopies.add("file:///tmp/b.png");

    const result = await pastedImageFiles([
      "file:///tmp/a.gif",
      "file:///tmp/b.png",
      "file:///tmp/c.jpg",
    ]);

    expect(result.failed).toBe(1);
    expect(result.files.map((file) => [file.name, file.mimeType])).toEqual([
      ["pasted-uuid-1.gif", "image/gif"],
      ["pasted-uuid-3.jpg", "image/jpeg"],
    ]);
  });

  it("gives nothing for no images", async () => {
    expect(await pastedImageFiles([])).toEqual({ files: [], failed: 0 });
  });
});

describe("pastedImageFromBase64", () => {
  it("writes a clipboard data URI as base64 into the cache with the given dimensions", async () => {
    const file = await pastedImageFromBase64({
      base64: "data:image/jpeg;base64,QUJDRA==",
      width: 1170,
      height: 2532,
    });

    expect(writes).toEqual([
      { uri: `${CACHE_URI}/pasted-uuid-1.jpg`, content: "QUJDRA==", encoding: "base64" },
    ]);
    expect(file).toEqual({
      key: "uuid-1",
      uri: `${CACHE_URI}/pasted-uuid-1.jpg`,
      name: "pasted-uuid-1.jpg",
      mimeType: "image/jpeg",
      size: 8,
      width: 1170,
      height: 2532,
    });
  });

  it("reads the dimensions from the written file when none are given", async () => {
    dimensions = { width: 300, height: 200 };

    const file = await pastedImageFromBase64({ base64: "QUJD", mimeType: "image/png" });

    expect(file.name).toBe("pasted-uuid-1.png");
    expect(file.mimeType).toBe("image/png");
    expect([file.width, file.height]).toEqual([300, 200]);
  });

  it("keeps an unknown type without an extension", async () => {
    const file = await pastedImageFromBase64({ base64: "QUJD", mimeType: "image/x-odd" });

    expect(file.name).toBe("pasted-uuid-1");
    expect(file.mimeType).toBe("image/x-odd");
  });
});
