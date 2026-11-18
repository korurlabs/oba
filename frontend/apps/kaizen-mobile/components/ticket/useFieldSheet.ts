import * as React from "react";

import {
  useDraftTicket,
  type DraftTicketChange,
  type DraftTicketFields,
} from "@/components/ticket/draftTicketStore";
import { useSheetTicket } from "@/components/ticket/useSheetTicket";
import { useWriteFailure } from "@/components/ticket/useWriteFailure";
import { useUpdateTicket, type TicketUpdate } from "@/hooks/queries/ticketActions";
import { toApiError, type ApiError } from "@/lib/api";

/*
 * The two sources a field sheet edits. Each picker takes a value and an
 * onChange; these hooks give it one: a saved ticket, whose change is written
 * at once, or the open New ticket draft, whose change is kept until Create.
 */

/**
 * A field sheet over a saved ticket: the cached ticket, and `save`, which
 * writes a change and closes the sheet once it lands. The ticket beneath
 * shows the change at once; a failed write puts it back, keeps the sheet
 * open and is toasted with Retry, unless `onInvalid` takes a refused field.
 */
export function useSavedTicketField() {
  const fail = useWriteFailure();
  const sheet = useSheetTicket();
  const update = useUpdateTicket(sheet.ticketRef);
  const [saved, setSaved] = React.useState<boolean>(false);

  const save = React.useCallback(
    (change: TicketUpdate, onInvalid?: (error: ApiError) => void) => {
      const run = () =>
        update.mutate(change, {
          onSuccess: () => setSaved(true),
          onError: (failure: Error) => {
            const apiError: ApiError = toApiError(failure);
            if (onInvalid && apiError.kind === "validation") {
              onInvalid(apiError);
              return;
            }
            fail(apiError, "put", run);
          },
        });
      run();
    },
    [fail, update]
  );

  return { ...sheet, saving: update.isPending, saved, save };
}

/**
 * A field sheet over the open New ticket draft: its fields, and `save`,
 * which puts a change into the draft and closes the sheet. Without an open
 * draft, such as after the screen beneath closed, the sheet closes.
 */
export function useDraftTicketField() {
  const session = useDraftTicket();
  const [saved, setSaved] = React.useState<boolean>(false);

  const save = React.useCallback(
    (patch: DraftTicketChange) => {
      session?.change(patch);
      setSaved(true);
    },
    [session]
  );

  const fields: DraftTicketFields | undefined = session?.fields;
  return { fields, save, closed: saved || !session };
}
