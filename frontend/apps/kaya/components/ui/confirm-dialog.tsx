"use client";

import {
  ConfirmDialog as SkyConfirmDialog,
  type ConfirmDialogProps as SkyConfirmDialogProps,
} from "@oba/ui/sky/confirm-dialog";

import { describeError } from "@/lib/api/error-text";

export type ConfirmDialogProps = Omit<SkyConfirmDialogProps, "describeError">;

/** Asks before an action that cannot be undone, and shows why Kaya refused it, in Kaya's words. */
export function ConfirmDialog(props: ConfirmDialogProps) {
  return <SkyConfirmDialog {...props} describeError={describeError} />;
}
