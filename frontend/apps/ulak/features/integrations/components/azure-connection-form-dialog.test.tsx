import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";
import type { ReactNode } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { ApiError } from "@oba/shared/api/axios";
import type { AzureConnection, AzureConnectionFormValues } from "../schemas";

// A static render cannot type or click, so the form is driven through its
// seams: `useForm` is wrapped to keep the form and the submit handler the
// dialog registers, and the mutation hooks are replaced by recorders that keep
// the options the dialog passes to `mutate`.
type MutateOptions = { onSuccess?: () => void; onError?: (error: Error) => void };
type FormLike = {
  control: {
    _formState: { errors: Record<string, { message?: string; server?: { message?: string } }> };
  };
};

let form: FormLike;
let submitHandler: (values: AzureConnectionFormValues) => void;
const hookOptions: unknown[] = [];
const created: { data: unknown; options: MutateOptions }[] = [];
const updated: { variables: unknown; options: MutateOptions }[] = [];
const closed: boolean[] = [];
const toasts: string[] = [];

const actualIntl = { ...(await import("next-intl")) };
const actualHookForm = { ...(await import("react-hook-form")) };
const actualDialog = { ...(await import("@oba/ui/sky/dialog")) };
const actualSonner = { ...(await import("sonner")) };
const actualHooks = { ...(await import("../hooks/use-azure-connections")) };

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
mock.module("sonner", () => ({
  ...actualSonner,
  toast: { success: () => undefined, error: (message: string) => void toasts.push(message) },
}));
mock.module("../hooks/use-azure-connections", () => ({
  ...actualHooks,
  useCreateAzureConnection: (_: string, options: unknown) => {
    hookOptions.push(options);
    return {
      isPending: false,
      mutate: (data: unknown, mutateOptions: MutateOptions) =>
        created.push({ data, options: mutateOptions }),
    };
  },
  useUpdateAzureConnection: (_: string, options: unknown) => {
    hookOptions.push(options);
    return {
      isPending: false,
      mutate: (variables: unknown, mutateOptions: MutateOptions) =>
        updated.push({ variables, options: mutateOptions }),
    };
  },
}));

afterAll(() => {
  mock.module("next-intl", () => actualIntl);
  mock.module("react-hook-form", () => actualHookForm);
  mock.module("@oba/ui/sky/dialog", () => actualDialog);
  mock.module("sonner", () => actualSonner);
  mock.module("../hooks/use-azure-connections", () => actualHooks);
});

const { AzureConnectionFormDialog } = await import("./azure-connection-form-dialog");

const TENANT_ID = "8f0e2c1a-4b5d-4e6f-9a7b-1c2d3e4f5a6b";
const CLIENT_ID = "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d";

const stored: AzureConnection = {
  id: "c0a80101-0000-4000-8000-000000000001",
  name: "Production tenant",
  tenantId: TENANT_ID,
  clientId: CLIENT_ID,
  status: "CONNECTED",
  lastVerifiedAt: "2026-11-27T04:30:00Z",
  lastError: null,
  createdAt: "2026-11-20T09:00:00Z",
  subscriptions: [],
  setup: {
    issuer: "https://api.example.com",
    subject: "oba:organization:org-1:connection:c0a80101-0000-4000-8000-000000000001",
    audience: "api://AzureADTokenExchange",
  },
};

const values: AzureConnectionFormValues = {
  name: " Production tenant ",
  tenantId: TENANT_ID,
  clientId: "",
};

function render(target: AzureConnection | null): string {
  return renderToStaticMarkup(
    <AzureConnectionFormDialog
      organizationId="org-1"
      connection={target}
      open
      onOpenChange={(open) => void closed.push(open)}
    />
  );
}

beforeEach(() => {
  hookOptions.length = 0;
  created.length = 0;
  updated.length = 0;
  closed.length = 0;
  toasts.length = 0;
});

