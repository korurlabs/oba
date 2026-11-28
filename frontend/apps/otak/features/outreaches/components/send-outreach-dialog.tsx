"use client";

import { useEffect, useState } from "react";
import { useTranslations } from "next-intl";
import { AlertTriangle } from "lucide-react";
import { isApiError } from "@oba/shared/api/axios";
import { Link } from "@oba/shared/libs/i18n/navigation";
import { Alert, AlertDescription, AlertTitle } from "@oba/ui/sky/alert";
import { Button } from "@oba/ui/sky/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@oba/ui/sky/dialog";
import { LoadError, TableLoading } from "@oba/ui/sky/page-states";
import { useMailSenders } from "@/features/mail-senders/hooks/use-mail-senders";
import { mailSenderErrorText } from "@/features/mail-senders/lib/mail-sender-error";
import { useSendOutreach } from "../hooks/use-outreaches";
import { outreachErrorText } from "../lib/outreach-error";
import { OutreachStatus, type OutreachListItem } from "../schemas";

export interface SendOutreachDialogProps {
  /** The DRAFT or FAILED outreach to send; null keeps the dialog closed. */
  outreach: OutreachListItem | null;
  onOpenChange: (open: boolean) => void;
}

/**
 * Sends an outreach from one of the organization's mail senders. The mail
 * goes to the contact's current address with the attachments as PDFs. A
 * refusal stays in the dialog; a failure in Microsoft 365 leaves the
 * outreach FAILED, which can be sent again.
 */
export function SendOutreachDialog({ outreach, onOpenChange }: SendOutreachDialogProps) {
  const t = useTranslations("outreaches");
  const tSend = useTranslations("outreaches.sendDialog");
  const tSenders = useTranslations("mailSenders");
  const tErrors = useTranslations("errors");
  const senders = useMailSenders();
  const sendOutreach = useSendOutreach();
  const [senderId, setSenderId] = useState<string | null>(null);
  const open = outreach !== null;
  const isPending = sendOutreach.isPending;
  const { reset } = sendOutreach;

  // Each open starts without a sender and without the last attempt's error.
  useEffect(() => {
    if (open) {
      setSenderId(null);
      reset();
    }
  }, [open, outreach?.id, reset]);

  const rows = senders.data?.data ?? [];
  const error = sendOutreach.error;
  const failed = isApiError(error, "NTF_UPS_001");

  const handleSend = () => {
    if (!outreach || senderId === null) return;
    sendOutreach.mutate(
      { outreachId: outreach.id, mailSenderId: senderId },
      { onSuccess: () => onOpenChange(false) }
    );
  };

  return (
    <Dialog open={open} onOpenChange={(next) => !isPending && onOpenChange(next)}>
      <DialogContent className="sm:max-w-xl">
        <DialogHeader>
          <DialogTitle className="break-words">
            {tSend("title", { title: outreach?.title ?? "" })}
          </DialogTitle>
          <DialogDescription>
            {outreach?.contact
              ? tSend("description", { name: outreach.contact.name })
              : tSend("descriptionNoContact")}
          </DialogDescription>
        </DialogHeader>

        {outreach?.status === OutreachStatus.FAILED && error === null && (
          <p className="text-warning-text flex items-start gap-2 text-sm" role="status">
            <AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
            {tSend("previouslyFailed")}
          </p>
        )}

        {senders.isError && senders.data === undefined ? (
          <LoadError
            title={tSenders("loadError")}
            message={mailSenderErrorText(senders.error, tSenders, "errors.loadHint")}
            retryLabel={tErrors("tryAgain")}
            onRetry={() => void senders.refetch()}
          />
        ) : senders.data === undefined ? (
          <TableLoading label={tSenders("loading")} />
        ) : rows.length === 0 ? (
          <div className="flex flex-col items-start gap-3">
            <p className="font-medium">{tSend("noSendersTitle")}</p>
            <p className="text-muted-foreground text-sm">{tSend("noSendersHint")}</p>
            <Button asChild variant="outline">
              <Link href={{ pathname: "/mail", query: { tab: "senders" } }}>
                {tSend("goToSenders")}
              </Link>
            </Button>
          </div>
        ) : (
          <fieldset className="flex max-h-[40vh] flex-col gap-1 overflow-y-auto">
            <legend className="mb-2 text-sm font-medium">{tSend("senderLegend")}</legend>
            {rows.map((sender) => (
              <label
                key={sender.id}
                className="hover:bg-muted has-[:checked]:border-primary flex cursor-pointer items-center gap-3 rounded-md border px-3 py-2"
              >
                <input
                  type="radio"
                  name="mail-sender"
                  value={sender.id}
                  checked={senderId === sender.id}
                  disabled={isPending}
                  onChange={() => setSenderId(sender.id)}
                />
                <span className="min-w-0 flex-1 truncate text-sm font-medium">
                  {sender.mailAddress}
                </span>
                <span className="text-muted-foreground text-sm whitespace-nowrap">
                  {tSenders(`types.${sender.type}`)}
                </span>
              </label>
            ))}
          </fieldset>
        )}

        {error !== null && (
          <Alert variant="destructive">
            <AlertTriangle aria-hidden="true" />
            <AlertTitle>{failed ? tSend("failedTitle") : tSend("refusedTitle")}</AlertTitle>
            <AlertDescription>
              <p>{outreachErrorText(error, t, "toast.sendError")}</p>
              {failed && <p>{tSend("failedHint")}</p>}
            </AlertDescription>
          </Alert>
        )}

        <DialogFooter>
          <Button
            type="button"
            variant="outline"
            disabled={isPending}
            onClick={() => onOpenChange(false)}
          >
            {t("form.cancel")}
          </Button>
          <Button type="button" disabled={isPending || senderId === null} onClick={handleSend}>
            {isPending ? tSend("sending") : tSend("send")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
