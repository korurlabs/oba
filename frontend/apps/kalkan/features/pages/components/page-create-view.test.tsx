import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";
import type { ReactNode } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import type { UseFormReturn } from "react-hook-form";
import { NextIntlClientProvider } from "next-intl";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ApiError } from "@oba/shared/api/axios";
import en from "@/locales/en.json";
import type { PageCreateRequest, PageDetail, PageListItem } from "../schemas";
import type { PageFormValues } from "../schemas/page-form";

// The tree, the role, the `?parent=` value and the create are fixed values;
// the view, its states and the messages are real. The form is a stand-in that
// records what the view hands it, so a test submits through the view's own
// onSubmit.
type TreeState = { data?: PageListItem[]; error?: unknown };
type FormProps = {
  mode: string;
  initialValues: PageFormValues;
  parentOptions?: { value: string }[];
  onSubmit: (values: PageFormValues, form: UseFormReturn<PageFormValues>) => void;
};
type MutateOptions = {
  onSuccess: (response: { data?: PageDetail }) => void;
  onError: (error: unknown) => void;
};

let treeState: TreeState;
let parentParam: string | null;
let isMember: boolean;
let formProps: FormProps | null;
let creates: { request: PageCreateRequest; options: MutateOptions }[];
let pushed: string[];

const actualPages = { ...(await import("../hooks/use-pages")) };
const actualRoles = { ...(await import("@/hooks/use-is-organization-admin")) };
const actualNavigation = { ...(await import("@oba/shared/libs/i18n/navigation")) };
const actualNuqs = { ...(await import("nuqs")) };
const actualForm = { ...(await import("./page-form")) };

mock.module("../hooks/use-pages", () => ({
  ...actualPages,
  usePageTree: () => ({
    data: treeState.data ? { data: treeState.data } : undefined,
    isPending: treeState.data === undefined && treeState.error === undefined,
    isError: treeState.error !== undefined,
    error: treeState.error ?? null,
    refetch: async () => undefined,
  }),
  useCreatePage: () => ({
    mutate: (request: PageCreateRequest, options: MutateOptions) => {
      creates.push({ request, options });
    },
    isPending: false,
    isSuccess: false,
  }),
}));
mock.module("@/hooks/use-is-organization-admin", () => ({
  ...actualRoles,
  useIsOrganizationMember: () => isMember,
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
mock.module("nuqs", () => ({
  ...actualNuqs,
  useQueryState: () => [parentParam, () => undefined],
}));
mock.module("./page-form", () => ({
  ...actualForm,
  PageForm: (props: FormProps) => {
    formProps = props;
    return <div data-form={props.mode} data-parent={props.initialValues.parentId} />;
  },
}));

afterAll(() => {
  mock.module("../hooks/use-pages", () => actualPages);
  mock.module("@/hooks/use-is-organization-admin", () => actualRoles);
  mock.module("@oba/shared/libs/i18n/navigation", () => actualNavigation);
  mock.module("nuqs", () => actualNuqs);
  mock.module("./page-form", () => actualForm);
});

const { PageCreateView } = await import("./page-create-view");

const ROOT: PageListItem = {
  id: "root",
  parentId: null,
  rootId: null,
  slug: "policies",
  path: "policies",
  position: 0,
  compliance: false,
  titles: { EN: "Policies" },
  level: "EDIT",
};
const CHILD: PageListItem = {
  ...ROOT,
  id: "access",
  parentId: "root",
  rootId: "root",
  slug: "access",
  path: "policies/access",
  titles: { EN: "Access control" },
  level: null,
};
const READ_ONLY_ROOT: PageListItem = {
  ...ROOT,
  id: "controls",
  slug: "controls",
  path: "controls",
  position: 1,
  level: "VIEW",
};

function render(): string {
  return renderToStaticMarkup(
    <QueryClientProvider client={new QueryClient()}>
      <NextIntlClientProvider locale="en" messages={en} timeZone="UTC">
        <PageCreateView />
      </NextIntlClientProvider>
    </QueryClientProvider>
  );
}

beforeEach(() => {
  treeState = { data: [ROOT, CHILD, READ_ONLY_ROOT] };
  parentParam = null;
  isMember = true;
  formProps = null;
  creates = [];
  pushed = [];
});

describe("page create view states", () => {
  test("shows the outline while the tree loads", () => {
    treeState = {};
    expect(render()).toContain(`aria-label="${en.pageForm.loading}"`);
  });

  test("shows a failed tree with a retry", () => {
    treeState = { error: new ApiError("Boom", { status: 500 }) };
    const html = render();
    expect(html).toContain(en.pageForm.treeLoadError);
    expect(html).toContain(en.pageForm.retry);
  });

  test("tells a guest there is no place to add a page, with no form", () => {
    isMember = false;
    const html = render();
    expect(html).toContain(en.pageForm.noParent.title);
    expect(html).not.toContain("data-form");
  });

  test("tells a member without EDIT on any section there is no place to add a page", () => {
    treeState = { data: [READ_ONLY_ROOT] };
    expect(render()).toContain(en.pageForm.noParent.title);
  });
});

describe("page create view parent", () => {
  test("offers only the pages of editable sections", () => {
    render();
    expect(formProps?.parentOptions?.map((option) => option.value)).toEqual(["root", "access"]);
  });

  test("starts under the page the user came from and leads back to it", () => {
    parentParam = "access";
    const html = render();
    expect(html).toContain('data-parent="access"');
    expect(html).toContain('href="/pages/access"');
  });

  test("starts without a parent when ?parent= names a page the user cannot add under", () => {
    parentParam = "controls";
    const html = render();
    expect(html).toContain('data-parent=""');
    expect(html).not.toContain('href="/pages/controls"');
  });
});

describe("page create view save", () => {
  test("sends the new page and opens it once saved", () => {
    parentParam = "access";
    render();
    if (formProps === null) throw new Error("The view rendered no form");
    const values = structuredClone(formProps.initialValues);
    values.slug = " review ";
    values.translations.EN = { title: "Review", body: "" };
    const errors: string[] = [];
    const form = {
      setError: (field: string) => {
        errors.push(field);
      },
    } as unknown as UseFormReturn<PageFormValues>;

    formProps.onSubmit(values, form);
    const create = creates[0];
    if (create === undefined) throw new Error("The view sent no create");

    expect(create.request).toEqual({
      parentId: "access",
      slug: "review",
      compliance: false,
      translations: [{ locale: "EN", title: "Review", body: "" }],
    });
    create.options.onSuccess({ data: { id: "new-page" } as PageDetail });
    expect(pushed).toEqual(["/pages/new-page"]);

    create.options.onError(new ApiError("Taken", { status: 409, code: "KLK_STA_001" }));
    expect(errors).toEqual(["slug"]);
  });
});
