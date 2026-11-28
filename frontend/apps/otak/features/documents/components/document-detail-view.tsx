"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { ArrowLeft, Copy, Download, FileUp, Pencil, Trash2 } from "lucide-react";
import { isApiError } from "@oba/shared/api/axios";
import { useDateFormat } from "@oba/shared/i18n/use-date-format";
import { Link, useRouter } from "@oba/shared/libs/i18n/navigation";
import { Badge } from "@oba/ui/sky/badge";
import { Button } from "@oba/ui/sky/button";
import { Card, CardContent, CardHeader } from "@oba/ui/sky/card";
import { PageHeading } from "@oba/ui/sky/page-heading";
import { EmptyState, LoadError, TableLoading } from "@oba/ui/sky/page-states";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { DocumentTemplatePreviewContent } from "@/features/document-templates/components/document-template-preview-dialog";
import { useDocumentTemplate } from "@/features/document-templates/hooks/use-document-templates";
import { useDeleteDocument, useDocument } from "../hooks/use-documents";
import { useEditedText } from "../hooks/use-edited-text";
import { documentErrorText } from "../lib/document-error";
import type { DocumentDetail } from "../schemas";
import { DocumentEditDialog } from "./document-edit-dialog";
import { DocumentFilesDialog } from "./document-files-dialog";

/** The CLI command that saves a document's HTML locally, to edit and upload it again. */
export function documentDownloadCommand(documentId: string): string {
  return `oba document download ${documentId}`;
}

/**
 * One document: its PDF large, and beside it the relation, the legal entity,
 * the type, the language, the source template and the last change; the CLI
 * command to edit its content; and the mails a PDF copy of it went out with.
 * Every member with Otak access edits, replaces the files of and deletes a
 * document.
 */
export function DocumentDetailView({ documentId }: { documentId: string }) {
  const t = useTranslations("documents");
  const tErrors = useTranslations("errors");
  const document = useDocument(documentId);
  const data = document.data?.data;

  if (data) {
    return <DocumentDetailContent document={data} />;
  }

  if (document.isError) {
    if (isApiError(document.error, "DOC_NOT_002")) {
      return (
        <EmptyState
          title={t("detail.notFoundTitle")}
          description={t("detail.notFoundDescription")}
          action={
            <Button asChild variant="outline">
              <Link href="/documents">
                <ArrowLeft aria-hidden="true" />
                {t("detail.back")}
              </Link>
            </Button>
          }
        />
      );
    }
    return (
      <LoadError
        title={t("detail.loadError")}
        message={documentErrorText(document.error, t, "errors.loadHint")}
        retryLabel={tErrors("tryAgain")}
        onRetry={() => void document.refetch()}
      />
    );
  }

  return <TableLoading label={t("detail.loading")} />;
}

