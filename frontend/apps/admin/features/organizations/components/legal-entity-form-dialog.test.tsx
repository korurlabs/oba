import { afterAll, beforeEach, describe, expect, mock, test } from "bun:test";
import type { ReactNode } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { ApiError } from "@oba/shared/api/axios";
import type { LegalEntityDetail, LegalEntityFormValues, LegalEntityListItem } from "../schemas";

// A static render cannot type or click, so the form is driven through its
// seams: `useForm` is wrapped to keep the form and the submit handler the
// dialog registers, the mutation hooks are replaced by recorders that keep the
// options the dialog passes to `mutate`, and the detail query is a variable.
type MutateOptions = { onSuccess?: () => void; onError?: (error: Error) => void };
type FormLike = {
  setError: (name: string, error: { type: string; message: string }) => void;
  control: { _formState: { errors: Record<string, { message?: string }> } };
};
type DetailResult = {
  data?: { data: LegalEntityDetail };
  isError: boolean;
  refetch: () => void;
};

let form: FormLike;
let submitHandler: (values: LegalEntityFormValues) => void;
let detail: DetailResult;
const detailIds: (string | null | undefined)[] = [];
const hookOptions: unknown[] = [];
const created: { data: unknown; options: MutateOptions }[] = [];
const updated: { variables: unknown; options: MutateOptions }[] = [];
const closed: boolean[] = [];
const toasts: string[] = [];

const actualIntl = { ...(await import("next-intl")) };
const actualHookForm = { ...(await import("react-hook-form")) };
const actualDialog = { ...(await import("@oba/ui/sky/dialog")) };
const actualSonner = { ...(await import("sonner")) };
const actualHooks = { ...(await import("../hooks/use-legal-entities")) };
const actualLoadError = { ...(await import("./organization-load-error")) };

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
mock.module("../hooks/use-legal-entities", () => ({
  ...actualHooks,
  useLegalEntity: (_: string, legalEntityId: string | null) => {
    detailIds.push(legalEntityId);
    return detail;
  },
  useCreateLegalEntity: (_: string, options: unknown) => {
    hookOptions.push(options);
    return {
      isPending: false,
      mutate: (data: unknown, mutateOptions: MutateOptions) =>
        created.push({ data, options: mutateOptions }),
    };
  },
  useUpdateLegalEntity: (_: string, options: unknown) => {
    hookOptions.push(options);
    return {
      isPending: false,
      mutate: (variables: unknown, mutateOptions: MutateOptions) =>
        updated.push({ variables, options: mutateOptions }),
    };
  },
}));
mock.module("./organization-load-error", () => ({
  ...actualLoadError,
  OrganizationLoadError: () => <p>load-error</p>,
}));

afterAll(() => {
  mock.module("next-intl", () => actualIntl);
  mock.module("react-hook-form", () => actualHookForm);
  mock.module("@oba/ui/sky/dialog", () => actualDialog);
  mock.module("sonner", () => actualSonner);
  mock.module("../hooks/use-legal-entities", () => actualHooks);
  mock.module("./organization-load-error", () => actualLoadError);
});

const { LegalEntityFormDialog } = await import("./legal-entity-form-dialog");

const row: LegalEntityListItem = {
  id: "le-1",
  shortName: "Korur NL",
  legalName: "Korur B.V.",
  country: "NLD",
  registrationNumber: "12345678",
  updatedAt: "2026-11-21T09:00:00Z",
};

const stored: LegalEntityDetail = {
  ...row,
  vatNumber: "NL123456789B01",
  street: null,
  postalCode: null,
  city: "Amsterdam",
  governingLaw: null,
  court: null,
  createdAt: "2026-11-20T09:00:00Z",
};

const values: LegalEntityFormValues = {
  shortName: " Korur NL ",
  legalName: "Korur B.V.",
  country: "NLD",
  registrationNumber: "",
  vatNumber: "",
  street: "",
  postalCode: "",
  city: "",
  governingLaw: "",
  court: "",
};

function render(target: LegalEntityListItem | null): string {
  return renderToStaticMarkup(
    <LegalEntityFormDialog
      organizationId="org-1"
      legalEntity={target}
      open
      onOpenChange={(open) => void closed.push(open)}
    />
  );
}

beforeEach(() => {
  detail = { data: { data: stored }, isError: false, refetch: () => undefined };
  detailIds.length = 0;
  hookOptions.length = 0;
  created.length = 0;
  updated.length = 0;
  closed.length = 0;
  toasts.length = 0;
});

