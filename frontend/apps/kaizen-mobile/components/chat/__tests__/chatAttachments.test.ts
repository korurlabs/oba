import { describe, expect, it } from "bun:test";

import {
  chatDownloadFileName,
  chatFilesProblem,
  chatImageBox,
  chatLocalFileFromDocument,
  chatLocalFileFromImage,
  chatSafeFileName,
  formatChatFileSize,
  isChatImageType,
  isChatPreviewableImage,
  splitChatAttachments,
} from "@/components/chat/chatAttachments";
import {
  CHAT_MAX_FILES,
  CHAT_MAX_REQUEST_BYTES,
  type ChatAttachmentResponse,
  type ChatLocalFile,
} from "@/lib/schemas/chat";

function attachment(overrides: Partial<ChatAttachmentResponse> = {}): ChatAttachmentResponse {
  return {
    id: "a1",
    url: "https://storage.example/a1?sig=x",
    fileName: "photo.png",
    contentType: "image/png",
    fileSize: 1024,
    entityType: "CHAT_MESSAGE",
    entityId: "m1",
    createdAt: "2026-11-01T10:00:00Z",
    width: null,
    height: null,
    ...overrides,
  } as ChatAttachmentResponse;
}

function localFile(overrides: Partial<ChatLocalFile> = {}): ChatLocalFile {
  return {
    key: "k1",
    uri: "file:///cache/a.jpg",
    name: "a.jpg",
    mimeType: "image/jpeg",
    size: 1000,
    width: null,
    height: null,
    ...overrides,
  };
}

describe("isChatPreviewableImage", () => {
  it("draws PNG and JPEG, with parameters and in any case", () => {
    expect(isChatPreviewableImage("image/png")).toBe(true);
    expect(isChatPreviewableImage("IMAGE/JPEG; charset=binary")).toBe(true);
  });

  it("lists other images and files by name", () => {
    expect(isChatPreviewableImage("image/webp")).toBe(false);
    expect(isChatPreviewableImage("image/gif")).toBe(false);
    expect(isChatPreviewableImage("application/pdf")).toBe(false);
    expect(isChatPreviewableImage(null)).toBe(false);
  });

  it("counts every image type as an image for the photo library", () => {
    expect(isChatImageType("image/webp")).toBe(true);
    expect(isChatImageType("application/pdf")).toBe(false);
    expect(isChatImageType(undefined)).toBe(false);
  });
});

describe("formatChatFileSize", () => {
  it("formats bytes, kilobytes and megabytes", () => {
    expect(formatChatFileSize(512)).toBe("512 B");
    expect(formatChatFileSize(1536)).toBe("1.5 KB");
    expect(formatChatFileSize(3 * 1024 * 1024)).toBe("3.0 MB");
  });

  it("is empty when the size is unknown", () => {
    expect(formatChatFileSize(null)).toBe("");
    expect(formatChatFileSize(0)).toBe("");
  });
});

describe("chatImageBox", () => {
  const max = { width: 280, height: 320 };

  it("gives an image of unknown size a 4:3 box", () => {
    expect(chatImageBox(null, null, max)).toEqual({ width: 280, height: 210 });
    expect(chatImageBox(800, null, max)).toEqual({ width: 280, height: 210 });
  });

  it("scales a large image down with its aspect kept", () => {
    expect(chatImageBox(1400, 1050, max)).toEqual({ width: 280, height: 210 });
    expect(chatImageBox(1000, 2000, max)).toEqual({ width: 160, height: 320 });
  });

  it("never grows a small image", () => {
    expect(chatImageBox(100, 50, max)).toEqual({ width: 100, height: 50 });
  });
});

describe("chatImageBox at the web chat's message image size", () => {
  // The web draws a message image with max-w-64 max-h-48: at most 256 by 192.
  const webMax = { width: 256, height: 192 };

  it("fits one large landscape image to the width", () => {
    expect(chatImageBox(1920, 1080, webMax)).toEqual({ width: 256, height: 144 });
  });

  it("gives each of three images of mixed aspect its own box, none square", () => {
    const boxes = [
      chatImageBox(1600, 1200, webMax),
      chatImageBox(1170, 2532, webMax),
      chatImageBox(3000, 1000, webMax),
    ];
    expect(boxes).toEqual([
      { width: 256, height: 192 },
      { width: 89, height: 192 },
      { width: 256, height: 85 },
    ]);
  });

  it("gives an image without stored dimensions the full 4:3 box", () => {
    expect(chatImageBox(null, null, webMax)).toEqual({ width: 256, height: 192 });
  });

  it("narrows the box on a screen with less room than 256", () => {
    expect(chatImageBox(1600, 1200, { width: 200, height: 192 })).toEqual({
      width: 200,
      height: 150,
    });
    expect(chatImageBox(null, null, { width: 200, height: 192 })).toEqual({
      width: 200,
      height: 150,
    });
  });
});

