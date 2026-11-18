import { beforeEach, describe, expect, it, mock } from "bun:test";

import type { ChatLocalFile } from "@/lib/schemas/chat";

/*
 * expo-image-manipulator and expo-file-system are native modules; the fakes
 * record each resize and save and report the sizes the test sets.
 */

const sizes: Map<string, number> = new Map();
const resizes: number[] = [];
const saves: { format: string | undefined; compress: number | undefined }[] = [];
let decoded: { width: number; height: number } = { width: 800, height: 600 };

class FakeImageRef {
  constructor(
    readonly width: number,
    readonly height: number
  ) {}

  async saveAsync(options: { format?: string; compress?: number }) {
    saves.push({ format: options.format, compress: options.compress });
    const uri = `file:///manipulated/${saves.length}.${options.format}`;
    sizes.set(uri, 1000 + saves.length);
    return { uri, width: this.width, height: this.height };
  }
}

class FakeContext {
  private size: { width: number; height: number };

  constructor(size: { width: number; height: number }) {
    this.size = size;
  }

  resize({ width }: { width: number }): FakeContext {
    resizes.push(width);
    this.size = { width, height: Math.round((this.size.height * width) / this.size.width) };
    return this;
  }

  async renderAsync(): Promise<FakeImageRef> {
    return new FakeImageRef(this.size.width, this.size.height);
  }
}

mock.module("expo-image-manipulator", () => ({
  ImageManipulator: {
    manipulate: (source: string | FakeImageRef) =>
      new FakeContext(typeof source === "string" ? decoded : source),
  },
  SaveFormat: { JPEG: "jpeg", PNG: "png", WEBP: "webp" },
}));

mock.module("expo-file-system", () => ({
  File: class {
    constructor(readonly uri: string) {}
    get size(): number {
      return sizes.get(this.uri) ?? 0;
    }
  },
  Directory: class {},
  Paths: { cache: { uri: "file:///cache" } },
}));

mock.module("expo-crypto", () => ({ randomUUID: () => "uuid-1" }));

const {
  DESCRIPTION_IMAGE_MAX_BYTES,
  imagePrepPlan,
  preparedImageName,
  prepareDescriptionImage,
  scaledImageWidth,
} = await import("@/components/kaizen/imagePrep");

function localFile(overrides: Partial<ChatLocalFile>): ChatLocalFile {
  return {
    key: "picked",
    uri: "file:///picked/photo.png",
    name: "photo.png",
    mimeType: "image/png",
    size: 5000,
    width: 800,
    height: 600,
    ...overrides,
  };
}

beforeEach(() => {
  sizes.clear();
  resizes.length = 0;
  saves.length = 0;
  decoded = { width: 800, height: 600 };
});

describe("imagePrepPlan", () => {
  it("refuses SVG and anything that is not an image", () => {
    expect(imagePrepPlan({ mimeType: "image/svg+xml", size: 10 })).toEqual({
      kind: "refuse",
      reason: "imageTypeBlocked",
    });
    expect(imagePrepPlan({ mimeType: "application/pdf", size: 10 })).toEqual({
      kind: "refuse",
      reason: "imageTypeBlocked",
    });
    expect(imagePrepPlan({ mimeType: null, size: 10 })).toEqual({
      kind: "refuse",
      reason: "imageTypeBlocked",
    });
  });

  it("refuses an image over 10 MB and takes one of exactly 10 MB", () => {
    expect(
      imagePrepPlan({ mimeType: "image/jpeg", size: DESCRIPTION_IMAGE_MAX_BYTES + 1 })
    ).toEqual({ kind: "refuse", reason: "imageTooLarge" });
    expect(imagePrepPlan({ mimeType: "image/jpeg", size: DESCRIPTION_IMAGE_MAX_BYTES })).toEqual({
      kind: "encode",
    });
  });

  it("keeps a GIF and encodes every other image", () => {
    expect(imagePrepPlan({ mimeType: "image/GIF", size: 10 })).toEqual({ kind: "keep" });
    expect(imagePrepPlan({ mimeType: "image/png; x=y", size: 10 })).toEqual({ kind: "encode" });
    expect(imagePrepPlan({ mimeType: "image/heic", size: 10 })).toEqual({ kind: "encode" });
  });

  it("scales only images wider than 1200 px", () => {
    expect(scaledImageWidth(1201)).toBe(1200);
    expect(scaledImageWidth(1200)).toBeNull();
    expect(scaledImageWidth(400)).toBeNull();
  });

  it("names the prepared file after the original with .webp", () => {
    expect(preparedImageName("IMG_0001.HEIC")).toBe("IMG_0001.webp");
    expect(preparedImageName("pasted-uuid-1")).toBe("pasted-uuid-1.webp");
    expect(preparedImageName(".png")).toBe("image.webp");
  });
});

describe("prepareDescriptionImage", () => {
  it("scales a wide image to 1200 px and saves it as WebP 0.8", async () => {
    decoded = { width: 2400, height: 1600 };

    const result = await prepareDescriptionImage(localFile({ width: null, height: null }));

    expect(resizes).toEqual([1200]);
    expect(saves).toEqual([{ format: "webp", compress: 0.8 }]);
    expect(result).toEqual({
      kind: "ready",
      file: {
        key: "uuid-1",
        uri: "file:///manipulated/1.webp",
        name: "photo.webp",
        mimeType: "image/webp",
        size: 1001,
        width: 1200,
        height: 800,
      },
    });
  });

  it("encodes a narrow image as WebP without scaling it", async () => {
    const result = await prepareDescriptionImage(localFile({}));

    expect(resizes).toEqual([]);
    expect(saves).toEqual([{ format: "webp", compress: 0.8 }]);
    expect(result.kind === "ready" && [result.file.width, result.file.height]).toEqual([800, 600]);
  });

  it("passes a GIF through unchanged, reading missing dimensions from its first frame", async () => {
    decoded = { width: 320, height: 240 };
    const gif = localFile({ uri: "file:///picked/a.gif", name: "a.gif", mimeType: "image/gif" });

    expect(await prepareDescriptionImage(gif)).toEqual({ kind: "ready", file: gif });
    expect(
      await prepareDescriptionImage({ ...gif, width: null, height: null, size: null })
    ).toEqual({ kind: "ready", file: { ...gif, size: 0, width: 320, height: 240 } });
    expect(saves).toEqual([]);
  });

  it("refuses an SVG and a file whose size, read from disk, is over 10 MB", async () => {
    expect(await prepareDescriptionImage(localFile({ mimeType: "image/svg+xml" }))).toEqual({
      kind: "refused",
      reason: "imageTypeBlocked",
    });
    sizes.set("file:///picked/big.jpg", DESCRIPTION_IMAGE_MAX_BYTES + 1);
    expect(
      await prepareDescriptionImage(
        localFile({ uri: "file:///picked/big.jpg", mimeType: "image/jpeg", size: null })
      )
    ).toEqual({ kind: "refused", reason: "imageTooLarge" });
    expect(saves).toEqual([]);
  });
});
