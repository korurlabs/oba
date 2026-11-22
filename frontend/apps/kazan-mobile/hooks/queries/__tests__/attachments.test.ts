import { afterAll, beforeAll, beforeEach, describe, expect, it, mock } from "bun:test";
import { AxiosError, type AxiosResponse, type InternalAxiosRequestConfig } from "axios";

import type { ChatLocalFile } from "@/lib/schemas/chat";

// Bun cannot parse react-native's Flow source; lib/api.ts reads only Platform.OS.
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

const { ORGANIZATION_HEADER, api, isApiError } = await import("@/lib/api");
const { setActiveOrganizationId } = await import("@/lib/organizationStore");
const {
  attachmentKeys,
  deleteAttachment,
  listTicketAttachments,
  ticketAttachmentExists,
  uploadTicketAttachments,
} = await import("@/hooks/queries/attachments");

/**
 * Records each part as appended. Bun's FormData turns React Native's
 * `{ uri, name, type }` and `{ string, type }` parts into "[object Object]",
 * so the parts are checked on this stand-in, which axios still treats as
 * form data (its `Symbol.toStringTag` is "FormData").
 */
class RecordingFormData {
  readonly parts: [string, unknown][] = [];

  append(name: string, value: unknown): void {
    this.parts.push([name, value]);
  }

  get [Symbol.toStringTag](): string {
    return "FormData";
  }
}

const realFormData: typeof FormData = globalThis.FormData;

beforeAll(() => {
  globalThis.FormData = RecordingFormData as unknown as typeof FormData;
});

afterAll(() => {
  globalThis.FormData = realFormData;
});

const TICKET_ID = "7d3f7f58-2f0b-4a8e-9c3e-1d2b3c4d5e6f";
const ATTACHMENT_ID = "b0000000-0000-0000-0000-000000000001";

function localFile(overrides: Partial<ChatLocalFile> = {}): ChatLocalFile {
  return {
    key: "k1",
    uri: "file:///cache/shot.png",
    name: "shot.png",
    mimeType: "image/png",
    size: 2048,
    width: 640,
    height: 480,
    ...overrides,
  };
}

/** An `AttachmentResponse` as the local backend sends it for a ticket image. */
function attachment(overrides: Record<string, unknown> = {}): Record<string, unknown> {
  return {
    id: ATTACHMENT_ID,
    url: "https://blob.test/village-container/shot.png?sig=x",
    fileName: "shot.png",
    contentType: "image/png",
    fileSize: 2048,
    entityType: "TICKET",
    entityId: TICKET_ID,
    createdAt: "2026-11-18T09:00:00Z",
    width: 640,
    height: 480,
    documentId: null,
    ...overrides,
  };
}

function envelope(data: unknown): Record<string, unknown> {
  return { success: true, message: "ok", data, pagination: null, error: null };
}

let calls: InternalAxiosRequestConfig[] = [];

function answer(status: number, body: unknown): void {
  api.defaults.adapter = async (config: InternalAxiosRequestConfig): Promise<AxiosResponse> => {
    calls.push(config);
    const response: AxiosResponse = {
      config,
      status,
      statusText: String(status),
      headers: {},
      data: body,
    };
    if (status >= 400) {
      throw new AxiosError("refused", AxiosError.ERR_BAD_REQUEST, config, null, response);
    }
    return response;
  };
}

function parts(config: InternalAxiosRequestConfig | undefined): [string, unknown][] {
  return (config?.data as RecordingFormData).parts;
}

beforeEach(async () => {
  await setActiveOrganizationId("org-1");
  calls = [];
});