describe("splitChatAttachments", () => {
  it("draws PNG and JPEG and lists the rest, in order", () => {
    const png = attachment({ id: "p" });
    const pdf = attachment({ id: "d", contentType: "application/pdf", fileName: "a.pdf" });
    const jpg = attachment({ id: "j", contentType: "image/jpeg" });
    const webp = attachment({ id: "w", contentType: "image/webp" });
    const { images, files } = splitChatAttachments([png, pdf, jpg, webp]);
    expect(images.map((item) => item.id)).toEqual(["p", "j"]);
    expect(files.map((item) => item.id)).toEqual(["d", "w"]);
  });
});

describe("chatFilesProblem", () => {
  it("accepts files within both limits", () => {
    expect(chatFilesProblem([])).toBeNull();
    expect(chatFilesProblem([localFile(), localFile({ key: "k2" })])).toBeNull();
  });

  it("refuses more than the file limit", () => {
    const files = Array.from({ length: CHAT_MAX_FILES + 1 }, (_, index) =>
      localFile({ key: `k${index}` })
    );
    expect(chatFilesProblem(files)).toBe("tooManyFiles");
  });

  it("refuses files that together pass the request limit", () => {
    const half = CHAT_MAX_REQUEST_BYTES / 2;
    expect(
      chatFilesProblem([localFile({ size: half }), localFile({ key: "k2", size: half })])
    ).toBeNull();
    expect(
      chatFilesProblem([localFile({ size: half }), localFile({ key: "k2", size: half + 1 })])
    ).toBe("filesTooLarge");
  });

  it("counts a file of unknown size as nothing", () => {
    expect(chatFilesProblem([localFile({ size: null })])).toBeNull();
  });
});

describe("chatLocalFileFromImage", () => {
  it("keeps the picker's name, type, size and dimensions", () => {
    const file = chatLocalFileFromImage(
      {
        uri: "file:///x/IMG_1.JPG",
        fileName: "IMG_1.JPG",
        fileSize: 2048,
        mimeType: "image/jpeg",
        width: 4032,
        height: 3024,
      },
      "k"
    );
    expect(file).toEqual({
      key: "k",
      uri: "file:///x/IMG_1.JPG",
      name: "IMG_1.JPG",
      mimeType: "image/jpeg",
      size: 2048,
      width: 4032,
      height: 3024,
    });
  });

  it("names a camera photo after its file and types it by extension", () => {
    const file = chatLocalFileFromImage(
      { uri: "file:///cache/ImagePicker/4F2A.jpeg", fileName: null, width: 0, height: 0 },
      "k"
    );
    expect(file.name).toBe("4F2A.jpeg");
    expect(file.mimeType).toBe("image/jpeg");
    expect(file.size).toBeNull();
    expect(file.width).toBeNull();
    expect(file.height).toBeNull();
  });
});

describe("chatLocalFileFromDocument", () => {
  it("keeps the name and size and has no dimensions", () => {
    const file = chatLocalFileFromDocument(
      {
        uri: "file:///cache/report.pdf",
        name: "report.pdf",
        size: 99,
        mimeType: "application/pdf",
      },
      "k"
    );
    expect(file).toEqual({
      key: "k",
      uri: "file:///cache/report.pdf",
      name: "report.pdf",
      mimeType: "application/pdf",
      size: 99,
      width: null,
      height: null,
    });
  });

  it("types a file without a type by its extension, else leaves it unknown", () => {
    expect(chatLocalFileFromDocument({ uri: "file:///a.png", name: "a.png" }, "k").mimeType).toBe(
      "image/png"
    );
    expect(
      chatLocalFileFromDocument({ uri: "file:///a.xyz", name: "a.xyz" }, "k").mimeType
    ).toBeNull();
  });
});

describe("chatDownloadFileName", () => {
  it("prefixes the ID and replaces characters file systems refuse", () => {
    expect(chatDownloadFileName({ id: "a1", fileName: "q3/report:final?.pdf" })).toBe(
      "a1-q3_report_final_.pdf"
    );
  });

  it("falls back to a plain name when nothing is left", () => {
    expect(chatDownloadFileName({ id: "a1", fileName: "   " })).toBe("a1-file");
  });
});

describe("chatSafeFileName", () => {
  it("keeps the name a picked folder gets readable and free of path separators", () => {
    expect(chatSafeFileName("../q3/report:final?.pdf")).toBe(".._q3_report_final_.pdf");
    expect(chatSafeFileName("notes.txt")).toBe("notes.txt");
    expect(chatSafeFileName("")).toBe("file");
  });
});
