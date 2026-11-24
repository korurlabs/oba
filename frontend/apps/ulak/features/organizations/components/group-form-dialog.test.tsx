import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";
import type { ReactNode } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { ApiError } from "@oba/shared/api/axios";
import type { OrganizationGroup } from "@oba/shared/features/organizations/schemas";

// A static render cannot type or click, so the form is driven through its
// seams: `useForm` is wrapped to keep the form and the submit handler the
// dialog registers, and the two mutation hooks are replaced by recorders that
// keep the options the dialog passes to `mutate`.
type MutateOptions = { onSuccess?: () => void; onError?: (error: Error) => void };
type FormLike = {
  setError: (name: string, error: { type: string; message: string }) => void;
  control: { _formState: { errors: Record<string, { message?: string }> } };
};

let form: FormLike;
let submitHandler: (values: { name: string; description: string }) => void;
const hookOptions: unknown[] = [];
const created: { data: unknown; options: MutateOptions }[] = [];
const renamed: { variables: unknown; options: MutateOptions }[] = [];
const closed: boolean[] = [];

const actualIntl = { ...(await import("next-intl")) };
const actualHookForm = { ...(await import("react-hook-form")) };
const actualDialog = { ...(await import("@oba/ui/sky/dialog")) };
const actualGroups = { ...(await import("../hooks/use-organization-groups")) };

mock.module("next-intl", () => ({
  ...actualIntl,
  useTranslations: () => (key: string) => key,
}));
mock.module("react-hook-form", () => ({
  ...actualHookForm,
  useForm: (...args: Parameters<typeof actualHookForm.useForm>) => {
    const created = actualHookForm.useForm(...args);
    form = created as unknown as FormLike;
    const handleSubmit = created.handleSubmit;
    created.handleSubmit = ((onValid: typeof submitHandler) => {
      submitHandler = onValid;
      return handleSubmit(onValid as never);
    }) as typeof handleSubmit;
    return created;
  },
}));
mock.module("@oba/ui/sky/dialog", () => ({
  ...actualDialog,
  Dialog: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  DialogContent: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  DialogHeader: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  DialogFooter: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  DialogTitle: ({ children }: { children: ReactNode }) => <h2>{children}</h2>,
  DialogDescription: ({ children }: { children: ReactNode }) => <p>{children}</p>,
}));
mock.module("../hooks/use-organization-groups", () => ({
  ...actualGroups,
  useCreateOrganizationGroup: (_: string, options: unknown) => {
    hookOptions.push(options);
    return {
      isPending: false,
      mutate: (data: unknown, mutateOptions: MutateOptions) =>
        created.push({ data, options: mutateOptions }),
    };
  },
  useUpdateOrganizationGroup: (_: string, options: unknown) => {
    hookOptions.push(options);
    return {
      isPending: false,
      mutate: (variables: unknown, mutateOptions: MutateOptions) =>
        renamed.push({ variables, options: mutateOptions }),
    };
  },
}));

afterAll(() => {
  mock.module("next-intl", () => actualIntl);
  mock.module("react-hook-form", () => actualHookForm);
  mock.module("@oba/ui/sky/dialog", () => actualDialog);
  mock.module("../hooks/use-organization-groups", () => actualGroups);
});

const { GroupFormDialog } = await import("./group-form-dialog");

const group = {
  id: "g-dev",
  name: "Developers",
  description: "Builds things",
  type: "CUSTOM",
  memberCount: 3,
  apps: [],
  createdAt: "2026-11-10T09:00:00Z",
} as OrganizationGroup;

function render(target: OrganizationGroup | null): string {
  return renderToStaticMarkup(
    <GroupFormDialog
      organizationId="org-1"
      group={target}
      open
      onOpenChange={(open) => void closed.push(open)}
    />
  );
}

beforeEach(() => {
  hookOptions.length = 0;
  created.length = 0;
  renamed.length = 0;
  closed.length = 0;
});

describe("GroupFormDialog", () => {
  test("create mode starts empty and offers Create", () => {
    const html = render(null);

    expect(html).toContain("groups.createTitle");
    expect(html).toContain("groups.create");
    expect(html).not.toContain("Developers");
  });

  test("rename mode starts with the current name and description", () => {
    const html = render(group);

    expect(html).toContain("groups.renameTitle");
    expect(html).toContain('value="Developers"');
    expect(html).toContain("Builds things");
  });

  test("a newly mounted dialog shows the renamed group, not the earlier name (the reopen effect itself is not covered)", () => {
    render(group);
    const html = render({ ...group, name: "Engineers" });

    expect(html).toContain('value="Engineers"');
    expect(html).not.toContain('value="Developers"');
  });

  test("both mutations skip the toast for APP_STA_002", () => {
    render(group);

    expect(hookOptions).toEqual([
      { formErrorCodes: ["APP_STA_002"] },
      { formErrorCodes: ["APP_STA_002"] },
    ]);
  });

  test("create sends the trimmed name and a null description for a blank one", () => {
    render(null);

    submitHandler({ name: "  QA  ", description: "   " });

    expect(created).toHaveLength(1);
    expect(created[0]!.data).toEqual({ name: "QA", description: null });
    expect(renamed).toHaveLength(0);
  });

  test("rename sends the group id with the full request", () => {
    render(group);

    submitHandler({ name: "Engineers", description: "" });

    expect(renamed).toHaveLength(1);
    expect(renamed[0]!.variables).toEqual({
      groupId: "g-dev",
      data: { name: "Engineers", description: null },
    });
    expect(created).toHaveLength(0);
  });

  test("APP_STA_002 sets the name field error", () => {
    render(null);
    submitHandler({ name: "Developers", description: "" });

    created[0]!.options.onError!(new ApiError("taken", { status: 409, code: "APP_STA_002" }));

    expect(form.control._formState.errors.name?.message).toBe("groups.nameTaken");
  });

  test("another error sets no field error and leaves the dialog open", () => {
    render(null);
    submitHandler({ name: "Developers", description: "" });

    created[0]!.options.onError!(new ApiError("boom", { status: 500, code: "GEN_API_002" }));

    expect(form.control._formState.errors.name).toBeUndefined();
    expect(closed).toEqual([]);
  });

  test("a saved rename closes the dialog", () => {
    render(group);
    submitHandler({ name: "Engineers", description: "" });

    renamed[0]!.options.onSuccess!();

    expect(closed).toEqual([false]);
  });
});
