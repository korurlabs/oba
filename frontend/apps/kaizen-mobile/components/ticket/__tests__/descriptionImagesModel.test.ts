import { describe, expect, it, mock } from "bun:test";

import type { UploadedImage } from "@/components/ticket/descriptionImagesModel";
import type { PendingImage } from "@/lib/schemas/tickets";

// The model reads attachment ids with ticketAttachmentsModel.ts, which loads
// lib/api.ts; these are the stubs lib/__tests__/api.test.ts uses for it.
mock.module("react-native", () => ({
  Platform: { OS: "ios" },
  AppState: { addEventListener: () => ({ remove: () => undefined }) },
}));

mock.module("expo-secure-store", () => ({
  getItemAsync: async () => null,
  setItemAsync: async () => undefined,
  deleteItemAsync: async () => undefined,
}));

mock.module("@/lib/env", () => ({
  ENV: {
    apiBaseUrl: "https://api.test",
    keycloakIssuer: "https://auth.test/realms/sensey",
    keycloakClientId: "kaizen-mobile",
    accountUrl: "https://account.test",
    adminUrl: "https://admin.test",
    kaizenWebUrl: "https://kaizen.test",
  },
}));

mock.module("expo-auth-session", () => ({
  fetchDiscoveryAsync: async (issuer: string) => ({
    authorizationEndpoint: `${issuer}/protocol/openid-connect/auth`,
    tokenEndpoint: `${issuer}/protocol/openid-connect/token`,
    endSessionEndpoint: `${issuer}/protocol/openid-connect/logout`,
  }),
  makeRedirectUri: ({ scheme, path }: { scheme: string; path: string }) => `${scheme}://${path}`,
}));

const {
  commitPendingImages,
  descriptionImageSources,
  isPendingImageSrc,
  MissingPendingImageError,
  orphanAttachmentIds,
  pendingImageSrc,
  pendingImageSrcs,
  reconcilePendingImages,
  rewritePendingImageTag,
} = await import("@/components/ticket/descriptionImagesModel");

/** A pending image as the editor inserts it, 800 x 600. */
function pending(key: string, extra: Partial<PendingImage> = {}): PendingImage {
  return {
    key,
    file: {
      key,
      uri: `file:///cache/${key}.jpg`,
      name: `${key}.jpg`,
      mimeType: "image/jpeg",
      size: 1000,
      width: 800,
      height: 600,
    },
    src: pendingImageSrc(key),
    alt: `${key}.jpg`,
    width: 800,
    height: 600,
    ...extra,
  };
}

/** The tag the editor serializes for an image, as the web's Image extension writes it. */
function tag(src: string, alt: string, size: string = ' width="800" height="600"'): string {
  return `<img src="${src}" alt="${alt}"${size} loading="lazy" decoding="async">`;
}

const STORED: UploadedImage = {
  id: "att-1",
  url: "https://store.example.com/c/a.jpg?sv=2024&sig=x",
};

describe("pendingImageSrc", () => {
  it("prefixes the key and is recognised as pending", () => {
    expect(pendingImageSrc("k1")).toBe("kaizen-pending:k1");
    expect(isPendingImageSrc("kaizen-pending:k1")).toBe(true);
    expect(isPendingImageSrc("https://store.example.com/a.jpg")).toBe(false);
  });
});

describe("rewritePendingImageTag", () => {
  // Expected strings follow the web's rewriteImgTag in commit-pending-images.ts.
  it("swaps the src for the escaped URL and appends the id after the existing size", () => {
    const image = pending("k1");
    expect(rewritePendingImageTag(`Intro\n\n${tag(image.src, "k1.jpg")}\n`, image, STORED)).toBe(
      'Intro\n\n<img src="https://store.example.com/c/a.jpg?sv=2024&amp;sig=x" alt="k1.jpg" width="800" height="600" loading="lazy" decoding="async" data-attachment-id="att-1">\n'
    );
  });

  it("appends width and height only when the tag has none", () => {
    const image = pending("k1");
    expect(rewritePendingImageTag(tag(image.src, "a", ""), image, STORED)).toBe(
      '<img src="https://store.example.com/c/a.jpg?sv=2024&amp;sig=x" alt="a" loading="lazy" decoding="async" data-attachment-id="att-1" width="800" height="600">'
    );
    expect(
      rewritePendingImageTag(`<img src="${image.src}" alt="a" height="10">`, image, STORED)
    ).toBe(
      '<img src="https://store.example.com/c/a.jpg?sv=2024&amp;sig=x" alt="a" height="10" data-attachment-id="att-1" width="800">'
    );
  });

  it("replaces an id the tag already carries in place", () => {
    const image = pending("k1");
    expect(
      rewritePendingImageTag(
        `<img src="${image.src}" data-attachment-id="old" alt="a" width="1" height="2">`,
        image,
        STORED
      )
    ).toBe(
      '<img src="https://store.example.com/c/a.jpg?sv=2024&amp;sig=x" data-attachment-id="att-1" alt="a" width="1" height="2">'
    );
  });

  it("leaves other images alone and rewrites every copy of this one", () => {
    const image = pending("k1");
    const other: string = tag(pendingImageSrc("k2"), "k2");
    const saved = '<img src="https://store.example.com/b.jpg" data-attachment-id="att-0">';
    const markdown = `${tag(image.src, "a")}\n\n${other}\n\n${saved}\n\n${tag(image.src, "a")}`;
    const rewritten: string = rewritePendingImageTag(markdown, image, STORED);
    expect(rewritten.split("\n\n")[1]).toBe(other);
    expect(rewritten.split("\n\n")[2]).toBe(saved);
    expect(rewritten).not.toContain(image.src);
    expect(rewritten.match(/data-attachment-id="att-1"/g)).toHaveLength(2);
  });

  it("does not take a longer key that starts with this one", () => {
    const image = pending("k1");
    const longer: string = tag(pendingImageSrc("k10"), "b");
    expect(rewritePendingImageTag(longer, image, STORED)).toBe(longer);
  });
});

