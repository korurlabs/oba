"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { useTranslations } from "next-intl";
import {
  listProjects,
  getProjectById,
  createProject,
  updateProject,
  deleteProject,
} from "../api";
import type {
  ProjectCreateRequest,
  ProjectUpdateRequest,
} from "../schemas";

const KEYS = {
  all: ["projects"] as const,
  list: (boardId: string) => [...KEYS.all, "list", boardId] as const,
  detail: (boardId: string, projectId: string) =>
    [...KEYS.all, "detail", boardId, projectId] as const,
};

export function useProjects(
  boardId: string,
  options?: { enabled?: boolean }
) {
  const enabled = options?.enabled ?? true;
  return useQuery({
    queryKey: KEYS.list(boardId),
    queryFn: () => listProjects(boardId),
    enabled: !!boardId && enabled,
  });
}

export function useProject(boardId: string, projectId: string) {
  return useQuery({
    queryKey: KEYS.detail(boardId, projectId),
    queryFn: () => getProjectById(boardId, projectId),
    enabled: !!boardId && !!projectId,
  });
}

export function useCreateProject() {
  const queryClient = useQueryClient();
  const t = useTranslations("kaizen");

  return useMutation({
    mutationFn: ({
      boardId,
      data,
    }: {
      boardId: string;
      data: ProjectCreateRequest;
    }) => createProject(boardId, data),
    onSuccess: () => {
      toast.success(t("projects.messages.created"));
      queryClient.invalidateQueries({ queryKey: KEYS.all });
    },
    onError: () => {
      toast.error(t("projects.messages.createError"));
    },
  });
}

export function useUpdateProject() {
  const queryClient = useQueryClient();
  const t = useTranslations("kaizen");

  return useMutation({
    mutationFn: ({
      boardId,
      projectId,
      data,
    }: {
      boardId: string;
      projectId: string;
      data: ProjectUpdateRequest;
    }) => updateProject(boardId, projectId, data),
    onSuccess: () => {
      toast.success(t("projects.messages.updated"));
      queryClient.invalidateQueries({ queryKey: KEYS.all });
    },
    onError: () => {
      toast.error(t("projects.messages.updateError"));
    },
  });
}

export function useDeleteProject() {
  const queryClient = useQueryClient();
  const t = useTranslations("kaizen");

  return useMutation({
    mutationFn: ({
      boardId,
      projectId,
    }: {
      boardId: string;
      projectId: string;
    }) => deleteProject(boardId, projectId),
    onSuccess: () => {
      toast.success(t("projects.messages.deleted"));
      queryClient.invalidateQueries({ queryKey: KEYS.all });
    },
    onError: () => {
      toast.error(t("projects.messages.deleteError"));
    },
  });
}
