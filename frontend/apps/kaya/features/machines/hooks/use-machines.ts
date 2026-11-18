"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { useIdentity } from "@/lib/identity/identity-context";
import { invalidatePeopleQueries } from "@/lib/invalidate-related-queries";

import {
  createMachine,
  getMachine,
  listMachines,
  retireMachine,
  revokeMachineToken,
  rotateMachineToken,
} from "../api";
import type { MachineCreateRequest, MachineQueryRequest } from "../schemas";

/** The machine list reloads every minute while the page is visible, so usage stays current. */
export const MACHINES_REFETCH_MS = 60_000;

const KEYS = {
  all: (organizationId: string) => ["machines", organizationId] as const,
  list: (organizationId: string, params: MachineQueryRequest) =>
    [...KEYS.all(organizationId), "list", params] as const,
  detail: (organizationId: string, id: string) =>
    [...KEYS.all(organizationId), "detail", id] as const,
};

export function useMachines(params: MachineQueryRequest = {}) {
  const { organization } = useIdentity();
  return useQuery({
    queryKey: KEYS.list(organization.organizationId, params),
    queryFn: () => listMachines(params),
    // TanStack Query pauses an interval while the tab is hidden.
    refetchInterval: MACHINES_REFETCH_MS,
  });
}

export function useMachine(id: string) {
  const { organization } = useIdentity();
  return useQuery({
    queryKey: KEYS.detail(organization.organizationId, id),
    queryFn: () => getMachine(id),
    enabled: id !== "",
    refetchInterval: MACHINES_REFETCH_MS,
  });
}

/**
 * Every machine change can change user counts and what sessions show, so each
 * mutation reloads users, machines and sessions. Errors are shown by the dialog
 * that ran the mutation, from its `error`.
 */
function useMachineMutation<TArgs, TResult>(mutationFn: (args: TArgs) => Promise<TResult>) {
  const queryClient = useQueryClient();
  const { organization } = useIdentity();
  return useMutation({
    mutationFn,
    onSuccess: () => invalidatePeopleQueries(queryClient, organization.organizationId),
    onError: (error: unknown) => console.warn("[machines] Change refused", error),
  });
}

export function useCreateMachine() {
  return useMachineMutation((data: MachineCreateRequest) => createMachine(data));
}

export function useRotateMachineToken() {
  return useMachineMutation((id: string) => rotateMachineToken(id));
}

export function useRevokeMachineToken() {
  return useMachineMutation((id: string) => revokeMachineToken(id));
}

export function useRetireMachine() {
  return useMachineMutation((id: string) => retireMachine(id));
}
