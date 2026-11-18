"use client";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  TextField,
  TextareaField,
} from "@/components/ui/form-fields";
import { SelectField as SharedSelectField } from "@oba/shared/features/form/components";
import { FormGrid, ObaForm } from "@oba/shared/features/form";
import { useTranslations } from "next-intl";
import { useFormContext } from "react-hook-form";
import { useBoards, useCreateBoard } from "@/features/boards/hooks/use-boards";
import { createBoardFormSchema, type CreateBoardFormValues } from "@/features/boards/schemas";
import { ColorPicker } from "@/components/shared/color-picker";
import { BOARD_COLOR_OPTIONS, DEFAULT_BOARD_COLOR } from "../lib/board-colors";

interface CreateBoardDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  parentBoardId?: string;
  showParentSelector?: boolean;
}

export function CreateBoardDialog({
  open,
  onOpenChange,
  parentBoardId,
  showParentSelector = false,
}: CreateBoardDialogProps) {
  const t = useTranslations();
  const { data: boardsResponse } = useBoards();
  const boards = boardsResponse?.data;
  const createBoard = useCreateBoard();

  const handleSubmit = (data: CreateBoardFormValues) => {
    const parentId =
      data.parentBoardId === "none" ? undefined : data.parentBoardId || parentBoardId;
    createBoard.mutate(
      {
        name: data.name.trim(),
        description: data.description?.trim() || undefined,
        prefix: data.prefix.toUpperCase(),
        color: data.color,
        parentBoardId: parentId,
      },
      {
        onSuccess: () => onOpenChange(false),
      }
    );
  };

  const handleClose = () => {
    if (!createBoard.isPending) {
      onOpenChange(false);
    }
  };

  const parentOptions = [
    { label: t("board.noParent"), value: "none" },
    ...(boards || []).map((board) => ({
      label: `(${board.prefix}) ${board.name}`,
      value: board.id,
    })),
  ];

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent className="sm:max-w-[500px]">
        <ObaForm
          schema={createBoardFormSchema}
          onSubmit={handleSubmit}
          defaultValues={{
            name: "",
            prefix: "",
            description: "",
            parentBoardId: parentBoardId ?? "none",
            color: DEFAULT_BOARD_COLOR,
          }}
          className="space-y-0"
        >
          <DialogHeader>
            <DialogTitle>
              {parentBoardId ? t("board.createChild") : t("board.createNew")}
            </DialogTitle>
            <DialogDescription>
              {parentBoardId ? t("board.createChildDescription") : t("board.createNewDescription")}
            </DialogDescription>
          </DialogHeader>

          <CreateBoardFormFields
            showParentSelector={showParentSelector}
            parentOptions={parentOptions}
            isPending={createBoard.isPending}
          />

          <DialogFooter>
            <Button
              type="button"
              variant="ghost"
              onClick={handleClose}
              disabled={createBoard.isPending}
            >
              {t("common.cancel")}
            </Button>
            <Button type="submit" disabled={createBoard.isPending}>
              {createBoard.isPending ? t("common.creating") : t("board.create")}
            </Button>
          </DialogFooter>
        </ObaForm>
      </DialogContent>
    </Dialog>
  );
}

function CreateBoardFormFields({
  showParentSelector,
  parentOptions,
  isPending,
}: {
  showParentSelector: boolean;
  parentOptions: { label: string; value: string }[];
  isPending: boolean;
}) {
  const t = useTranslations();
  const { control, watch, setValue } = useFormContext<CreateBoardFormValues>();
  const selectedColor = watch("color");

  return (
    <FormGrid className="py-6 md:grid-cols-1">
      <TextField
        name="name"
        label={t("board.name")}
        placeholder={t("board.namePlaceholder")}
        control={control}
        required
        disabled={isPending}
      />

      <TextField
        name="prefix"
        label={t("board.prefix")}
        placeholder={t("board.prefixPlaceholder")}
        description={t("board.prefixHelper")}
        control={control}
        required
        maxLength={5}
        disabled={isPending}
        onChange={(e) => {
          setValue("prefix", e.target.value.toUpperCase(), { shouldValidate: true, shouldDirty: true });
        }}
      />

      <TextareaField
        name="description"
        label={t("board.description")}
        placeholder={t("board.descriptionPlaceholder")}
        control={control}
        disabled={isPending}
        rows={3}
      />

      {showParentSelector && (
        <SharedSelectField
          name="parentBoardId"
          label={t("board.parent")}
          placeholder={t("board.selectParent")}
          control={control}
          searchPlaceholder={t("board.searchParent")}
          options={parentOptions.map((option) => ({
            label: option.label?.toString() ?? "",
            value: option.value,
          }))}
        />
      )}

      <ColorPicker
        label={t("board.color")}
        options={BOARD_COLOR_OPTIONS}
        value={selectedColor}
        onChange={(color) => setValue("color", color)}
        disabled={isPending}
      />
    </FormGrid>
  );
}
