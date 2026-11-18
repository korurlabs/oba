"use client";

import { useTranslations } from "next-intl";
import { useFormContext, useFormState, type FieldArrayWithId } from "react-hook-form";
import { ListPlus, RefreshCw } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { FormControl, FormField, FormItem } from "@/components/ui/form";
import { UPDATE_PERCENTAGE_MAX, UPDATE_PERCENTAGE_MIN, UPDATE_TICKETS_MAX } from "../schemas";
import type { UpdateFormLine, UpdateFormValues, UpdateTicketResponse } from "../schemas";
import {
  SelectedTicketNoAccessBadge,
  SelectedTicketSummary,
  SelectedTicketsSection,
} from "./selected-ticket-list";
import { UpdateTicketKey } from "./update-ticket-key";

export type SelectedTicketField = FieldArrayWithId<UpdateFormValues, "tickets", "fieldKey">;

interface SelectedTicketsCardProps {
  fields: SelectedTicketField[];
  onRemove: (index: number) => void;
  /** Ticket IDs whose percentage the preview says will be written to the ticket. */
  progressSyncIds: ReadonlySet<string>;
  /**
   * Lines of an edited update whose ticket no longer exists. They cannot be
   * sent (they have no ticket ID); the server keeps them unchanged.
   */
  keptLines?: UpdateTicketResponse[];
  /**
   * How many of the reader's in-progress tickets the ticket list holds under
   * its current filters that are not on the card yet; 0 disables the action.
   */
  addableInProgressCount: number;
  /** Adds those tickets to the card. */
  onAddMyInProgress: () => void;
}

/**
 * The tickets of the update, in the order they are posted, each with its
 * percentage (starting at the ticket's progress) and an optional note.
 * Ticking or unticking a row in the ticket list adds or removes a line here;
 * the remove button does the same from the card. "Add my in-progress
 * tickets" adds the reader's in-progress tickets from the list as it is
 * filtered, without cutting at the line limit: above it the alert shows and
 * the update cannot be sent, as when ticking rows by hand.
 */
export function SelectedTicketsCard({
  fields,
  onRemove,
  progressSyncIds,
  keptLines = [],
  addableInProgressCount,
  onAddMyInProgress,
}: SelectedTicketsCardProps) {
  const t = useTranslations("updates.form");

  return (
    <SelectedTicketsSection
      title={t("selectedTitle")}
      count={fields.length}
      max={UPDATE_TICKETS_MAX}
      tooManyMessage={t("ticketsTooMany")}
      emptyMessage={t("selectedEmpty")}
      isEmpty={fields.length === 0 && keptLines.length === 0}
      actions={
        // type="button": the card sits inside the update form.
        <Button
          type="button"
          variant="outline"
          size="sm"
          title={t("addMyInProgressHint")}
          disabled={addableInProgressCount === 0}
          onClick={onAddMyInProgress}
        >
          <ListPlus className="mr-1.5 h-4 w-4" />
          {t("addMyInProgress", { count: addableInProgressCount })}
        </Button>
      }
    >
      {fields.map((field, index) => (
        <li key={field.fieldKey} className="px-4 py-3">
          <SelectedTicketRow
            line={field}
            index={index}
            onRemove={() => onRemove(index)}
            syncsProgress={progressSyncIds.has(field.ticketId)}
          />
        </li>
      ))}
      {keptLines.map((line, index) => (
        <li key={`kept-${index}`} className="px-4 py-3">
          <KeptLineRow line={line} />
        </li>
      ))}
    </SelectedTicketsSection>
  );
}

// ── Rows ──────────────────────────────────────────────────────────────

interface SelectedTicketRowProps {
  line: UpdateFormLine;
  index: number;
  onRemove: () => void;
  syncsProgress: boolean;
}

