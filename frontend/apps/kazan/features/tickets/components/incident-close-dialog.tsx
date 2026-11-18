"use client";

import { useId, useState, type FormEvent } from "react";
import { useTranslations } from "next-intl";
import { AlertCircle, Loader2 } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { InlineAlert } from "@/components/ui/inline-alert";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { useTicket } from "../hooks/use-tickets";

/** The longest RCA or lessons learned text the ticket request schemas accept. */
const INCIDENT_TEXT_MAX_LENGTH = 10000;

// ── Props ──────────────────────────────────────────────────────────

/** The two texts the server requires before an incident may be set to Done. */
export interface IncidentCloseValues {
  rca: string;
  lessonsLearned: string;
}

export interface IncidentCloseDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  boardId: string;
  /**
   * The incident being closed. When set, the fields are prefilled from its
   * detail, because list responses carry no RCA. Absent for an incident that
   * does not exist yet (the create form).
   */
  ticketId?: string;
  /**
   * Prefill that wins over the loaded detail, such as texts already typed
   * into the ticket form.
   */
  defaultValues?: Partial<IncidentCloseValues>;
  /**
   * Receives both trimmed-non-blank texts. The caller sends them and closes
   * the dialog through `onOpenChange` once that succeeds; the dialog stays
   * open while it does not, so nothing typed is lost.
   */
  onConfirm: (values: IncidentCloseValues) => void;
  isPending?: boolean;
}

// ── Component ──────────────────────────────────────────────────────

/**
 * Asks for the root cause analysis and lessons learned of an incident that is
 * being set to Done, so the close is not refused by the server's rule that a
 * closed incident documents its cause.
 */
export function IncidentCloseDialog({
  open,
  onOpenChange,
  boardId,
  ticketId,
  defaultValues,
  onConfirm,
  isPending,
}: IncidentCloseDialogProps) {
  const t = useTranslations("ticket.incidentClose");
  const detailQuery = useTicket(open ? boardId : "", open ? (ticketId ?? "") : "");
  const detail = detailQuery.data?.data;
  const isLoadingDetail = !!ticketId && detailQuery.isLoading;
  // A failed load must not open blank fields: confirming them would replace
  // texts the incident may already hold.
  const detailFailed = !!ticketId && detailQuery.isError;

  const initialValues: IncidentCloseValues = {
    rca: defaultValues?.rca ?? detail?.rca ?? "",
    lessonsLearned: defaultValues?.lessonsLearned ?? detail?.lessonsLearned ?? "",
  };

  return (
    <Dialog open={open} onOpenChange={(next) => !isPending && onOpenChange(next)}>
      <DialogContent className="sm:max-w-[520px]">
        <DialogHeader>
          <DialogTitle>{t("title")}</DialogTitle>
          <DialogDescription>{t("description")}</DialogDescription>
        </DialogHeader>

        {isLoadingDetail ? (
          <div className="flex items-center justify-center py-8">
            <Loader2 className="text-muted-foreground h-5 w-5 animate-spin" />
          </div>
        ) : detailFailed ? (
          <div className="flex flex-col gap-4">
            <InlineAlert variant="danger" icon={<AlertCircle />} description={t("loadFailed")} />
            <DialogFooter>
              <Button type="button" variant="ghost" onClick={() => onOpenChange(false)}>
                {t("cancel")}
              </Button>
              <Button type="button" onClick={() => void detailQuery.refetch()}>
                {t("retry")}
              </Button>
            </DialogFooter>
          </div>
        ) : (
          // Mounted only once the detail is in, so the fields start from the
          // stored texts instead of from blanks that a late load would overwrite.
          <IncidentCloseForm
            key={ticketId ?? "new"}
            initialValues={initialValues}
            onCancel={() => onOpenChange(false)}
            onConfirm={onConfirm}
            isPending={isPending}
          />
        )}
      </DialogContent>
    </Dialog>
  );
}

// ── Form ───────────────────────────────────────────────────────────

interface IncidentCloseFormProps {
  initialValues: IncidentCloseValues;
  onCancel: () => void;
  onConfirm: (values: IncidentCloseValues) => void;
  isPending?: boolean;
}

function IncidentCloseForm({ initialValues, onCancel, onConfirm, isPending }: IncidentCloseFormProps) {
  const t = useTranslations("ticket.incidentClose");
  const idPrefix = useId();
  const [rca, setRca] = useState(initialValues.rca);
  const [lessonsLearned, setLessonsLearned] = useState(initialValues.lessonsLearned);
  const [touched, setTouched] = useState({ rca: false, lessonsLearned: false });

  const rcaBlank = rca.trim() === "";
  const lessonsBlank = lessonsLearned.trim() === "";
  const canConfirm = !rcaBlank && !lessonsBlank && !isPending;

  const rcaId = `${idPrefix}-rca`;
  const lessonsId = `${idPrefix}-lessons`;
  const showRcaError = touched.rca && rcaBlank;
  const showLessonsError = touched.lessonsLearned && lessonsBlank;

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!canConfirm) {
      setTouched({ rca: true, lessonsLearned: true });
      return;
    }
    onConfirm({ rca: rca.trim(), lessonsLearned: lessonsLearned.trim() });
  };

  return (
    <form className="flex flex-col gap-4" onSubmit={handleSubmit} noValidate>
      <InlineAlert variant="warning" icon={<AlertCircle />} description={t("warning")} />

      <div className="flex flex-col gap-2">
        <Label htmlFor={rcaId}>{t("rcaLabel")}</Label>
        <Textarea
          id={rcaId}
          value={rca}
          onChange={(event) => setRca(event.target.value)}
          onBlur={() => setTouched((current) => ({ ...current, rca: true }))}
          placeholder={t("rcaPlaceholder")}
          maxLength={INCIDENT_TEXT_MAX_LENGTH}
          rows={4}
          required
          disabled={isPending}
          aria-invalid={showRcaError}
          aria-describedby={showRcaError ? `${rcaId}-error` : undefined}
        />
        {showRcaError && (
          <p id={`${rcaId}-error`} className="text-destructive text-sm">
            {t("required")}
          </p>
        )}
      </div>

      <div className="flex flex-col gap-2">
        <Label htmlFor={lessonsId}>{t("lessonsLabel")}</Label>
        <Textarea
          id={lessonsId}
          value={lessonsLearned}
          onChange={(event) => setLessonsLearned(event.target.value)}
          onBlur={() => setTouched((current) => ({ ...current, lessonsLearned: true }))}
          placeholder={t("lessonsPlaceholder")}
          maxLength={INCIDENT_TEXT_MAX_LENGTH}
          rows={4}
          required
          disabled={isPending}
          aria-invalid={showLessonsError}
          aria-describedby={showLessonsError ? `${lessonsId}-error` : undefined}
        />
        {showLessonsError && (
          <p id={`${lessonsId}-error`} className="text-destructive text-sm">
            {t("required")}
          </p>
        )}
      </div>

      <DialogFooter>
        <Button type="button" variant="ghost" onClick={onCancel} disabled={isPending}>
          {t("cancel")}
        </Button>
        <Button type="submit" disabled={!canConfirm}>
          {isPending ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
          {t("confirm")}
        </Button>
      </DialogFooter>
    </form>
  );
}