describe("reconcilePendingImages", () => {
  it("keeps the images the text still shows, in the order added", () => {
    const [a, b, c] = [pending("a"), pending("b"), pending("c")];
    const markdown = `${tag(c.src, "c")}\n\n${tag(a.src, "a")}`;
    expect(reconcilePendingImages(markdown, [a, b, c]).map((image) => image.key)).toEqual([
      "a",
      "c",
    ]);
    expect(reconcilePendingImages("", [a, b, c])).toEqual([]);
  });
});

describe("pendingImageSrcs", () => {
  it("lists the pending tags only", () => {
    const markdown = `${tag(pendingImageSrc("a"), "a")} <img src="https://x.example.com/1.png"> ${tag(pendingImageSrc("b"), "b")}`;
    expect(pendingImageSrcs(markdown)).toEqual(["kaizen-pending:a", "kaizen-pending:b"]);
  });
});

describe("orphanAttachmentIds", () => {
  // Cases of the web's collectOrphanAttachmentIds in orphan-gc.ts.
  const img = (id: string): string =>
    `<img src="https://store.example.com/${id}.jpg" data-attachment-id="${id}">`;

  it("lists the ids the new text dropped, each once", () => {
    expect(orphanAttachmentIds(`${img("a")}${img("b")}${img("a")}${img("c")}`, img("b"))).toEqual([
      "a",
      "c",
    ]);
  });

  it("is empty when nothing was dropped or nothing was there", () => {
    expect(orphanAttachmentIds(img("a"), `${img("a")}${img("b")}`)).toEqual([]);
    expect(orphanAttachmentIds("", img("a"))).toEqual([]);
    expect(orphanAttachmentIds(null, undefined)).toEqual([]);
  });

  it("lists every id when the text is cleared", () => {
    expect(orphanAttachmentIds(`${img("a")}${img("b")}`, "")).toEqual(["a", "b"]);
  });
});

