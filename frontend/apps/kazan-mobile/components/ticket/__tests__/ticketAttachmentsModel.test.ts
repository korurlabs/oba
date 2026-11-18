import { describe, expect, it, mock } from "bun:test";

import type { ChatAttachmentResponse, ChatLocalFile } from "@/lib/schemas/chat";

// The same stubs as lib/__tests__/api.test.ts: lib/api.ts loads the env,
// SecureStore and Keycloak discovery at import.
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
    keycloakClientId: "kazan-mobile",
    accountUrl: "https://account.test",
    adminUrl: "https://admin.test",
    kazanWebUrl: "https://kazan.test",
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

const { ApiError } = await import("@/lib/api");
const {
  parseAttachmentIds,
  ticketAttachmentErrorMessage,
  ticketAttachmentsProblem,
  ticketAttachmentType,
  visibleTicketAttachments,
} = await import("@/components/ticket/ticketAttachmentsModel");

const MB = 1024 * 1024;

function localFile(overrides: Partial<ChatLocalFile> = {}): ChatLocalFile {
  return {
    key: "k1",
    uri: "file:///cache/shot.png",
    name: "shot.png",
    mimeType: "image/png",
    size: MB,
    width: null,
    height: null,
    ...overrides,
  };
}

function attachment(id: string): ChatAttachmentResponse {
  return {
    id,
    url: `https://blob.test/${id}`,
    fileName: `${id}.png`,
    contentType: "image/png",
    fileSize: 100,
    entityType: "TICKET",
    entityId: "7d3f7f58-2f0b-4a8e-9c3e-1d2b3c4d5e6f",
    createdAt: "2026-11-18T09:00:00Z",
  };
}

describe("ticketAttachmentType", () => {
  it("takes the picker's type without parameters, lower case", () => {
    expect(ticketAttachmentType({ name: "a.bin", mimeType: "Image/JPEG; q=1" })).toBe("image/jpeg");
  });

  it("falls back to the extension, and to null when neither tells", () => {
    expect(ticketAttachmentType({ name: "Report.PDF", mimeType: null })).toBe("application/pdf");
    expect(ticketAttachmentType({ name: "notes", mimeType: null })).toBeNull();
    expect(ticketAttachmentType({ name: "song.mp3", mimeType: null })).toBeNull();
  });
});

describe("ticketAttachmentsProblem", () => {
  it("accepts PNG, JPEG and PDF and fills in a type the picker left out", () => {
    const result = ticketAttachmentsProblem(
      [],
      [
        localFile(),
        localFile({ key: "k2", name: "photo.jpg", mimeType: "image/jpg" }),
        localFile({ key: "k3", name: "spec.pdf", mimeType: null }),
      ]
    );

    expect(result.refused).toEqual([]);
    expect(result.accepted.map((file) => file.mimeType)).toEqual([
      "image/png",
      "image/jpg",
      "application/pdf",
    ]);
  });

  it("refuses another type by name", () => {
    const result = ticketAttachmentsProblem(
      [],
      [
        localFile({ name: "clip.gif", mimeType: "image/gif" }),
        localFile({ name: "a.heic", mimeType: "image/heic" }),
      ]
    );

    expect(result.accepted).toEqual([]);
    expect(result.refused).toEqual([
      { key: "ticketAttachmentTypeNotAllowed", params: { name: "clip.gif", max: 12 } },
      { key: "ticketAttachmentTypeNotAllowed", params: { name: "a.heic", max: 12 } },
    ]);
  });

  it("refuses a file over 12 MB and keeps the rest", () => {
    const result = ticketAttachmentsProblem(
      [],
      [localFile({ name: "big.png", size: 12 * MB + 1 }), localFile({ key: "k2", name: "ok.png" })]
    );

    expect(result.accepted.map((file) => file.name)).toEqual(["ok.png"]);
    expect(result.refused).toEqual([
      { key: "ticketAttachmentFileTooLarge", params: { name: "big.png", max: 12 } },
    ]);
  });

  it("refuses a file that takes the staged batch over 12 MB, counting files already staged", () => {
    const staged: ChatLocalFile[] = [localFile({ key: "s1", name: "staged.pdf", size: 8 * MB })];

    const result = ticketAttachmentsProblem(staged, [
      localFile({ key: "k2", name: "three.png", size: 3 * MB }),
      localFile({ key: "k3", name: "two.png", size: 2 * MB }),
      localFile({ key: "k4", name: "one.png", size: MB }),
    ]);

    expect(result.accepted.map((file) => file.name)).toEqual(["three.png", "one.png"]);
    expect(result.refused).toEqual([
      { key: "ticketAttachmentBatchTooLarge", params: { name: "two.png", max: 12 } },
    ]);
  });

  it("accepts a batch of exactly 12 MB", () => {
    const result = ticketAttachmentsProblem(
      [localFile({ key: "s1", size: 6 * MB })],
      [localFile({ key: "k2", size: 6 * MB })]
    );

    expect(result.refused).toEqual([]);
  });

  it("counts a file of unknown size as nothing", () => {
    const result = ticketAttachmentsProblem(
      [localFile({ key: "s1", size: 12 * MB })],
      [localFile({ key: "k2", size: null })]
    );

    expect(result.accepted).toHaveLength(1);
  });
});

