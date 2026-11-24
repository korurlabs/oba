import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";
import type { ReactNode } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import type { UseFormReturn } from "react-hook-form";
import { NextIntlClientProvider } from "next-intl";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ApiError } from "@oba/shared/api/axios";
import en from "@/locales/en.json";
import type { PageDetail, PageUpdateRequest } from "../schemas";
import type { PageFormValues } from "../schemas/page-form";

// The page and the update are fixed values; the view, its states and the
// messages are real. The form is a stand-in that records what the view hands
// it, so a test submits through the view's own onSubmit. The page renders on
// the server only, so a state set after a save (the conflict notice) is not
// re-rendered: the notice is rendered on its own.
type PageState = { data?: PageDetail; error?: unknown };
type FormProps = {
  mode: string;
  isRoot?: boolean;
  initialValues: PageFormValues;
  notice?: ReactNode;
  onSubmit: (values: PageFormValues, form: UseFormReturn<PageFormValues>) => void;
};
type MutateOptions = { onSuccess: () => void; onError: (error: unknown) => void };

let pageState: PageState;
let formProps: FormProps | null;
let updates: { variables: { pageId: string; data: PageUpdateRequest }; options: MutateOptions }[];
let pushed: string[];

const actualPages = { ...(await import("../hooks/use-pages")) };
const actualNavigation = { ...(await import("@oba/shared/libs/i18n/navigation")) };
const actualForm = { ...(await import("./page-form")) };

mock.module("../hooks/use-pages", () => ({
  ...actualPages,
  usePage: () => ({
    data: pageState.data ? { data: pageState.data } : undefined,
    isError: pageState.error !== undefined,
    error: pageState.error ?? null,
    refetch: async () => undefined,
  }),
  useUpdatePage: () => ({
    mutate: (variables: { pageId: string; data: PageUpdateRequest }, options: MutateOptions) => {
      updates.push({ variables, options });
    },
    isPending: false,
    isSuccess: false,
  }),
}));
mock.module("@oba/shared/libs/i18n/navigation", () => ({
  ...actualNavigation,
  useRouter: () => ({
    push: (href: string) => {
      pushed.push(href);
    },
  }),
  Link: ({ href, children }: { href: string; children: ReactNode }) => (
    <a href={href}>{children}</a>
  ),
}));
mock.module("./page-form", () => ({
  ...actualForm,
  PageForm: (props: FormProps) => {
    formProps = props;
    return (
      <div data-form={props.mode} data-root={String(props.isRoot)}>
        {props.notice}
      </div>
    );
  },
}));

afterAll(() => {
  mock.module("../hooks/use-pages", () => actualPages);
  mock.module("@oba/shared/libs/i18n/navigation", () => actualNavigation);
  mock.module("./page-form", () => actualForm);
});

const { PageEditView, VersionConflictNotice } = await import("./page-edit-view");

function detail(overrides: Partial<PageDetail> = {}): PageDetail {
  return {
    id: "child",
    parentId: "root",
    rootId: "root",
    slug: "review",
    path: "policies/review",
    compliance: false,
    version: 4,
    level: "EDIT",
    updatedAt: "2026-11-20T10:00:00Z",
    translations: [
      {
        locale: "EN",
        title: "Access review",
        body: "# Review",
        updatedAt: "2026-11-20T10:00:00Z",
        updatedByName: null,
      },
    ],
    ...overrides,
  };
}

function wrap(node: ReactNode): string {
  return renderToStaticMarkup(
    <QueryClientProvider client={new QueryClient()}>
      <NextIntlClientProvider locale="en" messages={en} timeZone="UTC">
        {node}
      </NextIntlClientProvider>
    </QueryClientProvider>
  );
}

function render(): string {
  return wrap(<PageEditView pageId="child" />);
}

