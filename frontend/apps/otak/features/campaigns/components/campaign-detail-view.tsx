"use client";

import { useState, type ReactNode } from "react";
import { useTranslations } from "next-intl";
import { ArrowLeft, Building2, CheckCircle2, Copy, Pencil, Send, Trash2 } from "lucide-react";
import { toast } from "sonner";
import { isApiError } from "@oba/shared/api/axios";
import { useDateFormat } from "@oba/shared/i18n/use-date-format";
import { Link, useRouter } from "@oba/shared/libs/i18n/navigation";
import { Badge } from "@oba/ui/sky/badge";
import { Button } from "@oba/ui/sky/button";
import { Card, CardContent, CardHeader } from "@oba/ui/sky/card";
import { LoadError, TableLoading } from "@oba/ui/sky/page-states";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { ActionEmptyState } from "@/components/page-states";
import { useEvent } from "@/features/events/hooks/use-events";
import { useIcp } from "@/features/icps/hooks/use-icps";
import { useIsOrganizationAdmin } from "@/hooks/use-is-organization-admin";
import { cn } from "@/lib/utils";
import { useCampaign, useDeleteCampaign, useUpdateCampaign } from "../hooks/use-campaigns";
import { campaignErrorText } from "../lib/campaign-error";
import {
  canMarkCampaignPublished,
  CHANNEL_TEXT_LIMITS,
  toMarkPublishedRequest,
  type CampaignContent,
  type CampaignDetail,
} from "../schemas";
import { CampaignFormDialog } from "./campaign-form-dialog";
import { CampaignImagesCard } from "./campaign-images-card";
import { CampaignStatusBadge } from "./campaign-status-badge";

/**
 * One campaign: a heading with its name, channel and status, the edit and
 * "mark as published" actions for every member and the delete action for an
 * admin, its details, its texts per language to copy, links to the
 * relations and outreaches it brought, and its images.
 */
