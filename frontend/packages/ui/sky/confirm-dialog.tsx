"use client";

import { useState, type ReactNode } from "react";

import { Button } from "./button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "./dialog";

export type ConfirmDialogProps = {
  /** The button that opens the dialog; left out when the caller opens it through `open`. */
  trigger?: ReactNode;
  title: string;
  description: ReactNode;
  confirmLabel: string;
  destructive?: boolean;
  /** Runs the action; a rejection keeps the dialog open with the refusal shown. */
  onConfirm: () => Promise<unknown>;
  /** The sentence shown for a rejection; the app passes the one that reads its API's error codes. */
  describeError?: (error: unknown) => string;
  cancelLabel?: string;
  /**
   * Whether the dialog is open, for a caller whose opening control does not
   * stay mounted, such as a menu item: the menu unmounts its items when it
   * closes, which would take a dialog inside it along. Uncontrolled when left out.
   */
  open?: boolean;
  /** Told of every open and close, including the close after a confirmed action. */
  onOpenChange?: (open: boolean) => void;
  /**
   * Where focus goes once the dialog closes. Without a trigger nothing takes
   * it back, so a caller that opens the dialog through `open` sends it to the
   * control that opened it.
   */
  onCloseAutoFocus?: (event: Event) => void;
};

/** The rejection's own message, for an app that has no wording of its own for its API's refusals. */
function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

/**
 * Asks before an action that cannot be undone, and shows why the server refused it.
 * Opens from its `trigger`, or through `open` when the control that opens it does
 * not stay mounted.
 */
export function ConfirmDialog({
  trigger,
  title,
  description,
  confirmLabel,
  destructive = false,
  onConfirm,
  describeError = errorMessage,
  cancelLabel = "Cancel",
  open: openProp,
  onOpenChange,
  onCloseAutoFocus,
}: ConfirmDialogProps) {
  const [openState, setOpenState] = useState(false);
  const open = openProp ?? openState;
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Every close drops the refusal shown, so the dialog opens clean next time.
  const setOpen = (next: boolean) => {
    setOpenState(next);
    if (!next) setError(null);
    onOpenChange?.(next);
  };

  const confirm = async () => {
    setBusy(true);
    setError(null);
    try {
      await onConfirm();
      setOpen(false);
    } catch (failure: unknown) {
      setError(describeError(failure));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      {trigger !== undefined && <DialogTrigger asChild>{trigger}</DialogTrigger>}
      <DialogContent onCloseAutoFocus={onCloseAutoFocus}>
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription asChild>
            <div>{description}</div>
          </DialogDescription>
        </DialogHeader>
        {error !== null && (
          <p className="text-error text-sm" role="alert">
            {error}
          </p>
        )}
        <DialogFooter>
          <Button variant="outline" onClick={() => setOpen(false)} disabled={busy}>
            {cancelLabel}
          </Button>
          <Button
            variant={destructive ? "destructive" : "default"}
            onClick={() => void confirm()}
            disabled={busy}
          >
            {confirmLabel}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
