import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";
import type { ReactNode } from "react";
import { renderToStaticMarkup } from "react-dom/server";

// A static render cannot pick options or click. The dialog is driven through
// its seams: `useForm` is wrapped to keep the submit handler the dialog
// registers, and the grant mutation is a recorder that keeps the request body.
type Values = { app: string; targetType: "USER" | "GROUP"; targetId: string };

let submitHandler: (values: Values) => void;
const sent: unknown[] = [];

const actualIntl = { ...(await import("next-intl")) };
const actualHookForm = { ...(await import("react-hook-form")) };
const actualDialog = { ...(await import("@oba/ui/sky/dialog")) };
const actualAccess = { ...(await import("../hooks/use-app-access")) };
const actualGroups = { ...(await import("../hooks/use-organization-groups")) };
const actualMembers = { ...(await import("../hooks/use-organization-members")) };

mock.module("next-intl", () => ({
  ...actualIntl,
  useTranslations: () => (key: string) => key,
}));
mock.module("react-hook-form", () => ({
  ...actualHookForm,
  useForm: (...args: Parameters<typeof actualHookForm.useForm>) => {
    const form = actualHookForm.useForm(...args);
    const handleSubmit = form.handleSubmit;
    form.handleSubmit = ((onValid: typeof submitHandler) => {
      submitHandler = onValid;
      return handleSubmit(onValid as never);
    }) as typeof handleSubmit;
    return form;
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
mock.module("../hooks/use-app-access", () => ({
  ...actualAccess,
  useCreateAppGrant: () => ({
    isPending: false,
    mutate: (body: unknown) => void sent.push(body),
  }),
}));
mock.module("../hooks/use-organization-groups", () => ({
  ...actualGroups,
  useOrganizationGroups: () => ({ data: { data: [] }, isLoading: false, isError: false }),
}));
mock.module("../hooks/use-organization-members", () => ({
  ...actualMembers,
  useOrganizationMembers: () => ({ data: { data: [] }, isLoading: false, isError: false }),
}));

afterAll(() => {
  mock.module("next-intl", () => actualIntl);
  mock.module("react-hook-form", () => actualHookForm);
  mock.module("@oba/ui/sky/dialog", () => actualDialog);
  mock.module("../hooks/use-app-access", () => actualAccess);
  mock.module("../hooks/use-organization-groups", () => actualGroups);
  mock.module("../hooks/use-organization-members", () => actualMembers);
});

const { GrantAppDialog } = await import("./grant-app-dialog");

function render(): string {
  return renderToStaticMarkup(
    <GrantAppDialog organizationId="org-1" open onOpenChange={() => undefined} />
  );
}

beforeEach(() => {
  sent.length = 0;
});

describe("GrantAppDialog", () => {
  test("shows the app, target type and target fields", () => {
    const html = render();

    expect(html).toContain("access.grantTitle");
    expect(html).toContain("access.app");
    expect(html).toContain("access.targetType");
    expect(html).toContain("access.grant");
  });

  test("a member target sends userId and no groupId", () => {
    render();

    submitHandler({ app: "KAZAN", targetType: "USER", targetId: "u-1" });

    expect(sent).toEqual([{ app: "KAZAN", userId: "u-1" }]);
    expect(Object.keys(sent[0] as object)).not.toContain("groupId");
  });

  test("a group target sends groupId and no userId", () => {
    render();

    submitHandler({ app: "KAYA", targetType: "GROUP", targetId: "g-1" });

    expect(sent).toEqual([{ app: "KAYA", groupId: "g-1" }]);
    expect(Object.keys(sent[0] as object)).not.toContain("userId");
  });
});