function refusal(status: number | undefined, code: string | undefined, errorValues = {}): Error {
  const kind =
    status === undefined
      ? "network"
      : status === 403
        ? "forbidden"
        : status === 404
          ? "not-found"
          : status >= 500
            ? "server"
            : "client";
  return new ApiError("Refused by the server", { status, code, errorValues, kind });
}

describe("ticketAttachmentErrorMessage", () => {
  it("names the file of a duplicate", () => {
    expect(
      ticketAttachmentErrorMessage(
        refusal(400, "STG_VAL_005", { fileName: "shot.png", fileHash: "abc" }),
        "upload"
      )
    ).toEqual({ key: "ticketAttachmentsDuplicate", params: { name: "shot.png" } });
  });

  it("names the file of an empty upload", () => {
    expect(
      ticketAttachmentErrorMessage(refusal(400, "STG_VAL_002", { fileName: "empty.pdf" }), "upload")
    ).toEqual({ key: "ticketAttachmentsEmptyFile", params: { name: "empty.pdf" } });
  });

  it("explains a request over the limit, by status or by code", () => {
    const expected = { key: "ticketAttachmentsTooLarge", params: { max: 12 } };
    expect(ticketAttachmentErrorMessage(refusal(413, "GEN_API_008"), "upload")).toEqual(expected);
    expect(ticketAttachmentErrorMessage(refusal(413, undefined), "upload")).toEqual(expected);
  });

  it("explains a missing board role, a ticket or file that is gone, and no connection", () => {
    expect(ticketAttachmentErrorMessage(refusal(403, "SEC_001"), "delete").key).toBe(
      "ticketAttachmentsForbidden"
    );
    expect(ticketAttachmentErrorMessage(refusal(404, "STG_NOT_001"), "delete").key).toBe(
      "ticketAttachmentsNotFound"
    );
    expect(ticketAttachmentErrorMessage(refusal(undefined, undefined), "upload").key).toBe(
      "ticketAttachmentsNetwork"
    );
  });

  it("falls back to the operation's general failure", () => {
    expect(ticketAttachmentErrorMessage(refusal(500, "GEN_SRV_001"), "list").key).toBe(
      "ticketAttachmentsLoadFailed"
    );
    expect(ticketAttachmentErrorMessage(refusal(400, "GEN_API_001"), "upload").key).toBe(
      "ticketAttachmentsUploadFailed"
    );
    expect(ticketAttachmentErrorMessage(new Error("boom"), "delete").key).toBe(
      "ticketAttachmentsDeleteFailed"
    );
  });
});

describe("parseAttachmentIds", () => {
  it("reads every data-attachment-id of an img, in order, as the web's helper", () => {
    const description = [
      "Intro",
      '<img src="https://blob.test/a.png" alt="a" width="640" height="480" data-attachment-id="id-1" loading="lazy" decoding="async">',
      '<IMG class="x" data-attachment-id="id-2">',
      '<img src="https://blob.test/b.png" data-attachment-id="id-1">',
    ].join("\n");

    expect(parseAttachmentIds(description)).toEqual(["id-1", "id-2", "id-1"]);
  });

  it("skips images without the attribute and attributes outside an img", () => {
    expect(
      parseAttachmentIds(
        '<img src="kazan-pending:k1" alt="new"> ![alt](https://x.test/a.png) <div data-attachment-id="id-9"></div>'
      )
    ).toEqual([]);
  });

  it("reads nothing from an empty description", () => {
    expect(parseAttachmentIds("")).toEqual([]);
    expect(parseAttachmentIds(null)).toEqual([]);
    expect(parseAttachmentIds(undefined)).toEqual([]);
  });
});

describe("visibleTicketAttachments", () => {
  it("leaves out the images the description shows", () => {
    const list = [attachment("id-1"), attachment("id-2"), attachment("id-3")];

    expect(
      visibleTicketAttachments(list, '<img src="u" data-attachment-id="id-2">').map((a) => a.id)
    ).toEqual(["id-1", "id-3"]);
    expect(visibleTicketAttachments(list, null)).toHaveLength(3);
  });
});
