"use client";

import { useState, type ReactNode } from "react";
import { useLocale, useTranslations } from "next-intl";
import { ArrowLeft, Building2, Pencil, Send, Trash2 } from "lucide-react";
import { isApiError } from "@oba/shared/api/axios";
import { useDateFormat } from "@oba/shared/i18n/use-date-format";
import { Link, useRouter } from "@oba/shared/libs/i18n/navigation";
import { Button } from "@oba/ui/sky/button";
import { Card, CardContent, CardHeader } from "@oba/ui/sky/card";
import { PageHeading } from "@oba/ui/sky/page-heading";
import { EmptyState, LoadError, TableLoading } from "@oba/ui/sky/page-states";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { useIcp } from "@/features/icps/hooks/use-icps";
import { useIsOrganizationAdmin } from "@/hooks/use-is-organization-admin";
import { cn } from "@/lib/utils";
import { useCampaign, useDeleteCampaign } from "../hooks/use-campaigns";
import { campaignErrorText } from "../lib/campaign-error";
import { formatCampaignDates } from "../lib/campaign-format";
import type { CampaignDetail } from "../schemas";
import { CampaignContentTable } from "./campaign-content-table";
import { CampaignEventLink } from "./campaign-event-link";
import { CampaignFormDialog } from "./campaign-form-dialog";

/**
 * One campaign: a heading with its name, the edit action for every member
 * and the delete action for an admin, its details, links to the relations
 * and outreaches it brought, and its contents.
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
        <EmptyState
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
  const locale = useLocale();
  const { formatDate } = useDateFormat();
  const isAdmin = useIsOrganizationAdmin();
  const deleteCampaign = useDeleteCampaign();
  const [formOpen, setFormOpen] = useState(false);
  const [confirmOpen, setConfirmOpen] = useState(false);

  const handleDelete = () => {
    deleteCampaign.mutate(campaign.id, {
      onSuccess: () => router.replace("/campaigns"),
    });
  };

  const none = (label: string) => <span className="text-muted-foreground">{label}</span>;
  const dates = formatCampaignDates(campaign.startDate, campaign.endDate, locale, t);

  return (
    <div className="flex flex-col gap-6">
      <PageHeading
        title={campaign.name}
        back={{ href: "/campaigns", label: t("detail.back") }}
        linkComponent={Link}
        description={
          <div className="flex flex-wrap items-center gap-2">
            <span className="text-muted-foreground text-sm tabular-nums">
              {t("detail.timestamps", {
                created: formatDate(campaign.createdAt),
                updated: formatDate(campaign.updatedAt),
              })}
            </span>
          </div>
        }
        actions={
          <>
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
          </>
        }
      />

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
            <DetailItem label={t("detail.dates")}>
              {dates === null ? none(t("noDates")) : <span className="tabular-nums">{dates}</span>}
            </DetailItem>
            <DetailItem label={t("detail.owner")}>
              {campaign.owner ? campaign.owner.name : none(t("noOwner"))}
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
            <DetailItem label={t("detail.description")} className="sm:col-span-2">
              {campaign.description ? (
                <span className="whitespace-pre-wrap">{campaign.description}</span>
              ) : (
                none(t("noDescription"))
              )}
            </DetailItem>
          </dl>
        </CardContent>
      </Card>

      <CampaignContentTable campaignId={campaign.id} />

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

/** The linked ICP by name; "ICP" until it is read or when it cannot be read. */
function CampaignIcpName({ icpId }: { icpId: string }) {
  const t = useTranslations("campaigns.detail");
  const icp = useIcp(icpId);
  return <>{icp.data?.data?.name ?? t("icpUnnamed")}</>;
}