export function CampaignDetailView({ campaignId }: { campaignId: string }) {
  const t = useTranslations("campaigns");
  const tErrors = useTranslations("errors");
  const campaign = useCampaign(campaignId);
  const data = campaign.data?.data;

  if (data) {
    return <CampaignDetailContent campaign={data} />;
  }

  if (campaign.isError) {
    if (isApiError(campaign.error, "OTK_NOT_012")) {
      return (
        <ActionEmptyState
          title={t("detail.notFoundTitle")}
          description={t("detail.notFoundDescription")}
          action={
            <Button asChild variant="outline">
              <Link href="/campaigns">
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
        message={campaignErrorText(campaign.error, t, "errors.loadHint")}
        retryLabel={tErrors("tryAgain")}
        onRetry={() => void campaign.refetch()}
      />
    );
  }

  return <TableLoading label={t("detail.loading")} />;
}

function CampaignDetailContent({ campaign }: { campaign: CampaignDetail }) {
  const t = useTranslations("campaigns");
  const router = useRouter();
  const { formatDate, formatDateTime } = useDateFormat();
  const isAdmin = useIsOrganizationAdmin();
  const deleteCampaign = useDeleteCampaign();
  const markPublished = useUpdateCampaign({ successKey: "toast.markedPublished" });
  const [formOpen, setFormOpen] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);

  const handleDelete = () => {
    deleteCampaign.mutate(campaign.id, {
      onSuccess: () => router.replace("/campaigns"),
    });
  };

  const none = (label: string) => <span className="text-muted-foreground">{label}</span>;

  return (
    <div className="flex flex-col gap-6">
      <Button asChild variant="ghost" size="sm" className="w-fit">
        <Link href="/campaigns">
          <ArrowLeft aria-hidden="true" />
          {t("detail.back")}
        </Link>
      </Button>

      <div className="flex flex-wrap items-end justify-between gap-4">
        <div className="flex min-w-0 flex-col gap-2">
          <h1 className="break-words">{campaign.name}</h1>
          <div className="flex flex-wrap items-center gap-2">
            <Badge variant="secondary">{t(`channels.${campaign.channel}`)}</Badge>
            <CampaignStatusBadge status={campaign.status} />
            <span className="text-muted-foreground text-sm tabular-nums">
              {t("detail.timestamps", {
                created: formatDate(campaign.createdAt),
                updated: formatDate(campaign.updatedAt),
              })}
            </span>
          </div>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          {canMarkCampaignPublished(campaign) && (
            <Button
              disabled={markPublished.isPending}
              onClick={() =>
                markPublished.mutate({
                  campaignId: campaign.id,
                  data: toMarkPublishedRequest(campaign),
                })
              }
            >
              <CheckCircle2 aria-hidden="true" />
              {markPublished.isPending ? t("detail.markingPublished") : t("detail.markPublished")}
            </Button>
          )}
          <Button variant="outline" onClick={() => setFormOpen(true)}>
            <Pencil aria-hidden="true" />
            {t("detail.edit")}
          </Button>
          {isAdmin && (
            <Button
              variant="outline"
              disabled={deleteCampaign.isPending}
              onClick={() => setConfirmOpen(true)}
            >
              <Trash2 aria-hidden="true" />
              {deleteCampaign.isPending ? t("detail.deleting") : t("detail.delete")}
            </Button>
          )}
        </div>
      </div>

      <Card role="region" aria-labelledby="campaign-details" className="gap-4">
        <CardHeader className="flex flex-wrap items-center justify-between gap-2">
          <h2 id="campaign-details" className="text-lg leading-none">
            {t("detail.details")}
          </h2>
          <div className="flex flex-wrap gap-2">
            <Button asChild variant="outline" size="sm">
              <Link href={{ pathname: "/relations", query: { campaign: campaign.id } }}>
                <Building2 aria-hidden="true" />
                {t("detail.relationsLink")}
              </Link>
            </Button>
            <Button asChild variant="outline" size="sm">
              <Link href={{ pathname: "/outreaches", query: { campaign: campaign.id } }}>
                <Send aria-hidden="true" />
                {t("detail.outreachesLink")}
              </Link>
            </Button>
          </div>
        </CardHeader>
        <CardContent>
          <dl className="grid gap-4 text-sm sm:grid-cols-3">
            <DetailItem label={t("detail.plannedAt")}>
              {campaign.plannedAt ? (
                <span className="tabular-nums">{formatDateTime(campaign.plannedAt)}</span>
              ) : (
                none(t("notPlanned"))
              )}
            </DetailItem>
            <DetailItem label={t("detail.publishedAt")}>
              {campaign.publishedAt ? (
                <span className="tabular-nums">{formatDateTime(campaign.publishedAt)}</span>
              ) : (
                none(t("notPublished"))
              )}
            </DetailItem>
            <DetailItem label={t("detail.owner")}>
              {campaign.owner ? campaign.owner.name : none(t("noOwner"))}
            </DetailItem>
            <DetailItem label={t("detail.postUrl")} className="min-w-0">
              {campaign.postUrl ? (
                <a
                  href={campaign.postUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="block truncate hover:underline"
                  title={campaign.postUrl}
                >
                  {campaign.postUrl.replace(/^https?:\/\//, "")}
                </a>
              ) : (
                none(t("noPostUrl"))
              )}
            </DetailItem>
            <DetailItem label={t("detail.event")}>
              {campaign.eventId ? (
                <CampaignEventLink eventId={campaign.eventId} />
              ) : (
                none(t("noEvent"))
              )}
            </DetailItem>
            <DetailItem label={t("detail.icp")}>
              {campaign.icpId ? <CampaignIcpName icpId={campaign.icpId} /> : none(t("noIcp"))}
            </DetailItem>
          </dl>
        </CardContent>
      </Card>

      <CampaignTextsCard campaign={campaign} />
      <CampaignImagesCard campaignId={campaign.id} />

      <CampaignFormDialog campaignId={campaign.id} open={formOpen} onOpenChange={setFormOpen} />
      <ConfirmDialog
        open={confirmOpen}
        onOpenChange={setConfirmOpen}
        title={t("deleteTitle")}
        description={t("deleteConfirm", { name: campaign.name })}
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

function DetailItem({
  label,
  className,
  children,
}: {
  label: string;
  className?: string;
  children: ReactNode;
}) {
  return (
    <div className={cn("flex flex-col gap-1", className)}>
      <dt className="text-muted-foreground">{label}</dt>
      <dd className="break-words">{children}</dd>
    </div>
  );
}

/**
 * The linked event, as a link to its page. Until the event is read, or when
 * it cannot be read, the link says "Event" without a name, so the event page
 * explains what went wrong.
 */
function CampaignEventLink({ eventId }: { eventId: string }) {
  const t = useTranslations("campaigns.detail");
  const event = useEvent(eventId);
  const name = event.data?.data?.name;
  return (
    <Link href={`/events/${eventId}`} className="hover:underline">
      {name ?? t("eventUnnamed")}
    </Link>
  );
}

/** The linked ICP by name; "ICP" until it is read or when it cannot be read. */
function CampaignIcpName({ icpId }: { icpId: string }) {
  const t = useTranslations("campaigns.detail");
  const icp = useIcp(icpId);
  return <>{icp.data?.data?.name ?? t("icpUnnamed")}</>;
}

/**
 * The texts of the campaign, one per language, shown as plain text with a
 * copy button, so the member pastes the exact text into the post. The
 * length is shown against the channel's post limit.
 */
function CampaignTextsCard({ campaign }: { campaign: CampaignDetail }) {
  const t = useTranslations("campaigns");
  const tTemplates = useTranslations("documentTemplates");
  const limit = CHANNEL_TEXT_LIMITS[campaign.channel];

  const copy = (content: CampaignContent) => {
    navigator.clipboard.writeText(content.text).then(
      () => toast.success(t("detail.copied")),
      (error: unknown) => {
        console.error("[campaign-texts] copy to the clipboard failed:", error);
        toast.error(t("detail.copyError"));
      }
    );
  };

  return (
    <Card role="region" aria-labelledby="campaign-texts" className="gap-4">
      <CardHeader>
        <h2 id="campaign-texts" className="text-lg leading-none">
          {t("detail.texts")}
        </h2>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        {campaign.contents.length === 0 ? (
          <p className="text-muted-foreground text-sm">{t("detail.noTexts")}</p>
        ) : (
          campaign.contents.map((content) => {
            const languageName = tTemplates(`languages.${content.language}`);
            return (
              <section
                key={content.language}
                aria-label={languageName}
                className="flex flex-col gap-2 rounded-lg border p-3"
              >
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <h3 className="text-sm font-medium">{languageName}</h3>
                  <div className="flex items-center gap-2">
                    <span className="text-muted-foreground text-xs tabular-nums">
                      {limit === undefined
                        ? t("form.characterCount", { count: content.text.length })
                        : t("form.characterCountOfLimit", {
                            count: content.text.length,
                            limit,
                          })}
                    </span>
                    <Button
                      variant="outline"
                      size="sm"
                      aria-label={t("detail.copyLabel", { language: languageName })}
                      onClick={() => copy(content)}
                    >
                      <Copy aria-hidden="true" />
                      {t("detail.copy")}
                    </Button>
                  </div>
                </div>
                <p className="text-sm break-words whitespace-pre-wrap">{content.text}</p>
              </section>
            );
          })
        )}
      </CardContent>
    </Card>
  );
}
