import { afterAll, afterEach, beforeEach, describe, expect, mock, test } from "bun:test";
import { NextIntlClientProvider } from "next-intl";
import en from "@/locales/en.json";
import tr from "@/locales/tr.json";
import { cleanup, renderLive, type Screen } from "@/lib/testing/live-render";
import type { PageCreateRequest } from "../schemas";

// The create hook is a stand-in that records the request and either reports
// success or, as a refused create does, leaves the field alone; the field, its
// schema, the slug and the messages are real.
type Outcome = "created" | "refused";

let outcome: Outcome;
let requests: PageCreateRequest[];
let pushed: string[];

const actualPages = { ...(await import("../hooks/use-pages")) };
const actualNavigation = { ...(await import("@oba/shared/libs/i18n/navigation")) };

mock.module("../hooks/use-pages", () => ({
  ...actualPages,
  useCreatePage: () => ({
    isPending: false,
    mutate: (
      request: PageCreateRequest,
      options?: { onSuccess?: (response: { data?: { id: string } }) => void }
    ) => {
      requests.push(request);
      if (outcome === "created") options?.onSuccess?.({ data: { id: "new-page" } });
    },
  }),
}));
mock.module("@oba/shared/libs/i18n/navigation", () => ({
  ...actualNavigation,
  useRouter: () => ({ push: (href: string) => pushed.push(href), replace: () => undefined }),
}));

afterAll(() => {
  mock.module("../hooks/use-pages", () => actualPages);
  mock.module("@oba/shared/libs/i18n/navigation", () => actualNavigation);
});

const { NewChildPageField } = await import("./new-child-page-field");

let cancelled: number;
let created: number;

async function open(siblingSlugs: string[] = [], locale: "en" | "tr" = "en"): Promise<Screen> {
  return renderLive(
    <NextIntlClientProvider locale={locale} messages={locale === "en" ? en : tr} timeZone="UTC">
      <NewChildPageField
        parentId="parent-1"
        siblingSlugs={siblingSlugs}
        onCancel={() => {
          cancelled += 1;
        }}
        onCreated={() => {
          created += 1;
        }}
      />
    </NextIntlClientProvider>
  );
}

async function type(screen: Screen, name: string): Promise<void> {
  const input = screen.byAttribute("aria-label", en.sidebar.newChildName)[0];
  await input?.change(name);
}

/** Enter in the field: the browser submits the form that holds it. */
async function submit(screen: Screen): Promise<void> {
  await screen.byTag("form")[0]?.submit();
}

beforeEach(() => {
  outcome = "created";
  requests = [];
  pushed = [];
  cancelled = 0;
  created = 0;
});

afterEach(cleanup);

describe("NewChildPageField", () => {
  test("shows a focused name field labelled for the new page", async () => {
    const screen = await open();
    const input = screen.byAttribute("aria-label", en.sidebar.newChildName)[0];
    expect(input).toBeDefined();
    expect(input?.attribute("placeholder")).toBe(en.sidebar.newChildName);
  });

  test("Enter creates the page under the parent with the typed title and a derived slug", async () => {
    const screen = await open();
    await type(screen, "  Ağ Topolojisi ");
    await submit(screen);
    expect(requests).toEqual([
      {
        parentId: "parent-1",
        slug: "ag-topolojisi",
        position: null,
        compliance: false,
        translations: [{ locale: "EN", title: "Ağ Topolojisi", body: "" }],
      },
    ]);
  });

  test("avoids a slug a sibling already holds", async () => {
    const screen = await open(["Network"]);
    await type(screen, "network");
    await submit(screen);
    expect(requests[0]?.slug).toBe("network-2");
  });

  test("writes the title in Turkish for a Turkish interface", async () => {
    const screen = await open([], "tr");
    await screen.byAttribute("aria-label", tr.sidebar.newChildName)[0]?.change("Ağ");
    await submit(screen);
    expect(requests[0]?.translations[0]?.locale).toBe("TR");
  });

  test("closes the field and opens the new page's editor once it exists", async () => {
    const screen = await open();
    await type(screen, "Network");
    await submit(screen);
    expect(created).toBe(1);
    expect(pushed).toEqual(["/pages/new-page/edit"]);
    expect(cancelled).toBe(0);
  });

  test("keeps the field, the name and the page list as they were when the create is refused", async () => {
    outcome = "refused";
    const screen = await open();
    await type(screen, "Network");
    await submit(screen);
    expect(requests).toHaveLength(1);
    expect(created).toBe(0);
    expect(cancelled).toBe(0);
    expect(pushed).toEqual([]);
    const input = screen.byAttribute("aria-label", en.sidebar.newChildName)[0];
    expect(input?.value).toBe("Network");
  });

  test("Enter on an empty name cancels without a request", async () => {
    const screen = await open();
    await type(screen, "   ");
    await submit(screen);
    expect(cancelled).toBe(1);
    expect(requests).toEqual([]);
  });

  test("Escape cancels without a request", async () => {
    const screen = await open();
    await type(screen, "Network");
    await screen.byAttribute("aria-label", en.sidebar.newChildName)[0]?.keyDown("Escape");
    expect(cancelled).toBe(1);
    expect(requests).toEqual([]);
  });

  test("another key does not cancel", async () => {
    const screen = await open();
    await screen.byAttribute("aria-label", en.sidebar.newChildName)[0]?.keyDown("a");
    expect(cancelled).toBe(0);
  });

  test("a name over 200 characters shows its error, keeps the field and sends nothing", async () => {
    const screen = await open();
    await type(screen, "a".repeat(201));
    await submit(screen);
    expect(requests).toEqual([]);
    expect(screen.text()).toContain(en.sidebar.newChildNameMax);
    expect(cancelled).toBe(0);
    const input = screen.byAttribute("aria-label", en.sidebar.newChildName)[0];
    expect(input?.attribute("aria-invalid")).toBe("true");
  });

  test("typing again clears the error", async () => {
    const screen = await open();
    await type(screen, "a".repeat(201));
    await submit(screen);
    await type(screen, "a".repeat(20));
    expect(screen.text()).not.toContain(en.sidebar.newChildNameMax);
  });
});