describe("AzureConnectionFormDialog", () => {
  test("create mode starts empty and shows the three fields", () => {
    const html = render(null);

    expect(html).toContain("form.createTitle");
    expect(html).toContain("form.create");
    for (const field of ["name", "tenantId", "clientId"]) {
      expect(html).toContain(`form.${field}Label`);
    }
    expect(html).not.toContain(TENANT_ID);
  });

  test("edit mode starts from the stored values", () => {
    const html = render(stored);

    expect(html).toContain("form.editTitle");
    expect(html).toContain('value="Production tenant"');
    expect(html).toContain(`value="${TENANT_ID}"`);
    expect(html).toContain(`value="${CLIENT_ID}"`);
  });

  test("both mutations skip the toast for the codes shown on the form", () => {
    render(stored);

    const expected = {
      formErrorCodes: ["AZR_NOT_002", "AZR_STA_003", "AZR_STA_004", "GEN_VAL_001"],
    };
    expect(hookOptions).toEqual([expected, expected]);
  });

  test("create sends the trimmed values with null for an empty client id", () => {
    render(null);

    submitHandler(values);

    expect(created).toHaveLength(1);
    expect(created[0]!.data).toEqual({
      name: "Production tenant",
      tenantId: TENANT_ID,
      clientId: null,
    });
    expect(updated).toHaveLength(0);
  });

  test("edit sends the connection id with the full request", () => {
    render(stored);

    submitHandler({ ...values, clientId: CLIENT_ID });

    expect(updated).toHaveLength(1);
    expect(updated[0]!.variables).toEqual({
      connectionId: stored.id,
      data: { name: "Production tenant", tenantId: TENANT_ID, clientId: CLIENT_ID },
    });
    expect(created).toHaveLength(0);
  });

  test("AZR_STA_004 sets the name field error", () => {
    render(null);
    submitHandler(values);

    created[0]!.options.onError!(new ApiError("taken", { status: 409, code: "AZR_STA_004" }));

    expect(form.control._formState.errors.name?.message).toBe("form.nameTaken");
  });

  test("AZR_STA_003 sets the client id field error", () => {
    render(null);
    submitHandler(values);

    created[0]!.options.onError!(new ApiError("taken", { status: 409, code: "AZR_STA_003" }));

    expect(form.control._formState.errors.clientId?.message).toBe("form.identityTaken");
  });

  test("AZR_NOT_002 says on the form that connections are not available here", () => {
    render(null);
    submitHandler(values);

    created[0]!.options.onError!(new ApiError("no issuer", { status: 404, code: "AZR_NOT_002" }));

    expect(form.control._formState.errors.root?.server?.message).toBe("form.unavailable");
    expect(closed).toEqual([]);
  });

  test("GEN_VAL_001 puts the backend's messages on the fields it names", () => {
    render(null);
    submitHandler(values);

    created[0]!.options.onError!(
      new ApiError("invalid", {
        status: 422,
        code: "GEN_VAL_001",
        errorValues: { name: "must not be blank", unknownField: "ignored" },
      })
    );

    expect(form.control._formState.errors.name?.message).toBe("must not be blank");
    expect(toasts).toEqual([]);
  });

  test("GEN_VAL_001 that names no field of the form is shown as a toast", () => {
    render(null);
    submitHandler(values);

    created[0]!.options.onError!(
      new ApiError("invalid", { status: 422, code: "GEN_VAL_001", errorValues: {} })
    );

    expect(toasts).toEqual(["createError"]);
  });

  test("AZR_NOT_001 on save closes the dialog", () => {
    render(stored);
    submitHandler(values);

    updated[0]!.options.onError!(new ApiError("gone", { status: 404, code: "AZR_NOT_001" }));

    expect(closed).toEqual([false]);
  });

  test("another error sets no field error and leaves the dialog open", () => {
    render(null);
    submitHandler(values);

    created[0]!.options.onError!(new ApiError("boom", { status: 403, code: "ORG_SEC_003" }));

    expect(form.control._formState.errors.name).toBeUndefined();
    expect(closed).toEqual([]);
  });

  test("a saved connection closes the dialog", () => {
    render(stored);
    submitHandler(values);

    updated[0]!.options.onSuccess!();

    expect(closed).toEqual([false]);
  });
});
