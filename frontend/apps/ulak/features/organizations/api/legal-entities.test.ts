import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";

// The legal entity endpoints carry no organization in the path. The client is
// replaced by a recorder, so the test reads which organization each call names.
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
  shortName: "Korur NL",
  legalName: "Korur B.V.",
  country: "NLD",
  registrationNumber: null,
  vatNumber: null,
  street: null,
  postalCode: null,
  city: null,
  governingLaw: null,
  court: null,
};

beforeEach(() => {
  calls.length = 0;
});

describe("legal entity API", () => {
  test("every call names the viewed organization in X-Organization-ID", async () => {
    await api.listLegalEntities(ORG);
    await api.getLegalEntity(ORG, "le-1");
    await api.createLegalEntity(ORG, body);
    await api.updateLegalEntity(ORG, "le-1", body);
    await api.deleteLegalEntity(ORG, "le-1");

    expect(calls.map((call) => [call.method, call.url])).toEqual([
      ["GET", "/legal-entities"],
      ["GET", "/legal-entities/le-1"],
      ["POST", "/legal-entities"],
      ["PUT", "/legal-entities/le-1"],
      ["DELETE", "/legal-entities/le-1"],
    ]);
    expect(calls.every((call) => call.config?.headers?.["X-Organization-ID"] === ORG)).toBe(true);
  });

  test("create and update send the body as given", async () => {
    await api.createLegalEntity(ORG, body);
    await api.updateLegalEntity(ORG, "le-1", body);

    expect(calls.map((call) => call.body)).toEqual([body, body]);
  });
});
