"use client";

import { useTranslations } from "next-intl";
import { useDateFormat } from "@oba/shared/i18n/use-date-format";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@oba/ui/sky/dialog";
import { LoadError, TableLoading } from "@oba/ui/sky/page-states";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@oba/ui/sky/tabs";
import { EntityAttachments } from "@/features/attachments/components/entity-attachments";
import { useIsOrganizationAdmin } from "@/hooks/use-is-organization-admin";
import { useOutreach } from "../hooks/use-outreaches";
import { outreachErrorText } from "../lib/outreach-error";
import { canWriteMailAttachments, type OutreachDetail } from "../schemas";
import { OutreachStatusBadge } from "./outreach-status-badge";

export interface OutreachViewDialogProps {
  /** The outreach to show; null keeps the dialog closed. */
  outreachId: string | null;
  onOpenChange: (open: boolean) => void;
}

/**
 * Shows an outreach: the channel, to whom, from where and when, and for a
 * mail its text and, on a tab of their own, its attachments. Every member
 * sees them; an admin may still change them until the outreach is sent.
 */
export function OutreachViewDialog({ outreachId, onOpenChange }: OutreachViewDialogProps) {
  const t = useTranslations("outreaches");
  const tErrors = useTranslations("errors");
  const outreach = useOutreach(outreachId ?? "", { enabled: outreachId !== null });
  const data = outreach.data?.data;

  return (
    <Dialog open={outreachId !== null} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle className="break-words">{data?.title ?? t("viewTitle")}</DialogTitle>
          <DialogDescription>{t("viewDescription")}</DialogDescription>
        </DialogHeader>
        {outreach.isError && data === undefined ? (
          <LoadError
            title={t("loadOneError")}
            message={outreachErrorText(outreach.error, t, "errors.loadHint")}
            retryLabel={tErrors("tryAgain")}
            onRetry={() => void outreach.refetch()}
          />
        ) : data === undefined ? (
          <TableLoading label={t("loadingOne")} />
        ) : data.mail ? (
          <Tabs defaultValue="details">
            <TabsList aria-label={t("detailTabs.label")}>
              <TabsTrigger value="details">{t("detailTabs.details")}</TabsTrigger>
              <TabsTrigger value="attachments">{t("detailTabs.attachments")}</TabsTrigger>
            </TabsList>
            <TabsContent value="details">
              <OutreachDetails outreach={data} />
            </TabsContent>
            <TabsContent value="attachments">
              <MailAttachments outreach={data} mailId={data.mail.id} />
            </TabsContent>
          </Tabs>
        ) : (
          <OutreachDetails outreach={data} />
        )}
      </DialogContent>
    </Dialog>
  );
}

/** The mail's attachments; read-only once sent or for a user below ADMIN. */
function MailAttachments({ outreach, mailId }: { outreach: OutreachDetail; mailId: string }) {
  const isAdmin = useIsOrganizationAdmin();
  return (
    <EntityAttachments
      mailId={mailId}
      relationId={outreach.relationId}
      readOnly={!canWriteMailAttachments(outreach, isAdmin)}
    />
  );
}

/** The channel, to whom, from where and when, and for a mail its text. */
function OutreachDetails({ outreach: data }: { outreach: OutreachDetail }) {
  const t = useTranslations("outreaches");
  const tTemplates = useTranslations("documentTemplates");
  const { formatDateTime } = useDateFormat();

  return (
    <div className="flex flex-col gap-4">
      <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 text-sm">
        <dt className="text-muted-foreground">{t("columns.type")}</dt>
        <dd>{t(`types.${data.type}`)}</dd>
        <dt className="text-muted-foreground">{t("columns.status")}</dt>
        <dd>
          <OutreachStatusBadge status={data.status} />
        </dd>
        <dt className="text-muted-foreground">{t("columns.contact")}</dt>
        <dd className="break-words">{data.contact?.name ?? t("contactDeleted")}</dd>
        <dt className="text-muted-foreground">{t("details.sentTo")}</dt>
        <dd className="break-all">{data.sentTo ?? t("notSent")}</dd>
        <dt className="text-muted-foreground">{t("columns.sentAt")}</dt>
        <dd className="tabular-nums">{data.sentAt ? formatDateTime(data.sentAt) : t("notSent")}</dd>
        {data.mail && (
          <>
            <dt className="text-muted-foreground">{t("columns.sender")}</dt>
            <dd className="break-all">{data.mailSenderAddress ?? t("noSender")}</dd>
            <dt className="text-muted-foreground">{t("form.language")}</dt>
            <dd>{tTemplates(`languages.${data.mail.language}`)}</dd>
            <dt className="text-muted-foreground">{t("form.subject")}</dt>
            <dd className="break-words">{data.mail.subject}</dd>
          </>
        )}
      </dl>
      {data.mail && (
        <div className="bg-muted max-h-[40vh] overflow-y-auto rounded-md p-3 text-sm break-words whitespace-pre-wrap">
          {data.mail.body}
        </div>
      )}
    </div>
  );
}
