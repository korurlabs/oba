"use client";

import { useMemo } from "react";
import { useForm, useWatch, type Control } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useDebounce } from "@oba/shared/hooks/use-debounce";
import type { TicketListResponse } from "@/features/tickets/schemas";
import {
  UPDATE_PERCENTAGE_MAX,
  UPDATE_PERCENTAGE_MIN,
  updateFormDraftSchema,
  updateFormSchema,
  type UpdateCreateRequest,
  type UpdateFormLine,
  type UpdateFormValues,
  type UpdateTicketResponse,
} from "../schemas";

/** The preview follows the form after the user stops typing for this long. */
export const UPDATE_PREVIEW_DEBOUNCE_MS = 400;

// ── Mapping ─────────────────────────────────────────────────────────

/** A ticket picked from a list starts at the ticket's own progress. */
export function lineFromTicket(ticket: TicketListResponse): UpdateFormLine {
  return {
    ticketId: ticket.id,
    percentage: Math.min(
      UPDATE_PERCENTAGE_MAX,
      Math.max(UPDATE_PERCENTAGE_MIN, Math.round(ticket.percentage ?? 0)),
    ),
    note: "",
    ticketKey: ticket.key,
    ticketTitle: ticket.title,
    boardId: ticket.board?.id ?? "",
    boardName: ticket.board?.name ?? "",
    canOpen: true,
  };
}

/** A line of a stored update, with its snapshot, percentage and note. */
export function lineFromUpdateItem(
  item: UpdateTicketResponse & { ticketId: string },
): UpdateFormLine {
  return {
    ticketId: item.ticketId,
    percentage: item.percentage,
    note: item.note ?? "",
    ticketKey: item.ticketKey,
    ticketTitle: item.ticketTitle,
    boardId: item.boardId,
    boardName: item.boardName,
    canOpen: item.canOpen,
  };
}

/** Blank texts are sent as null; the server renders them as "- None". */
function textOrNull(text: string): string | null {
  return text.trim() ? text : null;
}

/** The create and edit body: lines in card order. */
export function toUpdateRequest(values: UpdateFormValues): UpdateCreateRequest {
  return {
    tickets: values.tickets.map((line) => ({
      ticketId: line.ticketId,
      percentage: line.percentage,
      note: textOrNull(line.note),
    })),
    focus: textOrNull(values.focus),
    questions: textOrNull(values.questions),
  };
}

// ── Hooks ───────────────────────────────────────────────────────────

/**
 * The create and edit form. Validates on change, so a wrong percentage or a
 * too long text is flagged while typing, not only on Confirm.
 */
export function useUpdateForm(defaultValues: UpdateFormValues) {
  return useForm<UpdateFormValues>({
    resolver: zodResolver(updateFormSchema),
    mode: "onChange",
    defaultValues,
  });
}

/**
 * The request the preview should render: the current form content once it
 * has been unchanged for `UPDATE_PREVIEW_DEBOUNCE_MS`, or `null` while the
 * draft is invalid, so an invalid draft never reaches the server. `isValid`
 * is the validity of the draft as it is now, without the delay.
 *
 * It checks the draft rules, not the posting rules: an empty focus still
 * previews, so the author sees the card while composing. Confirm and Save
 * enforce the focus through the form resolver.
 *
 * The request travels as a JSON string through the debounce, so an equal
 * form gives an equal string and the preview query key stays stable.
 */
export function useUpdatePreviewRequest(control: Control<UpdateFormValues>): {
  request: UpdateCreateRequest | null;
  isValid: boolean;
} {
  const values = useWatch({ control });

  const requestJson = useMemo(() => {
    const parsed = updateFormDraftSchema.safeParse(values);
    return parsed.success ? JSON.stringify(toUpdateRequest(parsed.data)) : null;
  }, [values]);

  const { debouncedValue } = useDebounce(requestJson, UPDATE_PREVIEW_DEBOUNCE_MS);

  const request = useMemo(
    () => (debouncedValue ? (JSON.parse(debouncedValue) as UpdateCreateRequest) : null),
    [debouncedValue],
  );

  return { request, isValid: requestJson !== null };
}