function DocumentDetailContent({ document }: { document: DocumentDetail }) {
  const t = useTranslations("documents");
  const tTemplates = useTranslations("documentTemplates");
  const router = useRouter();
  const { formatDateTime } = useDateFormat();
  const editedText = useEditedText();
  const deleteDocument = useDeleteDocument();
  const [editOpen, setEditOpen] = useState(false);
  const [filesOpen, setFilesOpen] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);

  const handleDelete = () => {
    deleteDocument.mutate(document.id, {
      onSuccess: () => router.replace("/documents"),
    });
  };

  return (
    <div className="flex flex-col gap-6">
      <PageHeading
        title={document.title}
        back={{ href: "/documents", label: t("detail.back") }}
        linkComponent={Link}
        description={
          <div className="flex flex-wrap items-center gap-2">
            <Badge variant="secondary">{tTemplates(`types.${document.type}`)}</Badge>
            <Badge variant="outline">{tTemplates(`languages.${document.language}`)}</Badge>
            {!document.ready && <Badge variant="warning">{t("detail.noFiles")}</Badge>}
            <span
              className="text-muted-foreground text-sm tabular-nums"
              title={formatDateTime(document.updatedAt)}
            >
              {t("detail.edited", { text: editedText(document.updatedAt, document.updatedBy) })}
            </span>
          </div>
        }
        actions={
          <>
            <Button variant="outline" onClick={() => setEditOpen(true)}>
              <Pencil aria-hidden="true" />
              {t("detail.edit")}
            </Button>
            <Button variant="outline" onClick={() => setFilesOpen(true)}>
              <FileUp aria-hidden="true" />
              {t("detail.replaceFiles")}
            </Button>
            <Button
              variant="outline"
              disabled={deleteDocument.isPending}
              onClick={() => setConfirmOpen(true)}
            >
              <Trash2 aria-hidden="true" />
              {deleteDocument.isPending ? t("detail.deleting") : t("detail.delete")}
            </Button>
          </>
        }
      />

      <div className="grid gap-6 lg:grid-cols-3">
        <Card role="region" aria-labelledby="document-preview" className="gap-4 lg:col-span-2">
          <CardHeader>
            <h2 id="document-preview" className="text-lg leading-none">
              {t("detail.preview")}
            </h2>
          </CardHeader>
          <CardContent>
            <DocumentTemplatePreviewContent
              template={{ name: document.title, pdfUrl: document.pdfUrl }}
              noFilesText={t("detail.previewNoFiles")}
            />
          </CardContent>
        </Card>

        <div className="flex flex-col gap-6">
          <DocumentDetailsCard document={document} />
          <DocumentContentCard document={document} />
        </div>
      </div>

      <DocumentSnapshotsCard document={document} />

      <DocumentEditDialog document={document} open={editOpen} onOpenChange={setEditOpen} />
      <DocumentFilesDialog documentId={document.id} open={filesOpen} onOpenChange={setFilesOpen} />
      <ConfirmDialog
        open={confirmOpen}
        onOpenChange={setConfirmOpen}
        title={t("deleteTitle")}
        description={t("deleteConfirm", { title: document.title })}
        confirmLabel={t("deleteAction")}
        cancelLabel={t("form.cancel")}
        destructive
        onConfirm={() => {
          handleDelete();
          setConfirmOpen(false);
        }}
      />
    </div>
  );
}

/** The relation, legal entity, type, language, source template and timestamps of a document. */
function DocumentDetailsCard({ document }: { document: DocumentDetail }) {
  const t = useTranslations("documents");
  const tTemplates = useTranslations("documentTemplates");
  const { formatDateTime } = useDateFormat();
  const editedText = useEditedText();
  const template = useDocumentTemplate(document.templateId ?? "", {
    enabled: document.templateId !== null,
  });

  return (
    <Card role="region" aria-labelledby="document-details" className="gap-4">
      <CardHeader>
        <h2 id="document-details" className="text-lg leading-none">
          {t("detail.details")}
        </h2>
      </CardHeader>
      <CardContent>
        <dl className="grid gap-4 text-sm">
          <div className="flex min-w-0 flex-col gap-1">
            <dt className="text-muted-foreground">{t("detail.relation")}</dt>
            <dd className="truncate">
              {document.relationId ? (
                <Link href={`/relations/${document.relationId}`} className="hover:underline">
                  {document.relationName ?? t("noRelation")}
                </Link>
              ) : (
                <span className="text-muted-foreground">{t("noRelation")}</span>
              )}
            </dd>
          </div>
          <div className="flex flex-col gap-1">
            <dt className="text-muted-foreground">{t("detail.legalEntity")}</dt>
            <dd>{document.legalEntityShortName}</dd>
          </div>
          <div className="flex flex-col gap-1">
            <dt className="text-muted-foreground">{t("detail.type")}</dt>
            <dd>{tTemplates(`types.${document.type}`)}</dd>
          </div>
          <div className="flex flex-col gap-1">
            <dt className="text-muted-foreground">{t("detail.language")}</dt>
            <dd>{tTemplates(`languages.${document.language}`)}</dd>
          </div>
          <div className="flex min-w-0 flex-col gap-1">
            <dt className="text-muted-foreground">{t("detail.template")}</dt>
            <dd className="truncate">
              {document.templateId === null ? (
                <span className="text-muted-foreground">{t("detail.noTemplate")}</span>
              ) : (
                (template.data?.data?.name ??
                (template.isError ? (
                  <span className="text-muted-foreground">{t("detail.templateUnavailable")}</span>
                ) : (
                  <span className="text-muted-foreground">{t("detail.templateLoading")}</span>
                )))
              )}
            </dd>
          </div>
          <div className="flex flex-col gap-1">
            <dt className="text-muted-foreground">{t("detail.lastEdited")}</dt>
            <dd className="tabular-nums" title={formatDateTime(document.updatedAt)}>
              {editedText(document.updatedAt, document.updatedBy)}
            </dd>
          </div>
          <div className="flex flex-col gap-1">
            <dt className="text-muted-foreground">{t("detail.created")}</dt>
            <dd className="tabular-nums">{formatDateTime(document.createdAt)}</dd>
          </div>
        </dl>
      </CardContent>
    </Card>
  );
}

