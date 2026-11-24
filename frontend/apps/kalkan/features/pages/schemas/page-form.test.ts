import { describe, expect, test } from "bun:test";
import { ApiError } from "@oba/shared/api/axios";
import type { PageDetail, PageListItem } from "./index";
import {
  appendToBody,
  createChildPageNameSchema,
  createPageFormSchema,
  emptyPageFormValues,
  isSectionRoot,
  isVersionConflict,
  markdownLink,
  pageFormValuesFromDetail,
  parentOptions,
  slugFromTitle,
  storedLocales,
  toPageCreateRequest,
  toPageUpdateRequest,
  uniqueChildSlug,
  type PageFormValues,
} from "./page-form";

const PARENT = "5c2d1e0f-3a4b-4c5d-8e9f-0a1b2c3d4e5f";
const t = (key: string) => key;

function values(overrides: Partial<PageFormValues> = {}): PageFormValues {
  const base = emptyPageFormValues(PARENT);
  base.slug = "access-policy";
  base.translations.EN = { title: "Access policy", body: "# Access" };
  return { ...base, ...overrides };
}

function messagesOf(result: ReturnType<ReturnType<typeof createPageFormSchema>["safeParse"]>) {
  return result.success ? [] : result.error.issues.map((issue) => issue.message);
}

describe("page form validation", () => {
  test("accepts a page with a title in one language", () => {
    const result = createPageFormSchema(t, { requireParent: true }).safeParse(values());
    expect(result.success).toBe(true);
  });

  test("refuses a page without any title, on both language tabs", () => {
    const form = values();
    form.translations.EN = { title: "  ", body: "" };
    const result = createPageFormSchema(t).safeParse(form);
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.issues.map((issue) => issue.path.join("."))).toEqual([
        "translations.TR.title",
        "translations.EN.title",
      ]);
      expect(messagesOf(result)).toEqual(["errors.titleRequired", "errors.titleRequired"]);
    }
  });

  test("refuses a body without a title in that language", () => {
    const form = values();
    form.translations.TR = { title: "", body: "Metin" };
    expect(messagesOf(createPageFormSchema(t).safeParse(form))).toEqual(["errors.titleForBody"]);
  });

  test("refuses emptying the title of a language the page already has", () => {
    const form = values();
    const result = createPageFormSchema(t, { requiredLocales: ["TR", "EN"] }).safeParse(form);
    expect(messagesOf(result)).toEqual(["errors.titleKept"]);
  });

  test("refuses a new page without a parent", () => {
    const result = createPageFormSchema(t, { requireParent: true }).safeParse(
      values({ parentId: "" })
    );
    expect(messagesOf(result)).toEqual(["errors.parentRequired"]);
  });

  test("refuses an empty slug and one the server pattern rejects", () => {
    expect(messagesOf(createPageFormSchema(t).safeParse(values({ slug: "" })))).toContain(
      "errors.slugRequired"
    );
    expect(messagesOf(createPageFormSchema(t).safeParse(values({ slug: "-bad slug" })))).toEqual([
      "errors.slugInvalid",
    ]);
    expect(createPageFormSchema(t).safeParse(values({ slug: "A.5.1" })).success).toBe(true);
  });

  test("refuses a title over the server limit", () => {
    const form = values();
    form.translations.EN.title = "x".repeat(201);
    expect(messagesOf(createPageFormSchema(t).safeParse(form))).toEqual(["errors.titleMax"]);
  });
});

describe("slugFromTitle", () => {
  test("transliterates Turkish letters and keeps the dots of control ids", () => {
    expect(slugFromTitle("A.5.1 Bilgi güvenliği politikası")).toBe(
      "a.5.1-bilgi-guvenligi-politikasi"
    );
    expect(slugFromTitle("İş sürekliliği & Çalışma")).toBe("is-surekliligi-calisma");
  });

  test("gives a slug the server pattern accepts, or nothing", () => {
    expect(slugFromTitle("  --Access  policy--  ")).toBe("access-policy");
    expect(slugFromTitle("!!!")).toBe("");
    expect(slugFromTitle("a".repeat(150))).toHaveLength(100);
  });
});

describe("page form mapping", () => {
  const stored: PageDetail = {
    id: PARENT,
    parentId: null,
    rootId: null,
    slug: "policies",
    path: "policies",
    compliance: true,
    version: 7,
    level: "EDIT",
    translations: [
      {
        locale: "TR",
        title: "Politikalar",
        body: "Gövde",
        updatedAt: "2026-11-20T10:00:00Z",
        updatedByName: null,
      },
    ],
    updatedAt: "2026-11-20T10:00:00Z",
  };

  test("loads a stored page with an empty form for its missing language", () => {
    const form = pageFormValuesFromDetail(stored);
    expect(form.translations.TR).toEqual({ title: "Politikalar", body: "Gövde" });
    expect(form.translations.EN).toEqual({ title: "", body: "" });
    expect(form.compliance).toBe(true);
    expect(storedLocales(stored)).toEqual(["TR"]);
  });

  test("an update carries the loaded version and only languages with a title", () => {
    const child: PageDetail = { ...stored, parentId: PARENT, rootId: PARENT, slug: "access" };
    const form = pageFormValuesFromDetail(child);
    form.slug = " access-v2 ";
    form.translations.TR.title = " Politikalar v2 ";
    expect(toPageUpdateRequest(form, child)).toEqual({
      slug: "access-v2",
      compliance: true,
      version: 7,
      translations: [{ locale: "TR", title: "Politikalar v2", body: "Gövde" }],
    });
  });

  test("an update of a section root sends no slug, even when the form holds another", () => {
    const form = pageFormValuesFromDetail(stored);
    form.slug = "renamed";
    expect(isSectionRoot(stored)).toBe(true);
    expect(toPageUpdateRequest(form, stored).slug).toBeNull();
  });

  test("a create sends the parent and the trimmed slug", () => {
    const request = toPageCreateRequest(values({ slug: " access-policy " }));
    expect(request.parentId).toBe(PARENT);
    expect(request.slug).toBe("access-policy");
    expect(request.translations).toEqual([
      { locale: "EN", title: "Access policy", body: "# Access" },
    ]);
  });
});

