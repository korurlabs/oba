"use client";

import { useMemo, useState } from "react";
import { useParams } from "next/navigation";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Badge } from "@/components/ui/badge";
import { BoardDot } from "@/components/ui/board-dot";
import { Settings, Users, Workflow, Tags, FolderKanban } from "lucide-react";
import { BoardMembersList } from "./board-members-list";
import { BoardHierarchy } from "./board-hierarchy";
import { BoardSettingsLabels } from "./board-settings-labels";
import { BoardSettingsProjects } from "./board-settings-projects";
import { useTranslations } from "next-intl";
import {
  useUpdateBoard,
  useDeleteBoard,
  useCachedChildBoards,
} from "@/features/boards/hooks/use-boards";
import { BoardUpdateRequest, boardUpdateRequestSchema } from "@/features/boards/schemas";
import type { BoardDeleteRequest, BoardListWithChildrenResponse } from "@/features/boards/schemas";
import { BoardDeleteDialog } from "./board-delete-dialog";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Form } from "@/components/ui/form";
import { TextareaField, TextField } from "@/components/ui/form-fields";
import { FormGrid } from "@oba/shared/features/form";
import { DangerZone } from "@/components/shared/danger-zone";
import { useBoardRole } from "@/features/boards/hooks/use-board-role";
import { usePathname, useRouter } from "@oba/shared/libs/i18n/navigation";
import { ColorPicker } from "@/components/shared/color-picker";
import { BOARD_COLOR_OPTIONS, DEFAULT_BOARD_COLOR } from "../lib/board-colors";
import { boardPath } from "../lib/board-route";

interface BoardSettingsDialogProps {
  boardId: string;
  board: BoardUpdateRequest & { id: string; slug: string };
  canManage: boolean;
  children: React.ReactNode;
}