/**
 * The files of a document and how to edit them: the PDF and the HTML to open
 * or save, and the CLI command that downloads the HTML to edit and upload
 * again.
 */
function DocumentContentCard({ document }: { document: DocumentDetail }) {
  const t = useTranslations("documents");
  const command = documentDownloadCommand(document.id);

  const copyCommand = () => {
    navigator.clipboard.writeText(command).then(
      () => toast.success(t("detail.commandCopied")),
      (error: unknown) => {
        console.error("[document-page] the CLI command could not be copied:", error);
        toast.error(t("detail.commandCopyError"));
      }
    );
  };

  return (
    <Card role="region" aria-labelledby="document-content" className="gap-4">
      <CardHeader>
        <h2 id="document-content" className="text-lg leading-none">
          {t("detail.content")}
        </h2>
      </CardHeader>
      <CardContent className="flex flex-col gap-4 text-sm">
        {document.pdfUrl || document.htmlUrl ? (
          <div className="flex flex-wrap gap-2">
            {document.pdfUrl && (
              <Button asChild variant="outline" size="sm">
                <a href={document.pdfUrl} target="_blank" rel="noopener noreferrer">
                  <Download aria-hidden="true" />
                  {t("detail.downloadPdf")}
                </a>
              </Button>
            )}
            {document.htmlUrl && (
              <Button asChild variant="outline" size="sm">
                <a href={document.htmlUrl} target="_blank" rel="noopener noreferrer">
                  <Download aria-hidden="true" />
                  {t("detail.downloadHtml")}
                </a>
              </Button>
            )}
          </div>
        ) : (
          <p className="text-muted-foreground">{t("detail.noFilesHint")}</p>
        )}
        <div className="flex flex-col gap-1">
          <p className="text-muted-foreground">{t("detail.commandHint")}</p>
          <div className="flex items-center gap-2">
            <code className="bg-muted min-w-0 flex-1 truncate rounded px-2 py-1 font-mono text-xs">
              {command}
            </code>
            <Button
              variant="ghost"
              size="icon-sm"
              aria-label={t("detail.copyCommand")}
              onClick={copyCommand}
            >
              <Copy aria-hidden="true" />
            </Button>
          </div>
        </div>
      </CardContent>
    </Card>
  );
}

/**
 * The PDF copies of the document that went out with mails, newest first,
 * each with the outreach that sends the mail. A copy keeps what the relation
 * received, whatever the document became afterwards.
 */
function DocumentSnapshotsCard({ document }: { document: DocumentDetail }) {
  const t = useTranslations("documents");
  const { formatDateTime } = useDateFormat();

  return (
    <Card role="region" aria-labelledby="document-snapshots" className="gap-4">
      <CardHeader>
        <h2 id="document-snapshots" className="text-lg leading-none">
          {t("detail.snapshots", { count: document.snapshots.length })}
        </h2>
      </CardHeader>
      <CardContent>
        {document.snapshots.length === 0 ? (
          <p className="text-muted-foreground text-sm">{t("detail.noSnapshots")}</p>
        ) : (
          <ul className="divide-y text-sm">
            {document.snapshots.map((snapshot) => (
              <li
                key={snapshot.attachmentId}
                className="flex flex-wrap items-center justify-between gap-2 py-2"
              >
                <span className="min-w-0 truncate">
                  {snapshot.outreachTitle ?? (
                    <span className="text-muted-foreground">{t("detail.mailWithoutOutreach")}</span>
                  )}
                </span>
                <span className="text-muted-foreground tabular-nums">
                  {formatDateTime(snapshot.createdAt)}
                </span>
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  );
}
