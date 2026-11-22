import { describe, expect, test } from "bun:test";
import {
  createDocumentTemplateFormSchema,
  documentTemplateToFormValues,
  emptyDocumentTemplateFormValues,
  toDocumentTemplateFiles,
  toDocumentTemplateRequest,
  type DocumentTemplateFormValues,
} from ".";

const t = (key: string): string => `t:${key}`;
const schema = createDocumentTemplateFormSchema(t);

const html = new File(["<p>hi</p>"], "offer.html", { type: "text/html" });
const pdf = new File(["%PDF-1.7"], "offer.pdf", { type: "application/pdf" });

function proposal(overrides: Partial<DocumentTemplateFormValues> = {}): DocumentTemplateFormValues {
  return { ...emptyDocumentTemplateFormValues(), name: "Offer", ...overrides };
}

function messages(values: DocumentTemplateFormValues): Record<string, string> {
  const result = schema.safeParse(values);
  if (result.success) return {};
  return Object.fromEntries(
    result.error.issues.map((issue) => [issue.path.join("."), issue.message])
  );
}

describe("document template form schema", () => {
  test("a new template is a proposal and valid without files", () => {
    expect(emptyDocumentTemplateFormValues().type).toBe("PROPOSAL");
    expect(messages(proposal())).toEqual({});
  });

  test("a name is required and capped at 100 characters", () => {
    expect(messages(proposal({ name: "  " }))).toEqual({ name: "t:errors.nameRequired" });
    expect(messages(proposal({ name: "n".repeat(101) }))).toEqual({ name: "t:errors.nameMax" });
  });

  test("a mail is no document type", () => {
    expect(schema.safeParse({ ...proposal(), type: "MAIL" }).success).toBe(false);
  });

  test("a proposal takes both files or neither", () => {
    expect(messages(proposal({ htmlFile: html, pdfFile: pdf }))).toEqual({});
    expect(messages(proposal({ htmlFile: html }))).toEqual({ pdfFile: "t:errors.pdfFileRequired" });
    expect(messages(proposal({ pdfFile: pdf }))).toEqual({ htmlFile: "t:errors.htmlFileRequired" });
  });

  test("a file of the wrong kind is refused on its field", () => {
    const text = new File(["x"], "notes.txt", { type: "text/plain" });
    expect(messages(proposal({ htmlFile: text, pdfFile: text }))).toEqual({
      htmlFile: "t:errors.htmlFileType",
      pdfFile: "t:errors.pdfFileType",
    });
  });

  test("a file without a browser type is taken by its extension", () => {
    const bareHtml = new File(["x"], "OFFER.HTM");
    const barePdf = new File(["x"], "offer.PDF");
    expect(messages(proposal({ htmlFile: bareHtml, pdfFile: barePdf }))).toEqual({});
  });

  test("files over 12 MB together are refused", () => {
    const big = new File([new Uint8Array(12 * 1024 * 1024)], "big.pdf", {
      type: "application/pdf",
    });
    expect(messages(proposal({ htmlFile: html, pdfFile: big }))).toEqual({
      pdfFile: "t:errors.filesTooLarge",
    });
  });
});

describe("toDocumentTemplateRequest", () => {
  test("sends the type, name and language, and no legal entity when none is picked", () => {
    expect(toDocumentTemplateRequest(proposal({ type: "NDA", language: "EN" }))).toEqual({
      type: "NDA",
      name: "Offer",
      language: "EN",
      legalEntityId: null,
    });
  });

  test("sends the picked legal entity", () => {
    expect(toDocumentTemplateRequest(proposal({ legalEntityId: "le-1" })).legalEntityId).toBe(
      "le-1"
    );
  });
});

describe("documentTemplateToFormValues", () => {
  test("a template for every legal entity edits with an empty legal entity", () => {
    const values = documentTemplateToFormValues({
      id: "t-1",
      name: "Offer",
      type: "CONTRACT",
      language: "TR",
      legalEntityId: null,
      legalEntityShortName: null,
      ready: true,
      updatedAt: "2026-11-01T10:00:00Z",
      createdAt: "2026-11-01T09:00:00Z",
      htmlUrl: null,
      pdfUrl: null,
    });
    expect(values.legalEntityId).toBe("");
    expect(values.type).toBe("CONTRACT");
  });
});

describe("toDocumentTemplateFiles", () => {
  test("both picked files are uploaded", () => {
    expect(toDocumentTemplateFiles(proposal({ htmlFile: html, pdfFile: pdf }))).toEqual({
      html,
      pdf,
    });
  });

  test("a template without both files uploads nothing", () => {
    expect(toDocumentTemplateFiles(proposal())).toBeNull();
    expect(toDocumentTemplateFiles(proposal({ htmlFile: html }))).toBeNull();
  });
});