describe("commitPendingImages", () => {
  function uploads() {
    const sent: string[] = [];
    const recorded: [string, UploadedImage][] = [];
    const progress: [number, number][] = [];
    return {
      sent,
      recorded,
      progress,
      upload: async (image: PendingImage): Promise<UploadedImage> => {
        sent.push(image.key);
        return { id: `att-${image.key}`, url: `https://store.example.com/${image.key}.jpg` };
      },
      onUploaded: (key: string, uploaded: UploadedImage) => recorded.push([key, uploaded]),
      onProgress: (current: number, total: number) => progress.push([current, total]),
    };
  }

  it("uploads the images still in the text, one by one, and rewrites their tags", async () => {
    const [a, b, removed] = [pending("a"), pending("b"), pending("removed")];
    const markdown = `${tag(a.src, "a")}\n\nText\n\n${tag(b.src, "b")}`;
    const fake = uploads();
    const committed: string = await commitPendingImages({
      markdown,
      images: [a, removed, b],
      ...fake,
    });
    expect(fake.sent).toEqual(["a", "b"]);
    expect(fake.progress).toEqual([
      [1, 2],
      [2, 2],
    ]);
    expect(fake.recorded.map(([key]) => key)).toEqual(["a", "b"]);
    expect(committed).toBe(
      '<img src="https://store.example.com/a.jpg" alt="a" width="800" height="600" loading="lazy" decoding="async" data-attachment-id="att-a">\n\nText\n\n<img src="https://store.example.com/b.jpg" alt="b" width="800" height="600" loading="lazy" decoding="async" data-attachment-id="att-b">'
    );
  });

  it("skips the upload of an image an earlier attempt uploaded", async () => {
    const a = pending("a", {
      uploaded: { id: "att-old", url: "https://store.example.com/old.jpg" },
    });
    const b = pending("b");
    const fake = uploads();
    const committed: string = await commitPendingImages({
      markdown: `${tag(a.src, "a")}${tag(b.src, "b")}`,
      images: [a, b],
      ...fake,
    });
    expect(fake.sent).toEqual(["b"]);
    expect(fake.progress).toEqual([[1, 1]]);
    expect(committed).toContain('data-attachment-id="att-old"');
    expect(committed).toContain('data-attachment-id="att-b"');
  });

  it("stops at the first failure with the uploads before it recorded", async () => {
    const [a, b, c] = [pending("a"), pending("b"), pending("c")];
    const fake = uploads();
    const failing = async (image: PendingImage): Promise<UploadedImage> => {
      if (image.key === "b") throw new Error("413");
      return fake.upload(image);
    };
    await expect(
      commitPendingImages({
        markdown: `${tag(a.src, "a")}${tag(b.src, "b")}${tag(c.src, "c")}`,
        images: [a, b, c],
        upload: failing,
        onUploaded: fake.onUploaded,
      })
    ).rejects.toThrow("413");
    expect(fake.recorded.map(([key]) => key)).toEqual(["a"]);
    expect(fake.sent).toEqual(["a"]);
  });

  it("refuses a pending tag whose image is unknown, before any upload", async () => {
    const a = pending("a");
    const fake = uploads();
    const markdown = `${tag(a.src, "a")}${tag(pendingImageSrc("lost"), "lost")}`;
    const attempt = commitPendingImages({ markdown, images: [a], ...fake });
    await expect(attempt).rejects.toBeInstanceOf(MissingPendingImageError);
    expect(fake.sent).toEqual([]);
  });

  it("returns a text without pending images unchanged", async () => {
    const fake = uploads();
    const markdown =
      '# Title\n\n<img src="https://store.example.com/a.jpg" data-attachment-id="x">';
    expect(await commitPendingImages({ markdown, images: [], ...fake })).toBe(markdown);
    expect(fake.sent).toEqual([]);
  });
});

describe("descriptionImageSources", () => {
  const SIGNED = "https://store.example.com/c/a.png?sv=2024&amp;sig=x%2By";

  it("reads a web-saved image tag with its id, size and decoded URL", () => {
    const markdown = `Intro\n\n<img src="${SIGNED}" alt="Login &quot;bug&quot;" width="640" height="480" data-attachment-id="att-1">\n`;
    expect(descriptionImageSources(markdown)).toEqual([
      {
        src: "https://store.example.com/c/a.png?sv=2024&sig=x%2By",
        alt: 'Login "bug"',
        attachmentId: "att-1",
        width: 640,
        height: 480,
      },
    ]);
  });

  it("lists tags and Markdown images in reading order", () => {
    const markdown = [
      "![first](https://img.example.com/1.png)",
      '<img data-attachment-id="att-2" src="https://store.example.com/2.png">',
      '![third](https://img.example.com/3.png "A title")',
    ].join("\n\n");
    expect(descriptionImageSources(markdown).map((image) => [image.src, image.alt])).toEqual([
      ["https://img.example.com/1.png", "first"],
      ["https://store.example.com/2.png", ""],
      ["https://img.example.com/3.png", "third"],
    ]);
  });

  it("leaves the id and size out where the image has none or an invalid one", () => {
    const [markdownImage, tag] = descriptionImageSources(
      '![](https://img.example.com/1.png) <img src=\'https://img.example.com/2.png\' width="auto" height="0">'
    );
    expect(markdownImage).toEqual({
      src: "https://img.example.com/1.png",
      alt: "",
      attachmentId: null,
      width: null,
      height: null,
    });
    expect(tag).toEqual({
      src: "https://img.example.com/2.png",
      alt: "",
      attachmentId: null,
      width: null,
      height: null,
    });
  });

  it("does not mistake a similar attribute for the source", () => {
    expect(
      descriptionImageSources(
        '<img data-src="https://x.example.com/a.png" src="https://y.example.com/b.png">'
      )[0]?.src
    ).toBe("https://y.example.com/b.png");
  });

  it("returns nothing for an empty or image-free description", () => {
    expect(descriptionImageSources(null)).toEqual([]);
    expect(descriptionImageSources("")).toEqual([]);
    expect(descriptionImageSources("Plain text with a [link](https://example.com).")).toEqual([]);
  });
});