function SelectedTicketRow({ line, index, onRemove, syncsProgress }: SelectedTicketRowProps) {
  const t = useTranslations("updates.form");
  const { control } = useFormContext<UpdateFormValues>();

  return (
    <div className="space-y-2">
      <SelectedTicketSummary
        ticket={line}
        removeLabel={t("remove", { key: line.ticketKey })}
        onRemove={onRemove}
      />

      {/* Label-less inline inputs: each carries an aria-label naming its ticket. */}
      <div className="flex items-start gap-2">
        <FormField
          control={control}
          name={`tickets.${index}.percentage`}
          render={({ field }) => (
            <FormItem className="w-24 shrink-0 space-y-0">
              <FormControl>
                <div className="relative">
                  <Input
                    type="number"
                    inputMode="numeric"
                    min={UPDATE_PERCENTAGE_MIN}
                    max={UPDATE_PERCENTAGE_MAX}
                    step={1}
                    aria-label={t("percentageLabel", { key: line.ticketKey })}
                    name={field.name}
                    ref={field.ref}
                    onBlur={field.onBlur}
                    value={Number.isNaN(field.value) ? "" : field.value}
                    // An emptied input becomes NaN, which the form flags as
                    // out of range instead of silently sending 0.
                    onChange={(event) =>
                      field.onChange(
                        event.target.value === "" ? Number.NaN : Number(event.target.value),
                      )
                    }
                    className="h-8 pr-7 text-sm tabular-nums"
                  />
                  <span
                    aria-hidden="true"
                    className="text-muted-foreground pointer-events-none absolute top-1/2 right-2.5 -translate-y-1/2 text-xs"
                  >
                    %
                  </span>
                </div>
              </FormControl>
            </FormItem>
          )}
        />
        <FormField
          control={control}
          name={`tickets.${index}.note`}
          render={({ field }) => (
            <FormItem className="min-w-0 flex-1 space-y-0">
              <FormControl>
                <Input
                  {...field}
                  aria-label={t("noteLabel", { key: line.ticketKey })}
                  placeholder={t("notePlaceholder")}
                  className="h-8 text-sm"
                />
              </FormControl>
            </FormItem>
          )}
        />
      </div>

      {/* Field errors under both inputs, so a long message never squeezes them. */}
      <RowErrors index={index} />

      {(syncsProgress || !line.canOpen) && (
        <div className="flex flex-wrap items-center gap-2">
          {syncsProgress && (
            <span
              className="text-muted-foreground inline-flex items-center gap-1 text-xs"
              title={t("progressSyncHint")}
            >
              <RefreshCw className="h-3 w-3" />
              {t("progressSync")}
            </span>
          )}
          {!line.canOpen && <SelectedTicketNoAccessBadge hint={t("noAccessHint")} />}
        </div>
      )}
    </div>
  );
}

/**
 * The field errors of one row, below both inputs, so a message never squeezes
 * the narrow percentage input. The messages are i18n keys.
 */
function RowErrors({ index }: { index: number }) {
  const t = useTranslations();
  const { control } = useFormContext<UpdateFormValues>();
  const { errors } = useFormState({ control, name: `tickets.${index}` });
  const lineErrors = errors.tickets?.[index];
  const messages = [lineErrors?.percentage?.message, lineErrors?.note?.message].filter(
    (message): message is string => !!message,
  );
  if (messages.length === 0) return null;
  return (
    <div className="space-y-0.5">
      {messages.map((message) => (
        <p key={message} className="text-destructive text-[0.8rem] font-medium">
          {t(message as Parameters<typeof t>[0])}
        </p>
      ))}
    </div>
  );
}

/** A line whose ticket was deleted: shown as stored, not editable. */
function KeptLineRow({ line }: { line: UpdateTicketResponse }) {
  const t = useTranslations("updates.form");
  return (
    <div className="space-y-1.5 opacity-80">
      <div className="flex min-w-0 flex-wrap items-center gap-x-2 gap-y-1">
        {line.boardName && (
          <Badge variant="outline" className="max-w-40 truncate text-[11px] font-medium">
            {line.boardName}
          </Badge>
        )}
        <UpdateTicketKey item={line} />
        <span className="text-foreground min-w-0 text-sm break-words">{line.ticketTitle}</span>
        <span className="text-foreground text-sm tabular-nums">{line.percentage}%</span>
      </div>
      {line.note && <p className="text-muted-foreground text-xs break-words">{line.note}</p>}
      <Badge
        variant="outline"
        className="text-muted-foreground text-[11px] font-normal"
        title={t("ticketDeletedHint")}
      >
        {t("ticketDeleted")}
      </Badge>
    </div>
  );
}
