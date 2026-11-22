import { describe, expect, test } from "bun:test";
import {
  createDocumentCreateFormSchema,
  createDocumentEditFormSchema,
  createDocumentFilesFormSchema,
  documentDetailSchema,
  documentListItemSchema,
  documentToEditFormValues,
  emptyDocumentCreateFormValues,
  toDocumentCreateRequest,
  toDocumentUpdateRequest,
  type DocumentDetail,
} from ".";

const t = (key: string): string => `t:${key}`;

const LIST_ITEM = {
  id: "doc-1",
  title: "Proposal penetration test",
  type: "PROPOSAL",
  language: "NL",
  entityType: "RELATION",
  entityId: "rel-1",
  relationId: "rel-1",
  relationName: "Acme B.V.",
  legalEntityId: "le-1",
  legalEntityShortName: "Korur B.V.",
  templateId: null,
  ready: true,
  snapshotCount: 2,
  updatedBy: { id: "user-1", displayName: "Merthan Tukus" },
  updatedAt: "2026-11-20T09:15:00Z",
  createdAt: "2026-11-18T12:00:00Z",
};

const DETAIL: DocumentDetail = {
  ...(LIST_ITEM as DocumentDetail),
  htmlUrl: "https://blob.example/doc.html",
  pdfUrl: "https://blob.example/doc.pdf",
  snapshots: [
    {
      attachmentId: "att-1",
      mailId: "mail-1",
      outreachTitle: "Q4 security offers",
      createdAt: "2026-11-21T08:00:00Z",
    },
  ],
};

describe("document responses", () => {
  test("a list item parses", () => {
    expect(documentListItemSchema.safeParse(LIST_ITEM).success).toBe(true);
  });

  test("a list item of a deleted relation and user parses", () => {
    const item = { ...LIST_ITEM, relationName: null, updatedBy: null };
    expect(documentListItemSchema.safeParse(item).success).toBe(true);
  });

  test("a detail without files and with a mail outside an outreach parses", () => {
    const detail = {
      ...DETAIL,
      htmlUrl: null,
      pdfUrl: null,
      ready: false,
      snapshots: [{ ...DETAIL.snapshots[0], outreachTitle: null }],
    };
    expect(documentDetailSchema.safeParse(detail).success).toBe(true);
  });

  test("a CONTRACT document parses", () => {
    expect(documentListItemSchema.safeParse({ ...LIST_ITEM, type: "CONTRACT" }).success).toBe(true);
  });
});

describe("new document form", () => {
  const schema = createDocumentCreateFormSchema(t);

  test("the relation and the legal entity are preselected when given", () => {
    expect(emptyDocumentCreateFormValues("rel-1", "le-1")).toMatchObject({
      relationId: "rel-1",
      legalEntityId: "le-1",
      templateId: "",
    });
  });

  test("the relation, the legal entity and the title are required", () => {
    const result = schema.safeParse(emptyDocumentCreateFormValues(null, null));
    expect(result.success).toBe(false);
    const messages = result.error?.issues.map((issue) => issue.message) ?? [];
    expect(messages).toContain("t:errors.relationRequired");
    expect(messages).toContain("t:errors.legalEntityRequired");
    expect(messages).toContain("t:errors.titleRequired");
  });

  test("a document without a template sends a null template", () => {
    const values = { ...emptyDocumentCreateFormValues("rel-1", "le-1"), title: "Offer" };
    expect(schema.safeParse(values).success).toBe(true);
    expect(toDocumentCreateRequest(values)).toEqual({
      entityType: "RELATION",
      entityId: "rel-1",
      legalEntityId: "le-1",
      title: "Offer",
      type: "PROPOSAL",
      language: "NL",
      templateId: null,
    });
  });

  test("a picked template is sent", () => {
    const values = {
      ...emptyDocumentCreateFormValues("rel-1", "le-1"),
      title: "Offer",
      templateId: "tpl-1",
    };
    expect(toDocumentCreateRequest(values).templateId).toBe("tpl-1");
  });

  test("a title over 200 characters is refused", () => {
    const values = { ...emptyDocumentCreateFormValues("rel-1", "le-1"), title: "a".repeat(201) };
    const result = schema.safeParse(values);
    expect(result.error?.issues.map((issue) => issue.message)).toContain("t:errors.titleMax");
  });
});

describe("edit document form", () => {
  test("starts from the stored document and sends every field", () => {
    const values = documentToEditFormValues(DETAIL);
    expect(createDocumentEditFormSchema(t).safeParse(values).success).toBe(true);
    expect(toDocumentUpdateRequest({ ...values, type: "CONTRACT" })).toEqual({
      title: "Proposal penetration test",
      type: "CONTRACT",
      language: "NL",
      legalEntityId: "le-1",
    });
  });
});

describe("file replacement form", () => {
  const schema = createDocumentFilesFormSchema(t);
  const html = new File(["<p>Offer</p>"], "offer.html", { type: "text/html" });
  const pdf = new File(["%PDF-1.7"], "offer.pdf", { type: "application/pdf" });

  test("takes an HTML file and a PDF together", () => {
    expect(schema.safeParse({ htmlFile: html, pdfFile: pdf }).success).toBe(true);
  });

  test("needs both files", () => {
    const result = schema.safeParse({ htmlFile: null, pdfFile: null });
    const messages = result.error?.issues.map((issue) => issue.message) ?? [];
    expect(messages).toEqual(["t:errors.htmlFileRequired", "t:errors.pdfFileRequired"]);
  });

  test("refuses swapped files", () => {
    const result = schema.safeParse({ htmlFile: pdf, pdfFile: html });
    const messages = result.error?.issues.map((issue) => issue.message) ?? [];
    expect(messages).toEqual(["t:errors.htmlFileType", "t:errors.pdfFileType"]);
  });
});
