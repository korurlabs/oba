"use client";

import { useMutation, useQuery, useQueryClient, type QueryClient } from "@tanstack/react-query";

import { useIdentity } from "@/lib/identity/identity-context";

import {
  createLesson,
  deleteLesson,
  getLesson,
  getLessonFilters,
  listLessons,
  updateLesson,
} from "../api";
import type { LessonQueryRequest, LessonRequest } from "../schemas";

export const LESSONS_PAGE_SIZE = 25;

/** How often an open list reloads, so a lesson an agent records shows without a reload. */
export const LESSON_REFRESH_MS = 10_000;

/** Every key starts with the organization, so answers of one never show under another. */
export const LESSON_KEYS = {
  all: (organizationId: string) => ["lessons", organizationId] as const,
  lists: (organizationId: string) => [...LESSON_KEYS.all(organizationId), "list"] as const,
  list: (organizationId: string, params: LessonQueryRequest) =>
    [...LESSON_KEYS.lists(organizationId), params] as const,
  filters: (organizationId: string) => [...LESSON_KEYS.all(organizationId), "filters"] as const,
  detail: (organizationId: string, lessonId: string) =>
    [...LESSON_KEYS.all(organizationId), "detail", lessonId] as const,
};

/** One page of the organization's lessons; `enabled` holds the read until its filter is known. */
export function useLessons(params: LessonQueryRequest, options: { enabled?: boolean } = {}) {
  const { organization } = useIdentity();
  return useQuery({
    queryKey: LESSON_KEYS.list(organization.organizationId, params),
    queryFn: () => listLessons(params),
    enabled: options.enabled ?? true,
    refetchInterval: LESSON_REFRESH_MS,
    // A new filter or page keeps the current rows until its answer arrives,
    // but never the rows of the organization the user just left.
    placeholderData: (previous, previousQuery) =>
      previousQuery?.queryKey[1] === organization.organizationId ? previous : undefined,
  });
}

export function useLessonFilters() {
  const { organization } = useIdentity();
  return useQuery({
    queryKey: LESSON_KEYS.filters(organization.organizationId),
    queryFn: getLessonFilters,
  });
}

export function useLesson(lessonId: string) {
  const { organization } = useIdentity();
  return useQuery({
    queryKey: LESSON_KEYS.detail(organization.organizationId, lessonId),
    queryFn: () => getLesson(lessonId),
    enabled: lessonId !== "",
  });
}

/** Any lesson change alters the lists, and can add or empty a repository or user of the filters. */
async function invalidateLessonLists(
  queryClient: QueryClient,
  organizationId: string
): Promise<void> {
  await Promise.all(
    [LESSON_KEYS.lists(organizationId), LESSON_KEYS.filters(organizationId)].map((queryKey) =>
      queryClient.invalidateQueries({ queryKey })
    )
  );
}

// The dialog that runs a mutation shows the refusal from its `error`, so the
// hooks only log it.

export function useCreateLesson() {
  const queryClient = useQueryClient();
  const { organization } = useIdentity();
  return useMutation({
    mutationFn: (data: LessonRequest) => createLesson(data),
    onSuccess: () => invalidateLessonLists(queryClient, organization.organizationId),
    onError: (error: unknown) => console.warn("[lessons] Create refused", error),
  });
}

export function useUpdateLesson() {
  const queryClient = useQueryClient();
  const { organization } = useIdentity();
  return useMutation({
    mutationFn: ({ lessonId, data }: { lessonId: string; data: LessonRequest }) =>
      updateLesson(lessonId, data),
    onSuccess: (_result, { lessonId }) =>
      Promise.all([
        queryClient.invalidateQueries({
          queryKey: LESSON_KEYS.detail(organization.organizationId, lessonId),
        }),
        invalidateLessonLists(queryClient, organization.organizationId),
      ]),
    onError: (error: unknown) => console.warn("[lessons] Edit refused", error),
  });
}

/** The lesson is gone for good, so its detail entry is dropped, not reloaded into a 404. */
export function useDeleteLesson() {
  const queryClient = useQueryClient();
  const { organization } = useIdentity();
  return useMutation({
    mutationFn: (lessonId: string) => deleteLesson(lessonId),
    onSuccess: (_result, lessonId) => {
      queryClient.removeQueries({
        queryKey: LESSON_KEYS.detail(organization.organizationId, lessonId),
      });
      return invalidateLessonLists(queryClient, organization.organizationId);
    },
    onError: (error: unknown) => console.warn("[lessons] Delete refused", error),
  });
}
