import { describe, expect, test } from "bun:test";
import { ApiError } from "@oba/shared/api/axios";
import type { DocumentTemplateListItem } from "@/features/document-templates/schemas";
import type { DocumentListItem } from "@/features/documents/schemas";
import { createDocumentFromTemplateFormSchema } from "../schemas";
import {
  NEW_FROM_TEMPLATE,
  attachableDocuments,
  attachableTemplates,
  documentSnapshotErrorText,
  isDocumentSnapshot,
  snapshotPickerQuery,
  withNewFromTemplate,
} from "./document-snapshots";

const t = (key: string): string => `t:${key}`;

function apiError(code: string | undefined): ApiError {
  return new ApiError("server message", { status: 422, code, errorValues: {} });
}

function document(id: string, ready: boolean): DocumentListItem {
  return {
    id,
    title: `Document ${id}`,
    type: "PROPOSAL",
    language: "NL",
    entityType: "RELATION",
    entityId: "rel-1",
    relationId: "rel-1",
    relationName: "Acme B.V.",
    legalEntityId: "le-1",
    legalEntityShortName: "Korur B.V.",
    templateId: null,
    ready,
    snapshotCount: 0,
    updatedBy: { id: "user-1", displayName: "Merthan Tukus" },
    updatedAt: "2026-11-20T09:15:00Z",
    createdAt: "2026-11-18T12:00:00Z",
  };
}

describe("documentSnapshotErrorText", () => {
  test.each([
    ["DOC_NOT_002", "t:errors.documentNotFound"],
    ["OTK_NOT_011", "t:errors.mailNotFound"],
    ["DOC_VAL_003", "t:errors.noPdf"],
    ["DOC_VAL_004", "t:errors.otherRelation"],
    ["OTK_STA_011", "t:errors.mailSent"],
    ["STG_VAL_005", "t:errors.duplicate"],
    ["APP_SEC_001", "t:errors.noOtakAccess"],
    ["APP_SEC_002", "t:errors.forbidden"],
    ["ORG_SEC_003", "t:errors.forbidden"],
  ])("%s shows its translated text", (code, text) => {
    expect(documentSnapshotErrorText(apiError(code), t, "toast.addError")).toBe(text);
  });

  test("an unmapped code shows the server's message", () => {
    expect(documentSnapshotErrorText(apiError("DOC_XXX_999"), t, "toast.addError")).toBe(
      "server message"
    );
  });

  test("an error without a message falls back", () => {
    expect(documentSnapshotErrorText(new Error(""), t, "toast.addError")).toBe("t:toast.addError");
  });
});

describe("snapshotPickerQuery", () => {
  test("lists the first documents of the mail's relation", () => {
    expect(snapshotPickerQuery("rel-1", "  ", null)).toEqual({
      relationId: "rel-1",
      page: 1,
      amount: 20,
    });
  });

  test("a search and a type narrow the list", () => {
    expect(snapshotPickerQuery("rel-1", " offer ", "CONTRACT")).toEqual({
      relationId: "rel-1",
      search: "offer",
      type: "CONTRACT",
      page: 1,
      amount: 20,
    });
  });

  test("a search is cut to the server's limit", () => {
    expect(snapshotPickerQuery("rel-1", "x".repeat(150), null).search).toHaveLength(100);
  });
});

describe("attachableDocuments", () => {
  test("keeps only documents with their files", () => {
    expect(
      attachableDocuments([document("a", true), document("b", false)]).map((d) => d.id)
    ).toEqual(["a"]);
  });
});

describe("isDocumentSnapshot", () => {
  test("only a file with a document id is a snapshot", () => {
    expect(isDocumentSnapshot({ documentId: "doc-1" })).toBe(true);
    expect(isDocumentSnapshot({ documentId: null })).toBe(false);
    expect(isDocumentSnapshot({})).toBe(false);
  });
});

function template(id: string, ready: boolean): DocumentTemplateListItem {
  return {
    id,
    name: `Template ${id}`,
    type: "PROPOSAL",
    language: "NL",
    legalEntityId: null,
    legalEntityShortName: null,
    ready,
    updatedAt: "2026-11-01T10:00:00Z",
  };
}

describe("withNewFromTemplate", () => {
  test("puts the new from template entry after the documents", () => {
    const options = withNewFromTemplate([{ value: "doc-1", label: "Proposal" }], "New");
    expect(options).toEqual([
      { value: "doc-1", label: "Proposal" },
      { value: NEW_FROM_TEMPLATE, label: "New" },
    ]);
  });

  test("offers the entry when the relation has no documents", () => {
    expect(withNewFromTemplate([], "New").map((option) => option.value)).toEqual([
      NEW_FROM_TEMPLATE,
    ]);
  });
});

describe("attachableTemplates", () => {
  test("keeps only templates with both files", () => {
    expect(
      attachableTemplates([template("a", true), template("b", false)]).map((t) => t.id)
    ).toEqual(["a"]);
  });
});

describe("createDocumentFromTemplateFormSchema", () => {
  const schema = createDocumentFromTemplateFormSchema(t, t);
  const values = {
    relationId: "rel-1",
    legalEntityId: "le-1",
    title: "Proposal",
    type: "PROPOSAL",
    language: "NL",
    templateId: "tpl-1",
  };

  test("accepts a document with a template", () => {
    expect(schema.safeParse(values).success).toBe(true);
  });

  test("requires the template and the legal entity", () => {
    const result = schema.safeParse({ ...values, templateId: "", legalEntityId: "" });
    expect(result.success).toBe(false);
    const messages = result.error?.issues.map((issue) => issue.message) ?? [];
    expect(messages).toContain("t:errors.templateRequired");
    expect(messages).toContain("t:errors.legalEntityRequired");
  });
});