describe("LegalEntityFormDialog", () => {
  test("create mode starts empty, loads nothing and offers Create", () => {
    const html = render(null);

    expect(html).toContain("legalEntities.createTitle");
    expect(html).toContain("legalEntities.create");
    expect(html).not.toContain("Korur");
    expect(detailIds).toEqual([null]);
  });

  test("create mode shows every field of the legal entity", () => {
    const html = render(null);

    for (const field of [
      "shortName",
      "legalName",
      "country",
      "registrationNumber",
      "vatNumber",
      "street",
      "postalCode",
      "city",
      "governingLaw",
      "court",
    ]) {
      expect(html).toContain(`legalEntities.${field}Label`);
    }
  });

  test("edit mode loads the legal entity and starts from its stored values", () => {
    const html = render(row);

    expect(detailIds).toEqual(["le-1"]);
    expect(html).toContain("legalEntities.editTitle");
    expect(html).toContain('value="Korur NL"');
    expect(html).toContain('value="NL123456789B01"');
    expect(html).toContain('value="Amsterdam"');
  });

  test("edit mode shows a loader until the legal entity arrives", () => {
    detail = { isError: false, refetch: () => undefined };

    const html = render(row);

    expect(html).toContain("animate-pulse");
    expect(html).not.toContain("legalEntities.shortNameLabel");
  });

  test("edit mode shows a retry when the legal entity could not be loaded", () => {
    detail = { isError: true, refetch: () => undefined };

    expect(render(row)).toContain("load-error");
  });

  test("both mutations skip the toast for LGE_STA_001 and GEN_VAL_001", () => {
    render(row);

    expect(hookOptions).toEqual([
      { formErrorCodes: ["LGE_STA_001", "GEN_VAL_001"] },
      { formErrorCodes: ["LGE_STA_001", "GEN_VAL_001"] },
    ]);
  });

  test("create sends the trimmed values with null for empty optional fields", () => {
    render(null);

    submitHandler(values);

    expect(created).toHaveLength(1);
    expect(created[0]!.data).toEqual({
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
    });
    expect(updated).toHaveLength(0);
  });

  test("edit sends the legal entity id with the full request", () => {
    render(row);

    submitHandler({ ...values, city: "Utrecht" });

    expect(updated).toHaveLength(1);
    expect(updated[0]!.variables).toMatchObject({
      legalEntityId: "le-1",
      data: { shortName: "Korur NL", city: "Utrecht", court: null },
    });
    expect(created).toHaveLength(0);
  });

  test("LGE_STA_001 sets the short name field error", () => {
    render(null);
    submitHandler(values);

    created[0]!.options.onError!(new ApiError("taken", { status: 409, code: "LGE_STA_001" }));

    expect(form.control._formState.errors.shortName?.message).toBe("legalEntities.shortNameTaken");
  });

  test("GEN_VAL_001 puts the backend's messages on the fields it names", () => {
    render(null);
    submitHandler(values);

    created[0]!.options.onError!(
      new ApiError("invalid", {
        status: 422,
        code: "GEN_VAL_001",
        errorValues: { legalName: "must not be blank", unknownField: "ignored" },
      })
    );

    expect(form.control._formState.errors.legalName?.message).toBe("must not be blank");
    expect(toasts).toEqual([]);
  });

  test("GEN_VAL_001 that names no field of the form is shown as a toast", () => {
    render(null);
    submitHandler(values);

    created[0]!.options.onError!(
      new ApiError("invalid", { status: 422, code: "GEN_VAL_001", errorValues: {} })
    );

    expect(toasts).toEqual(["legalEntities.invalid"]);
  });

  test("LGE_NOT_001 on save closes the dialog", () => {
    render(row);
    submitHandler(values);

    updated[0]!.options.onError!(new ApiError("gone", { status: 404, code: "LGE_NOT_001" }));

    expect(closed).toEqual([false]);
  });

  test("another error sets no field error and leaves the dialog open", () => {
    render(null);
    submitHandler(values);

    created[0]!.options.onError!(new ApiError("boom", { status: 403, code: "ORG_SEC_003" }));

    expect(form.control._formState.errors.shortName).toBeUndefined();
    expect(closed).toEqual([]);
  });

  test("a saved legal entity closes the dialog", () => {
    render(row);
    submitHandler(values);

    updated[0]!.options.onSuccess!();

    expect(closed).toEqual([false]);
  });
});