describe("version conflict", () => {
  test("is the stale-version refusal only", () => {
    expect(isVersionConflict(new ApiError("stale", { status: 409, code: "KLK_STA_002" }))).toBe(
      true
    );
    expect(isVersionConflict(new ApiError("taken", { status: 409, code: "KLK_STA_001" }))).toBe(
      false
    );
    expect(isVersionConflict(new Error("network"))).toBe(false);
  });
});

describe("attachment links", () => {
  test("escapes brackets in the name and encodes spaces and parentheses in the address", () => {
    expect(markdownLink("scan [v2].pdf", "https://blob/x/scan (v2).pdf")).toBe(
      "[scan \\[v2\\].pdf](https://blob/x/scan%20%28v2%29.pdf)"
    );
  });

  test("turns markup in the name into text, so a file name never becomes HTML", () => {
    expect(
      markdownLink('<a href="https:evil.example" class="fixed">r&d.pdf', "https://blob/x/f.pdf")
    ).toBe('[&lt;a href="https:evil.example" class="fixed"&gt;r&amp;d.pdf](https://blob/x/f.pdf)');
  });

  test("appends the link as its own paragraph", () => {
    expect(appendToBody("", "[a](b)")).toBe("[a](b)");
    expect(appendToBody("Text\n\n", "[a](b)")).toBe("Text\n\n[a](b)");
  });
});

describe("parentOptions", () => {
  const page = (overrides: Partial<PageListItem> & Pick<PageListItem, "id">): PageListItem => ({
    parentId: null,
    rootId: null,
    slug: overrides.id,
    path: overrides.id,
    position: 0,
    compliance: false,
    titles: { EN: overrides.id },
    level: null,
    ...overrides,
  });

  test("lists the pages of editable sections in tree order, grouped and indented", () => {
    const deep = Array.from({ length: 10 }, (_, index) => `d${index}`).join("/");
    const pages: PageListItem[] = [
      page({ id: "controls", level: "VIEW", position: 0 }),
      page({ id: "policies", level: "EDIT", position: 1 }),
      page({ id: "b", parentId: "policies", rootId: "policies", path: "policies/b", position: 1 }),
      page({ id: "a", parentId: "policies", rootId: "policies", path: "policies/a", position: 0 }),
      page({ id: "a1", parentId: "a", rootId: "policies", path: "policies/a/a1" }),
      page({ id: "deep", parentId: "a1", rootId: "policies", path: deep }),
    ];
    const options = parentOptions(pages, "en");
    expect(options.map((option) => option.value)).toEqual(["policies", "a", "a1", "b"]);
    expect(options.map((option) => option.meta?.depth)).toEqual([0, 1, 2, 1]);
    expect(new Set(options.map((option) => option.group))).toEqual(new Set(["policies"]));
  });
});

describe("uniqueChildSlug", () => {
  test("turns Turkish letters into Latin ones", () => {
    expect(uniqueChildSlug("Ağ Topolojisi", [])).toBe("ag-topolojisi");
    expect(uniqueChildSlug("ŞÇÖĞÜİ ışık", [])).toBe("scogui-isik");
  });

  test("falls back to page when the title holds only punctuation", () => {
    expect(uniqueChildSlug("???", [])).toBe("page");
    expect(uniqueChildSlug("  --  ", [])).toBe("page");
  });

  test("adds -2, -3 until no sibling holds the slug, ignoring case", () => {
    expect(uniqueChildSlug("Network", ["network"])).toBe("network-2");
    expect(uniqueChildSlug("Network", ["Network", "NETWORK-2"])).toBe("network-3");
    expect(uniqueChildSlug("???", ["page"])).toBe("page-2");
  });

  test("keeps 200 characters within the 100 character limit, also with a suffix", () => {
    const title = "a".repeat(200);
    const plain = uniqueChildSlug(title, []);
    expect(plain).toHaveLength(100);
    const suffixed = uniqueChildSlug(title, [plain]);
    expect(suffixed).toHaveLength(100);
    expect(suffixed.endsWith("-2")).toBe(true);
    expect(suffixed).not.toBe(plain);
  });

  test("trims a hyphen the cut leaves before the suffix", () => {
    const title = `${"a".repeat(97)} b`;
    const slug = uniqueChildSlug(title, [uniqueChildSlug(title, [])]);
    expect(slug.length).toBeLessThanOrEqual(100);
    expect(slug).not.toContain("--");
  });
});

describe("createChildPageNameSchema", () => {
  test("accepts a name up to 200 characters and trims it", () => {
    expect(createChildPageNameSchema(t).safeParse("a".repeat(200)).success).toBe(true);
    const parsed = createChildPageNameSchema(t).safeParse("  Network  ");
    expect(parsed.success && parsed.data).toBe("Network");
  });

  test("refuses a name over 200 characters with the message", () => {
    const result = createChildPageNameSchema(t).safeParse("a".repeat(201));
    expect(result.success ? [] : result.error.issues.map((issue) => issue.message)).toEqual([
      "newChildNameMax",
    ]);
  });

  test("refuses an empty name", () => {
    expect(createChildPageNameSchema(t).safeParse("   ").success).toBe(false);
  });
});