export function BoardSettingsDialog({ boardId, board, canManage, children }: BoardSettingsDialogProps) {
  const t = useTranslations();
  const router = useRouter();
  const pathname = usePathname();
  const params = useParams<{ boardSlug?: string }>();
  const { isOwner } = useBoardRole(boardId);
  const form = useForm<BoardUpdateRequest>({
    resolver: zodResolver(boardUpdateRequestSchema),
    reValidateMode: "onChange",
    mode: "all",
    defaultValues: {
      name: board.name,
      prefix: board.prefix,
      description: board.description ?? "",
      color: board.color ?? DEFAULT_BOARD_COLOR,
    },
  });

  const [open, setOpen] = useState(false);
  const updateBoardMutation = useUpdateBoard();
  const { data: childBoards } = useCachedChildBoards(boardId);

  // Collect all descendant board IDs so the delete mutation can clean up their cached queries
  const childBoardIds = useMemo(() => {
    function collectIds(boards: BoardListWithChildrenResponse[]): string[] {
      const ids: string[] = [];
      for (const b of boards) {
        ids.push(b.id);
        if (b.childBoards?.length) {
          ids.push(...collectIds(b.childBoards as BoardListWithChildrenResponse[]));
        }
      }
      return ids;
    }
    return collectIds(childBoards ?? []);
  }, [childBoards]);

  const deleteBoardMutation = useDeleteBoard({
    childBoardIds,
    onBeforeInvalidate: () => {
      setOpen(false);
      router.push("/home");
    },
  });

  /**
   * A rename can change the board's slug, and the old slug no longer resolves.
   * When this board is the one open, its page is replaced under the new slug,
   * keeping the tab or sprint and the query, so the user stays where they were.
   */
  const followSlugChange = (newSlug: string) => {
    const oldPrefix = boardPath(board.slug);
    const rest = pathname.startsWith(oldPrefix) ? pathname.slice(oldPrefix.length) : "";
    router.replace(`${boardPath(newSlug)}${rest}${window.location.search}`);
  };

  const handleSave = async (data: BoardUpdateRequest) => {
    updateBoardMutation.mutate(
      {
        id: boardId,
        data: {
          ...data,
        },
      },
      {
        onSuccess: (response) => {
          form.reset({ ...data });
          if (params.boardSlug !== board.slug) {
            router.push("/home");
            return;
          }
          const newSlug = response.data?.slug;
          if (newSlug && newSlug !== board.slug) followSlugChange(newSlug);
        },
      }
    );
  };

  const handleDelete = (request: BoardDeleteRequest) => {
    deleteBoardMutation.mutate({ id: boardId, params: request });
  };

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>{children}</DialogTrigger>
      <DialogContent className="max-h-[90vh] max-w-2xl overflow-y-auto">
        <DialogHeader>
          {/* The right padding keeps a long board name and its badge clear
            of the close button. */}
          <DialogTitle className="flex items-center gap-2 pr-8">
            <BoardDot color={board.color} />
            <span>{board.name}</span>
            <Badge variant="secondary" className="text-xs font-normal">
              {board.prefix}
            </Badge>
          </DialogTitle>
          <DialogDescription>{t("board.settings.description")}</DialogDescription>
        </DialogHeader>

        {/* The tabs share the row in equal cells while their labels fit; a
          longer label takes the width it needs, and on a phone the row
          scrolls. `h-auto` leaves room for a scrollbar that takes space, and
          `min-w-0` keeps the row from widening the dialog's grid column. */}
        <Tabs defaultValue="general" className="w-full min-w-0">
          <TabsList className="flex h-auto w-full justify-start overflow-x-auto">
            <TabsTrigger value="general" className="flex-1 gap-2">
              <Settings className="h-4 w-4 shrink-0" />
              {t("board.settings.tabs.general")}
            </TabsTrigger>
            <TabsTrigger value="members" className="flex-1 gap-2">
              <Users className="h-4 w-4 shrink-0" />
              {t("board.settings.tabs.members")}
            </TabsTrigger>
            <TabsTrigger value="labels" className="flex-1 gap-2">
              <Tags className="h-4 w-4 shrink-0" />
              {t("board.settings.tabs.labels")}
            </TabsTrigger>
            <TabsTrigger value="projects" className="flex-1 gap-2">
              <FolderKanban className="h-4 w-4 shrink-0" />
              {t("board.settings.tabs.projects")}
            </TabsTrigger>
            <TabsTrigger value="childboards" className="flex-1 gap-2">
              <Workflow className="h-4 w-4 shrink-0" />
              {t("board.settings.tabs.childBoards")}
            </TabsTrigger>
          </TabsList>

          <TabsContent value="general">
            <Form {...form}>
              <FormGrid className="py-4 md:grid-cols-1">
                <TextField
                  name="name"
                  label={t("board.settings.boardName")}
                  control={form.control}
                  required
                  placeholder={t("board.settings.boardNamePlaceholder")}
                  className="w-full"
                  disabled={!canManage || updateBoardMutation.isPending}
                />

                <TextField
                  name="prefix"
                  label={t("board.prefix")}
                  placeholder={t("board.prefixPlaceholder")}
                  description={t("board.prefixHelper")}
                  control={form.control}
                  required
                  maxLength={5}
                  disabled={!canManage || updateBoardMutation.isPending}
                  onChange={(e) => {
                    form.setValue("prefix", e.target.value.toUpperCase(), {
                      shouldValidate: true,
                      shouldDirty: true,
                    });
                  }}
                />
                <TextareaField
                  name="description"
                  label={t("board.description")}
                  placeholder={t("board.descriptionPlaceholder")}
                  control={form.control}
                  disabled={!canManage || updateBoardMutation.isPending}
                  rows={3}
                />
                <ColorPicker
                  label={t("board.color")}
                  options={BOARD_COLOR_OPTIONS}
                  value={form.watch("color")}
                  onChange={(color) =>
                    form.setValue("color", color, { shouldValidate: true, shouldDirty: true })
                  }
                  disabled={!canManage || updateBoardMutation.isPending}
                />
              </FormGrid>
            </Form>

            {/* Danger Zone — only visible to Owner */}
            {isOwner && (
              <DangerZone
                title={t("board.settings.dangerZone")}
                description={t("board.settings.deleteWarning")}
                onDelete={() => {}}
                disabled={deleteBoardMutation.isPending}
                buttonText={
                  deleteBoardMutation.isPending
                    ? t("board.settings.deleting")
                    : t("board.settings.deleteButton")
                }
                customDialog={({ open: deleteOpen, onOpenChange }) => (
                  <BoardDeleteDialog
                    open={deleteOpen}
                    onOpenChange={onOpenChange}
                    boardId={boardId}
                    boardName={board.name}
                    boardPrefix={board.prefix}
                    boardColor={board.color}
                    childBoards={childBoards ?? []}
                    onConfirm={handleDelete}
                    isPending={deleteBoardMutation.isPending}
                  />
                )}
              />
            )}

            <DialogFooter>
              <Button
                variant="ghost"
                onClick={() => setOpen(false)}
                disabled={updateBoardMutation.isPending}
              >
                {t("common.cancel")}
              </Button>
              {canManage && (
                <Button
                  onClick={() => form.handleSubmit(handleSave)()}
                  disabled={
                    updateBoardMutation.isPending ||
                    form.formState.isSubmitting ||
                    !form.formState.isValid ||
                    !form.formState.isDirty
                  }
                >
                  {updateBoardMutation.isPending
                    ? t("common.saving")
                    : t("board.settings.saveChanges")}
                </Button>
              )}
            </DialogFooter>
          </TabsContent>

          <TabsContent value="members" className="py-4">
            <BoardMembersList boardId={boardId} canManage={canManage} />
          </TabsContent>

          <TabsContent value="labels" className="py-4">
            <BoardSettingsLabels boardId={boardId} canManage={canManage} />
          </TabsContent>

          <TabsContent value="projects" className="py-4">
            <BoardSettingsProjects boardId={boardId} canManage={canManage} />
          </TabsContent>

          <TabsContent value="childboards" className="py-4">
            <BoardHierarchy boardId={boardId} showAlways canManage={canManage} />
          </TabsContent>
        </Tabs>
      </DialogContent>
    </Dialog>
  );
}
