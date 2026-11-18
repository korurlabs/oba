"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { useTranslations } from "next-intl";
import {
  createAlertRoute,
  deleteAlertRoute,
  listAlertRoutes,
  updateAlertRoute,
} from "../api";
import type { AlertRouteCreateRequest, AlertRouteUpdateRequest } from "../schemas";
import { ALERT_ROUTE_KEYS } from "./query-keys";

export { ALERT_ROUTE_KEYS };

const KEYS = ALERT_ROUTE_KEYS;

export function useAlertRoutes() {
  return useQuery({
    queryKey: KEYS.list(),
    queryFn: listAlertRoutes,
  });
}

/*
 * The error toasts show the server's message: it names the actual refusal
 * (not an admin, not a member of the board, the row is gone), which a generic
 * text would hide. The shared API client keeps only that message, not the
 * status, so a duplicate system is caught in the dialog before the request is
 * sent; a concurrent duplicate still arrives here with the server's text.
 */

export function useCreateAlertRoute() {
  const queryClient = useQueryClient();
  const t = useTranslations("alertRoutes");

  return useMutation({
    mutationFn: (data: AlertRouteCreateRequest) => createAlertRoute(data),
    onSuccess: () => {
      toast.success(t("toast.created"));
      void queryClient.invalidateQueries({ queryKey: KEYS.all });
    },
    onError: (error: Error) => {
      toast.error(error.message || t("toast.saveError"));
    },
  });
}

export function useUpdateAlertRoute() {
  const queryClient = useQueryClient();
  const t = useTranslations("alertRoutes");

  return useMutation({
    mutationFn: ({ routeId, data }: { routeId: string; data: AlertRouteUpdateRequest }) =>
      updateAlertRoute(routeId, data),
    onSuccess: () => {
      toast.success(t("toast.updated"));
      void queryClient.invalidateQueries({ queryKey: KEYS.all });
    },
    onError: (error: Error) => {
      toast.error(error.message || t("toast.saveError"));
      // A row removed meanwhile must disappear from the table too.
      void queryClient.invalidateQueries({ queryKey: KEYS.all });
    },
  });
}

export function useDeleteAlertRoute() {
  const queryClient = useQueryClient();
  const t = useTranslations("alertRoutes");

  return useMutation({
    mutationFn: (routeId: string) => deleteAlertRoute(routeId),
    onSuccess: () => {
      toast.success(t("toast.removed"));
      void queryClient.invalidateQueries({ queryKey: KEYS.all });
    },
    onError: (error: Error) => {
      toast.error(error.message || t("toast.removeError"));
      void queryClient.invalidateQueries({ queryKey: KEYS.all });
    },
  });
}