describe("uploadTicketAttachments", () => {
  it("sends one metadata array and one files part per file, each named as its metadata entry", async () => {
    answer(
      201,
      envelope([
        attachment(),
        attachment({
          id: "b0000000-0000-0000-0000-000000000002",
          fileName: "spec.pdf",
          contentType: "application/pdf",
          width: null,
          height: null,
        }),
      ])
    );

    const created = await uploadTicketAttachments(TICKET_ID, [
      localFile(),
      localFile({
        key: "k2",
        uri: "file:///docs/spec.pdf",
        name: "spec.pdf",
        mimeType: "application/pdf",
      }),
    ]);

    expect(created.map((item) => item.fileName)).toEqual(["shot.png", "spec.pdf"]);
    expect(calls).toHaveLength(1);
    expect(calls[0]?.method).toBe("post");
    expect(calls[0]?.url).toBe("/storage/attachments");
    expect(calls[0]?._silentErrors).toBe(true);
    expect(calls[0]?.headers.get(ORGANIZATION_HEADER)).toBe("org-1");
    const sent = parts(calls[0]);
    expect(sent.map(([name]) => name)).toEqual(["metadata", "files", "files"]);
    const metadata = sent[0]?.[1] as { string: string; type: string };
    expect(metadata.type).toBe("application/json");
    expect(JSON.parse(metadata.string)).toEqual([
      { fileName: "shot.png", entityType: "TICKET", entityId: TICKET_ID },
      { fileName: "spec.pdf", entityType: "TICKET", entityId: TICKET_ID },
    ]);
    expect(sent[1]?.[1]).toEqual({
      uri: "file:///cache/shot.png",
      name: "shot.png",
      type: "image/png",
    });
    expect(sent[2]?.[1]).toEqual({
      uri: "file:///docs/spec.pdf",
      name: "spec.pdf",
      type: "application/pdf",
    });
  });

  it("sends a file of unknown type as bytes", async () => {
    answer(201, envelope([attachment()]));

    await uploadTicketAttachments(TICKET_ID, [localFile({ mimeType: null })]);

    expect(parts(calls[0])[1]?.[1]).toMatchObject({ type: "application/octet-stream" });
  });

  it("sends nothing for no files", async () => {
    answer(201, envelope([]));

    expect(await uploadTicketAttachments(TICKET_ID, [])).toEqual([]);
    expect(calls).toHaveLength(0);
  });

  it("rejects a duplicate as its error code with the file name", async () => {
    answer(400, {
      success: false,
      message: "An attachment with the same content is already attached to this entity.",
      data: null,
      pagination: null,
      error: {
        code: "STG_VAL_005",
        message: "An attachment with the same content is already attached to this entity.",
        errorValues: { fileName: "shot.png", fileHash: "abc" },
      },
    });

    const error: unknown = await uploadTicketAttachments(TICKET_ID, [localFile()]).catch(
      (caught: unknown) => caught
    );

    expect(isApiError(error, "STG_VAL_005")).toBe(true);
    expect(isApiError(error) && error.errorValues.fileName).toBe("shot.png");
  });
});

describe("listTicketAttachments", () => {
  it("reads the ticket's attachments by entity type and id, silently", async () => {
    answer(200, envelope([attachment()]));

    const list = await listTicketAttachments(TICKET_ID);

    expect(list).toHaveLength(1);
    expect(list[0]?.width).toBe(640);
    expect(calls[0]?.method).toBe("get");
    expect(calls[0]?.url).toBe("/storage/attachments");
    expect(calls[0]?.params).toEqual({ entityType: "TICKET", entityId: TICKET_ID });
    expect(calls[0]?._silentErrors).toBe(true);
  });

  it("accepts an attachment stored before dimensions were recorded", async () => {
    answer(200, envelope([attachment({ width: undefined, height: undefined, contentType: null })]));

    const list = await listTicketAttachments(TICKET_ID);

    expect(list[0]?.width).toBeUndefined();
  });
});

describe("ticketAttachmentExists", () => {
  it("sends one metadata object without the ticket and the file as `file`", async () => {
    answer(200, envelope({ exists: true }));

    expect(await ticketAttachmentExists(localFile())).toBe(true);
    expect(calls[0]?.url).toBe("/storage/attachments/exists");
    expect(calls[0]?._silentErrors).toBe(true);
    const sent = parts(calls[0]);
    expect(sent.map(([name]) => name)).toEqual(["metadata", "file"]);
    const metadata = sent[0]?.[1] as { string: string; type: string };
    expect(metadata.type).toBe("application/json");
    expect(JSON.parse(metadata.string)).toEqual({ fileName: "shot.png", entityType: "TICKET" });
    expect(sent[1]?.[1]).toEqual({
      uri: "file:///cache/shot.png",
      name: "shot.png",
      type: "image/png",
    });
  });

  it("answers false for new content", async () => {
    answer(200, envelope({ exists: false }));

    expect(await ticketAttachmentExists(localFile())).toBe(false);
  });
});

describe("deleteAttachment", () => {
  it("deletes the attachment by id, silently", async () => {
    answer(200, envelope(null));

    await deleteAttachment(ATTACHMENT_ID);

    expect(calls[0]?.method).toBe("delete");
    expect(calls[0]?.url).toBe(`/storage/attachments/${ATTACHMENT_ID}`);
    expect(calls[0]?._silentErrors).toBe(true);
  });

  it("rejects a missing attachment as a not-found error", async () => {
    answer(404, {
      success: false,
      message: "Storage file not found.",
      data: null,
      pagination: null,
      error: { code: "STG_NOT_001", message: "Storage file not found.", errorValues: {} },
    });

    const error: unknown = await deleteAttachment(ATTACHMENT_ID).catch((caught: unknown) => caught);

    expect(isApiError(error) && error.kind).toBe("not-found");
  });
});

describe("attachmentKeys", () => {
  it("scopes a ticket's list by organization", () => {
    expect(attachmentKeys.list("org-1", TICKET_ID)).toEqual([
      "attachments",
      "org-1",
      "ticket",
      TICKET_ID,
    ]);
  });
});
