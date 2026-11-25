"use client";

import { useMutation, useQuery, useQueryClient, type QueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { useTranslations } from "next-intl";
import {
  createCheck,
  deleteCheck,
  listChecks,
  setCheckOutcome,
  setCheckStatus,
  updateCheck,
} from "../api";
import { checkErrorKey, isCheckNotFound } from "../lib/check-errors";
import type { CheckOutcome, CheckRequest, CheckStatus } from "../schemas";
import { CHECK_KEYS, CYBERSCORE_KEYS } from "./query-keys";

export { CHECK_KEYS };

/** The organization's checks, sorted by category, sort order and name; `status` filters them. */
export function useChecks(status?: CheckStatus) {
  return useQuery({
    queryKey: CHECK_KEYS.list(status),
    queryFn: () => listChecks(status ? { status } : undefined),
  });
}

/*
 * The server recalculates the score in the same transaction as a create,
 * delete, outcome, weight or category change, so those writes refresh the
 * score and trend with the list. A status change never affects the score and
 * refreshes only the list.
 *
 * Error toasts name a known refusal (duplicate name, check gone, not an
 * admin, invalid field) in the app's words and fall back to the server's
 * message for anything else. A check that is gone also leaves the list.
 */

function invalidateChecks(queryClient: QueryClient) {
  return queryClient.invalidateQueries({ queryKey: CHECK_KEYS.all });
}

function invalidateChecksAndScore(queryClient: QueryClient) {
  return Promise.all([
    queryClient.invalidateQueries({ queryKey: CHECK_KEYS.all }),
    queryClient.invalidateQueries({ queryKey: CYBERSCORE_KEYS.all }),
  ]);
}

function useCheckErrorToast() {
  const queryClient = useQueryClient();
  const t = useTranslations("cyberscore");

  return (error: Error, fallbackKey: "toast.saveError" | "toast.deleteError") => {
    const key = checkErrorKey(error);
    toast.error(key ? t(`errors.${key}`) : error.message || t(fallbackKey));
    if (isCheckNotFound(error)) {
      void invalidateChecksAndScore(queryClient);
    }
  };
}

export function useCreateCheck() {
  const queryClient = useQueryClient();
  const t = useTranslations("cyberscore");
  const showError = useCheckErrorToast();

  return useMutation({
    mutationFn: (data: CheckRequest) => createCheck(data),
    onSuccess: () => {
      toast.success(t("toast.created"));
      void invalidateChecksAndScore(queryClient);
    },
    onError: (error: Error) => showError(error, "toast.saveError"),
  });
}

/*
 * A full edit may change only texts or the status, which leaves the score as
 * it is; the score is refreshed anyway, because the client cannot tell which
 * fields the server counted as a scoring change.
 */
export function useUpdateCheck() {
  const queryClient = useQueryClient();
  const t = useTranslations("cyberscore");
  const showError = useCheckErrorToast();

  return useMutation({
    mutationFn: ({ checkId, data }: { checkId: string; data: CheckRequest }) =>
      updateCheck(checkId, data),
    onSuccess: () => {
      toast.success(t("toast.updated"));
      void invalidateChecksAndScore(queryClient);
    },
    onError: (error: Error) => showError(error, "toast.saveError"),
  });
}

/** Sets the outcome and the status it implies; the score follows at once. */
export function useSetCheckOutcome() {
  const queryClient = useQueryClient();
  const t = useTranslations("cyberscore");
  const showError = useCheckErrorToast();

  return useMutation({
    mutationFn: ({ checkId, outcome }: { checkId: string; outcome: CheckOutcome }) =>
      setCheckOutcome(checkId, { outcome }),
    onSuccess: () => {
      toast.success(t("toast.outcomeUpdated"));
      void invalidateChecksAndScore(queryClient);
    },
    onError: (error: Error) => showError(error, "toast.saveError"),
  });
}

/*
 * The save settles only once the list holds the new status, so a card moved
 * on the board stays in its new column instead of flashing back while the
 * list refetches.
 */
export function useSetCheckStatus() {
  const queryClient = useQueryClient();
  const t = useTranslations("cyberscore");
  const showError = useCheckErrorToast();

  return useMutation({
    mutationFn: ({ checkId, status }: { checkId: string; status: CheckStatus }) =>
      setCheckStatus(checkId, { status }),
    onSuccess: () => {
      toast.success(t("toast.statusUpdated"));
      return invalidateChecks(queryClient);
    },
    onError: (error: Error) => showError(error, "toast.saveError"),
  });
}

export function useDeleteCheck() {
  const queryClient = useQueryClient();
  const t = useTranslations("cyberscore");
  const showError = useCheckErrorToast();

  return useMutation({
    mutationFn: (checkId: string) => deleteCheck(checkId),
    onSuccess: () => {
      toast.success(t("toast.deleted"));
      void invalidateChecksAndScore(queryClient);
    },
    onError: (error: Error) => showError(error, "toast.deleteError"),
  });
}
