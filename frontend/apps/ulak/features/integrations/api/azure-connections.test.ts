import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";

// The Azure connection endpoints carry no organization in the path. The client
// is replaced by a recorder, so the test reads which organization each call names.
type Call = {
  method: string;
  url: string;
  body?: unknown;
  config?: { headers?: Record<string, string> };
};

const calls: Call[] = [];
const reply = { data: { success: true, data: null } };

const actualClient = { ...(await import("@/lib/api/client")) };

mock.module("@/lib/api/client", () => ({
  ...actualClient,
  api: {
    get: async (url: string, config?: Call["config"]) => {
      calls.push({ method: "GET", url, config });
      return reply;
    },
    post: async (url: string, body: unknown, config?: Call["config"]) => {
      calls.push({ method: "POST", url, body, config });
      return reply;
    },
    put: async (url: string, body: unknown, config?: Call["config"]) => {
      calls.push({ method: "PUT", url, body, config });
      return reply;
    },
    delete: async (url: string, config?: Call["config"]) => {
      calls.push({ method: "DELETE", url, config });
      return reply;
    },
  },
}));

afterAll(() => {
  mock.module("@/lib/api/client", () => actualClient);
});

const api = await import("./index");

const ORG = "org-viewed";
const body = {
  name: "Production tenant",
  tenantId: "11111111-1111-4111-8111-111111111111",
  clientId: null,
};

beforeEach(() => {
  calls.length = 0;
});

describe("Azure connection API", () => {
  test("every call names the viewed organization in X-Organization-ID", async () => {
    await api.listAzureConnections(ORG);
    await api.getAzureConnection(ORG, "c-1");
    await api.createAzureConnection(ORG, body);
    await api.updateAzureConnection(ORG, "c-1", body);
    await api.deleteAzureConnection(ORG, "c-1");
    await api.verifyAzureConnection(ORG, "c-1");

    expect(calls.map((call) => [call.method, call.url])).toEqual([
      ["GET", "/azure/connections"],
      ["GET", "/azure/connections/c-1"],
      ["POST", "/azure/connections"],
      ["PUT", "/azure/connections/c-1"],
      ["DELETE", "/azure/connections/c-1"],
      ["POST", "/azure/connections/c-1/verify"],
    ]);
    expect(calls.every((call) => call.config?.headers?.["X-Organization-ID"] === ORG)).toBe(true);
  });

  test("create and update send the body as given, verify sends none", async () => {
    await api.createAzureConnection(ORG, body);
    await api.updateAzureConnection(ORG, "c-1", body);
    await api.verifyAzureConnection(ORG, "c-1");

    expect(calls.map((call) => call.body)).toEqual([body, body, undefined]);
  });
});