/** A form that records the field errors the view puts on it. */
function recordingForm() {
  const errors: { field: string; message: string | undefined }[] = [];
  const form = {
    setError: (field: string, error: { message?: string }) => {
      errors.push({ field, message: error.message });
    },
  } as unknown as UseFormReturn<PageFormValues>;
  return { form, errors };
}

/** Submits the form the view rendered with `edit` applied to its loaded values. */
function submit(edit: (values: PageFormValues) => void = () => undefined) {
  if (formProps === null) throw new Error("The view rendered no form");
  const values = structuredClone(formProps.initialValues);
  edit(values);
  const { form, errors } = recordingForm();
  formProps.onSubmit(values, form);
  const update = updates.at(-1);
  if (update === undefined) throw new Error("The view sent no update");
  return { update, errors };
}

beforeEach(() => {
  pageState = { data: detail() };
  formProps = null;
  updates = [];
  pushed = [];
});

describe("page edit view states", () => {
  test("shows the outline while the page loads", () => {
    pageState = {};
    expect(render()).toContain(`aria-label="${en.pages.view.loading}"`);
  });

  test("shows a failed load with its text and a retry", () => {
    pageState = { error: new ApiError("Forbidden", { status: 403, code: "KLK_SEC_001" }) };
    const html = render();
    expect(html).toContain(en.pages.errors.levelTooLow);
    expect(html).toContain(en.pageForm.retry);
  });

  test("tells a reader without EDIT that the page is read-only, with no form", () => {
    pageState = { data: detail({ level: "VIEW" }) };
    const html = render();
    expect(html).toContain(en.pageForm.readOnly.title);
    expect(html).not.toContain("data-form");
  });
});

describe("page edit view save", () => {
  test("saves a page against the version it loaded, with its new address, and opens it", () => {
    expect(render()).toContain('data-root="false"');

    const { update } = submit((values) => {
      values.slug = "access-review";
    });

    expect(update.variables).toEqual({
      pageId: "child",
      data: {
        slug: "access-review",
        compliance: false,
        version: 4,
        translations: [{ locale: "EN", title: "Access review", body: "# Review" }],
      },
    });
    update.options.onSuccess();
    expect(pushed).toEqual(["/pages/child"]);
  });

  test("keeps the address of a section root fixed and sends none", () => {
    pageState = { data: detail({ parentId: null, rootId: null, slug: "policies" }) };
    expect(render()).toContain('data-root="true"');

    const { update } = submit((values) => {
      values.slug = "renamed";
    });

    expect(update.variables.data.slug).toBeNull();
  });

  test("a version conflict puts no error on a field, so the user's text stays as typed", () => {
    render();
    const { update, errors } = submit();

    update.options.onError(new ApiError("Stale", { status: 409, code: "KLK_STA_002" }));

    expect(errors).toEqual([]);
    expect(pushed).toEqual([]);
  });

  test.each([
    ["KLK_STA_001", 409, en.pages.errors.slugTaken],
    ["KLK_VAL_009", 422, en.pages.errors.rootSlugFixed],
  ])("a %p refusal is shown on the address field", (code, status, message) => {
    render();
    const { update, errors } = submit();

    update.options.onError(new ApiError("Refused", { status, code }));

    expect(errors).toEqual([{ field: "slug", message }]);
  });
});

describe("version conflict notice", () => {
  test("says the page changed and offers the reload", () => {
    const html = wrap(<VersionConflictNotice reloading={false} error={null} onReload={() => {}} />);
    expect(html).toContain(en.pageForm.conflict.title);
    expect(html).toContain(en.pageForm.conflict.reload);
    expect(html).not.toContain('disabled=""');
  });

  test("disables the reload while it runs and shows a failed reload", () => {
    const html = wrap(
      <VersionConflictNotice reloading error="The page could not be loaded" onReload={() => {}} />
    );
    expect(html).toContain(en.pageForm.conflict.reloading);
    expect(html).toContain('disabled=""');
    expect(html).toContain("The page could not be loaded");
  });
});
