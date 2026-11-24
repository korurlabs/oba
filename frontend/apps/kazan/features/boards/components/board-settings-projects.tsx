"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Pencil, Plus, Trash2 } from "lucide-react";
import { InlineForm } from "./board-settings-inline-form";
import { useTranslations } from "next-intl";
import {
  useProjects,
  useCreateProject,
  useUpdateProject,
  useDeleteProject,
} from "@/features/projects/hooks/use-projects";
import type { ProjectListResponse } from "@/features/projects/schemas";

interface BoardSettingsProjectsProps {
  boardId: string;
  canManage: boolean;
}

const DEFAULT_COLOR = "#3B82F6"; // blue

type FormState =
  | { type: "closed" }
  | { type: "add" }
  | { type: "edit"; project: ProjectListResponse };

export function BoardSettingsProjects({ boardId, canManage }: BoardSettingsProjectsProps) {
  const t = useTranslations();
  const { data: projectsResponse, isLoading } = useProjects(boardId);
  const createMutation = useCreateProject();
  const updateMutation = useUpdateProject();
  const deleteMutation = useDeleteProject();

  const [formState, setFormState] = useState<FormState>({ type: "closed" });
  const [name, setName] = useState("");
  const [color, setColor] = useState(DEFAULT_COLOR);
  const [deleteTarget, setDeleteTarget] = useState<ProjectListResponse | null>(null);

  const projects = projectsResponse?.data ?? [];

  const openAddForm = () => {
    setFormState({ type: "add" });
    setName("");
    setColor(DEFAULT_COLOR);
  };

  const openEditForm = (project: ProjectListResponse) => {
    setFormState({ type: "edit", project });
    setName(project.name);
    setColor(project.color ?? DEFAULT_COLOR);
  };

  const closeForm = () => {
    setFormState({ type: "closed" });
    setName("");
    setColor(DEFAULT_COLOR);
  };

  const handleSave = () => {
    if (!name.trim()) return;

    if (formState.type === "add") {
      createMutation.mutate(
        { boardId, data: { name: name.trim(), color } },
        { onSuccess: closeForm }
      );
    } else if (formState.type === "edit") {
      updateMutation.mutate(
        {
          boardId: formState.project.boardId,
          projectId: formState.project.id,
          data: { name: name.trim(), color },
        },
        { onSuccess: closeForm }
      );
    }
  };

  const handleDelete = () => {
    if (!deleteTarget) return;
    deleteMutation.mutate(
      { boardId: deleteTarget.boardId, projectId: deleteTarget.id },
      { onSuccess: () => setDeleteTarget(null) }
    );
  };

  const isSaving = createMutation.isPending || updateMutation.isPending;

  if (isLoading) {
    return (
      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <Skeleton className="h-5 w-20" />
          <Skeleton className="h-7 w-24" />
        </div>
        <div className="space-y-2">
          {Array.from({ length: 3 }).map((_, i) => (
            <Skeleton key={i} className="h-10 w-full" />
          ))}
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-foreground text-sm font-medium">
          {t("board.settings.projects")}
        </h3>
        {canManage && formState.type === "closed" && (
          <Button
            size="sm"
            variant="ghost"
            onClick={openAddForm}
            className="h-7 text-xs"
          >
            <Plus className="h-4 w-4" />
            {t("board.settings.addProject")}
          </Button>
        )}
      </div>

      {/* Inline form for adding */}
      {formState.type === "add" && (
        <InlineForm
          name={name}
          color={color}
          onNameChange={setName}
          onColorChange={setColor}
          onSave={handleSave}
          onCancel={closeForm}
          isSaving={isSaving}
          namePlaceholder={t("board.settings.projectName")}
        />
      )}

      {/* Projects list */}
      {projects.length === 0 && formState.type === "closed" ? (
        <p className="text-muted-foreground py-6 text-center text-sm">
          {t("board.settings.noProjectsYet")}
        </p>
      ) : (
        <div className="space-y-1">
          {projects.map((project) =>
            formState.type === "edit" && formState.project.id === project.id ? (
              <InlineForm
                key={project.id}
                name={name}
                color={color}
                onNameChange={setName}
                onColorChange={setColor}
                onSave={handleSave}
                onCancel={closeForm}
                isSaving={isSaving}
                namePlaceholder={t("board.settings.projectName")}
              />
            ) : (
              <div
                key={project.id}
                className="flex items-center justify-between rounded-lg p-2"
              >
                <div className="flex items-center gap-3">
                  <span
                    className="inline-block h-3 w-3 shrink-0"
                    style={{ backgroundColor: project.color ?? DEFAULT_COLOR }}
                  />
                  <span className="text-sm font-medium">{project.name}</span>
                  {project.boardId !== boardId && (
                    <span className="text-muted-foreground text-xs">{project.boardName}</span>
                  )}
                </div>
                {canManage && (
                  <div className="flex items-center gap-1">
                    <Button
                      size="sm"
                      variant="ghost"
                      className="text-muted-foreground hover:text-foreground h-8 w-8 p-0"
                      onClick={() => openEditForm(project)}
                      disabled={formState.type !== "closed"}
                    >
                      <Pencil className="h-4 w-4" />
                    </Button>
                    <Button
                      size="sm"
                      variant="ghost"
                      className="text-muted-foreground hover:text-destructive h-8 w-8 p-0"
                      onClick={() => setDeleteTarget(project)}
                      disabled={formState.type !== "closed"}
                    >
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  </div>
                )}
              </div>
            )
          )}
        </div>
      )}

      {/* Delete confirmation */}
      <AlertDialog
        open={deleteTarget !== null}
        onOpenChange={(open) => {
          if (!open) setDeleteTarget(null);
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t("board.settings.deleteProject")}</AlertDialogTitle>
            <AlertDialogDescription>
              {t("board.settings.deleteProjectConfirm")}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={deleteMutation.isPending}>
              {t("common.cancel")}
            </AlertDialogCancel>
            <AlertDialogAction
              onClick={handleDelete}
              disabled={deleteMutation.isPending}
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
            >
              {t("common.delete")}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
