"use client";

import { useState, type FormEvent, type ReactNode } from "react";

import { Button } from "@oba/ui/sky/button";
import { describeError } from "@/lib/api/error-text";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@oba/ui/sky/dialog";
import { Field, Textarea } from "@oba/ui/sky/field";

import { firstIssue, reasonSchema } from "@/lib/forms";

export type ReasonDialogProps = {
  /** The button that opens the dialog; left out when the caller opens it through `open`. */
  trigger?: ReactNode;
  title: string;
  /** What the action will change, named before it runs. */
  description: ReactNode;
  /** Extra fields above the reason. */
  children?: ReactNode;
  confirmLabel: string;
  /** A reason the form refuses before sending, such as a missing choice. */
  blocker?: string | null;
  /** Whether the confirm button warns, as for an action on a member; a submit for review does not. */
  destructive?: boolean;
  onConfirm: (reason: string) => Promise<unknown>;
  /**
   * Whether the dialog is open, for a caller that opens it from more than one
   * place, such as a button and a keyboard shortcut; uncontrolled when left out.
   */
  open?: boolean;
  onOpenChange?: (open: boolean) => void;
};

/** An action that needs a reason of 10 to 1000 characters for its audit entry. */
export function ReasonDialog({
  trigger,
  title,
  description,
  children,
  confirmLabel,
  blocker = null,
  destructive = true,
  onConfirm,
  open: openProp,
  onOpenChange,
}: ReasonDialogProps) {
  const [openState, setOpenState] = useState(false);
  const open = openProp ?? openState;
  const setOpen = (next: boolean) => {
    setOpenState(next);
    onOpenChange?.(next);
  };
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    const parsed = reasonSchema.safeParse(reason);
    if (!parsed.success) {
      setError(firstIssue(parsed.error));
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await onConfirm(parsed.data);
      setOpen(false);
      setReason("");
    } catch (failure: unknown) {
      setError(describeError(failure));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Dialog
      open={open}
      onOpenChange={(next: boolean) => {
        setOpen(next);
        if (!next) setError(null);
      }}
    >
      {trigger !== undefined && <DialogTrigger asChild>{trigger}</DialogTrigger>}
      <DialogContent>
        <form onSubmit={(event) => void submit(event)} className="flex flex-col gap-4">
          <DialogHeader>
            <DialogTitle>{title}</DialogTitle>
            <DialogDescription asChild>
              <div className="flex flex-col gap-2">{description}</div>
            </DialogDescription>
          </DialogHeader>
          {children}
          <Field
            label="Reason"
            hint={`${reason.trim().length} of 10 to 1000 characters; kept in the audit log.`}
          >
            <Textarea
              value={reason}
              maxLength={1000}
              onChange={(event) => setReason(event.target.value)}
            />
          </Field>
          {(blocker ?? error) !== null && (
            <p className="text-error text-sm" role="alert">
              {blocker ?? error}
            </p>
          )}
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setOpen(false)} disabled={busy}>
              Cancel
            </Button>
            <Button
              type="submit"
              variant={destructive ? "destructive" : "default"}
              disabled={busy || blocker !== null}
            >
              {confirmLabel}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
