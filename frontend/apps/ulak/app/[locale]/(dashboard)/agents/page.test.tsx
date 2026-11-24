import { afterAll, afterEach, beforeEach, describe, expect, mock, spyOn, test } from "bun:test";
import { renderToStaticMarkup } from "react-dom/server";
import en from "@/locales/en.json";

// The page is a placeholder with no data: it reads only messages. The server
// translator returns the real English texts of the namespace asked for, and
// every channel a request could leave by is watched.
const actualServerIntl = { ...(await import("next-intl/server")) };
const actualAxios = { ...(await import("@/lib/api/client")) };
const namespaces: string[] = [];
let requests: string[];
let fetchSpy: ReturnType<typeof spyOn>;

mock.module("next-intl/server", () => ({
  ...actualServerIntl,
  getTranslations: async (namespace: keyof typeof en) => {
    namespaces.push(String(namespace));
    const messages = en[namespace] as Record<string, string>;
    return (key: string) => messages[key] ?? key;
  },
}));
mock.module("@/lib/api/client", () => ({
  ...actualAxios,
  api: new Proxy(actualAxios.api, {
    get(target, property, receiver) {
      requests.push(String(property));
      return Reflect.get(target, property, receiver);
    },
  }),
}));

afterAll(() => {
  mock.module("next-intl/server", () => actualServerIntl);
  mock.module("@/lib/api/client", () => actualAxios);
});

const { default: AgentsPage, generateMetadata } = await import("./page");

beforeEach(() => {
  requests = [];
  fetchSpy = spyOn(globalThis, "fetch");
});

afterEach(() => {
  fetchSpy.mockRestore();
});

describe("Agents page", () => {
  test("renders its title, description and empty state", async () => {
    const html = renderToStaticMarkup(await AgentsPage());
    expect(html).toContain(`<h1 class="break-words">${en.agents.title}</h1>`);
    expect(html).toContain(en.agents.description);
    expect(html).toContain(en.agents.emptyTitle);
    expect(html).toContain(en.agents.emptyDescription);
  });

  test("sends no request", async () => {
    renderToStaticMarkup(await AgentsPage());
    expect(fetchSpy).not.toHaveBeenCalled();
    expect(requests).toEqual([]);
  });

  test("is titled Agents in the tab", async () => {
    expect(await generateMetadata()).toEqual({ title: en.sidebar.agents });
    expect(namespaces).toContain("sidebar");
  });
});
