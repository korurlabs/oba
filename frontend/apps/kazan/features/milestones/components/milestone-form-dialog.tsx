"use client";

import { useMemo } from "react";
import { useLocale, useTranslations } from "next-intl";
import { useFormContext, type DefaultValues } from "react-hook-form";
import { Loader2 } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { DateField, TextareaField, TextField } from "@/components/ui/form-fields";
import { FormGrid, ObaForm } from "@oba/shared/features/form";
import { formatDateOnly, parseDateOnly, toDateOnly } from "@oba/shared/lib/date-only";
import {
  SelectField,
  type SelectOption,
} from "@oba/shared/features/form/components";
import { Button } from "@/components/ui/button";
import { ColorPicker } from "@/components/shared/color-picker";
import { useCachedBoard } from "@/features/boards/hooks/use-boards";
import { BOARD_COLOR_OPTIONS, HEX_COLOR_PATTERN } from "@/features/boards/lib/board-colors";
import {
  milestoneFormSchema,
  type MilestoneDetailResponse,
  type MilestoneFormValues,
  type MilestoneUpdateRequest,
} from "../schemas";
import {
  useAddMilestoneDependency,
  useCreateMilestone,
  useRemoveMilestoneDependency,
  useUpdateMilestone,
} from "../hooks/use-milestones";
import { useMilestoneOptions } from "../hooks/use-milestone-options";

interface MilestoneFormDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Board the new milestone is created on. Ignored in edit mode. */
  boardId: string;
  /** The milestone to edit; create mode when absent. */
  milestone?: MilestoneDetailResponse;
}

/**
 * Create or edit a milestone. Names are not unique, so there is no
 * duplicate-name check: the id is the only identifier.
 */
export function MilestoneFormDialog({
  open,
  onOpenChange,
  boardId,
  milestone,
}: MilestoneFormDialogProps) {
  const t = useTranslations("milestone");
  const tCommon = useTranslations("common");
  const createMilestone = useCreateMilestone();
  const updateMilestone = useUpdateMilestone();
  const addDependency = useAddMilestoneDependency();
  const removeDependency = useRemoveMilestoneDependency();
  const isEdit = !!milestone;
  const isPending =
    createMilestone.isPending ||
    updateMilestone.isPending ||
    addDependency.isPending ||
    removeDependency.isPending;
  // A new milestone starts in its board's colour, as the server would store it.
  const { data: board } = useCachedBoard(boardId);

  const defaultValues: DefaultValues<MilestoneFormValues> = milestone
    ? {
        name: milestone.name,
        description: milestone.description ?? "",
        startDate: milestone.startDate ? parseDateOnly(milestone.startDate) : null,
        // A target that is not a day leaves the field empty, so the required
        // error shows and the user picks the day again.
        targetDate: parseDateOnly(milestone.targetDate) ?? undefined,
        color: milestone.color ?? null,
        dependsOnIds: milestone.dependsOn.map((dependency) => dependency.milestoneId),
      }
    : {
        name: "",
        description: "",
        startDate: null,
        color: board?.color && HEX_COLOR_PATTERN.test(board.color) ? board.color : null,
        dependsOnIds: [],
      };

  // Errors are toasted by the mutation hooks; the dialog stays open then.
  const handleSubmit = async (values: MilestoneFormValues) => {
    const description = values.description?.trim() ?? "";
    const close = { onSuccess: () => onOpenChange(false) };

    if (!milestone) {
      createMilestone.mutate(
        {
          boardId,
          data: {
            name: values.name,
            description: description || undefined,
            startDate: values.startDate ? toDateOnly(values.startDate) : undefined,
            targetDate: toDateOnly(values.targetDate),
            color: values.color ?? undefined,
            dependsOnIds: values.dependsOnIds.length > 0 ? values.dependsOnIds : undefined,
          },
        },
        close,
      );
      return;
    }

    // The update API leaves an omitted field unchanged, so emptying an
    // optional field needs its explicit clear flag.
    const data: MilestoneUpdateRequest = {
      name: values.name,
      targetDate: toDateOnly(values.targetDate),
    };
    if (description) data.description = description;
    else if (milestone.description) data.clearDescription = true;
    if (values.startDate) data.startDate = toDateOnly(values.startDate);
    else if (milestone.startDate) data.clearStartDate = true;
    // The API has no way to clear a colour, and the picker cannot empty it.
    if (values.color && values.color !== milestone.color) data.color = values.color;

    // The update request carries no dependencies, so the picker's changes are
    // applied afterwards as single add and remove calls.
    const current = new Set(milestone.dependsOn.map((dependency) => dependency.milestoneId));
    const selected = new Set(values.dependsOnIds);
    const toAdd = values.dependsOnIds.filter((id) => !current.has(id));
    const toRemove = [...current].filter((id) => !selected.has(id));
    const target = { boardId: milestone.boardId, milestoneId: milestone.id };

    try {
      await updateMilestone.mutateAsync({ ...target, data });
    } catch (error) {
      // The hook has toasted the server message; the dialog stays open so the
      // values can be corrected. Logged for the console, not re-thrown, since
      // the form submit handler has no caller that could act on it.
      console.warn("[MilestoneFormDialog] update failed", error);
      return;
    }

    // Removals first, so a swap does not briefly hold a pair the server could
    // refuse as a cycle. Each failure is toasted by its hook.
    const removals = await Promise.allSettled(
      toRemove.map((dependsOnId) => removeDependency.mutateAsync({ ...target, dependsOnId })),
    );
    const additions = await Promise.allSettled(
      toAdd.map((dependsOnId) => addDependency.mutateAsync({ ...target, dependsOnId })),
    );
    const failed = [...removals, ...additions].filter((result) => result.status === "rejected");
    if (failed.length > 0) {
      // Keep the dialog open: the picker still shows the intended set and the
      // toasts name what the server refused.
      console.warn("[MilestoneFormDialog] dependency changes failed", failed);
      return;
    }
    onOpenChange(false);
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[500px]">
        {/* Keyed so reopening for another milestone starts from its values. */}
        <ObaForm
          key={`${milestone?.id ?? "new"}-${open}`}
          schema={milestoneFormSchema}
          onSubmit={handleSubmit}
          defaultValues={defaultValues}
          className="flex flex-col gap-4"
        >
          <DialogHeader>
            <DialogTitle>{isEdit ? t("form.editTitle") : t("form.createTitle")}</DialogTitle>
            <DialogDescription>{t("form.description")}</DialogDescription>
          </DialogHeader>

          <MilestoneFormFields isPending={isPending} milestone={milestone} boardId={boardId} />

          <DialogFooter>
            <Button
              type="button"
              variant="ghost"
              onClick={() => onOpenChange(false)}
              disabled={isPending}
            >
              {tCommon("cancel")}
            </Button>
            <Button type="submit" disabled={isPending}>
              {isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
              {isEdit ? tCommon("save") : t("form.create")}
            </Button>
          </DialogFooter>
        </ObaForm>
      </DialogContent>
    </Dialog>
  );
}

function MilestoneFormFields({
  isPending,
  milestone,
  boardId,
}: {
  isPending: boolean;
  milestone?: MilestoneDetailResponse;
  boardId: string;
}) {
  const t = useTranslations("milestone");
  const tCommon = useTranslations("common");
  const dependencyOptions = useDependencyOptions(milestone?.boardId ?? boardId, milestone);
  const { control, watch, setValue } = useFormContext<MilestoneFormValues>();
  const startDate = watch("startDate");
  const color = watch("color");

  return (
    <FormGrid className="py-2 md:grid-cols-1">
      <TextField
        name="name"
        label={t("form.name")}
        placeholder={t("form.namePlaceholder")}
        control={control}
        required
        disabled={isPending}
      />

      <TextareaField
        name="description"
        label={t("form.descriptionField")}
        placeholder={t("form.descriptionPlaceholder")}
        control={control}
        rows={3}
        className="resize-none"
        disabled={isPending}
      />

      <div className="grid grid-cols-2 gap-4">
        <DateField
          control={control}
          name="startDate"
          label={t("form.startDate")}
          placeholder={tCommon("selectDate")}
          dateFormat="MMM d, yyyy"
        />
        <DateField
          control={control}
          name="targetDate"
          label={t("form.targetDate")}
          required
          placeholder={tCommon("selectDate")}
          dateFormat="MMM d, yyyy"
          fromDate={startDate ?? undefined}
        />
      </div>

      <SelectField
        isMulti
        name="dependsOnIds"
        label={t("form.dependsOn")}
        control={control}
        options={dependencyOptions.options}
        isLoading={dependencyOptions.isLoading}
        placeholder={t("form.dependsOnPlaceholder")}
        searchPlaceholder={t("dependsOn.search")}
        contentClassName="w-80 min-w-64 max-w-[calc(100vw-2rem)]"
        disabled={isPending}
      />

      <ColorPicker
        label={t("form.color")}
        options={BOARD_COLOR_OPTIONS}
        value={color}
        onChange={(value) => setValue("color", value, { shouldDirty: true })}
        disabled={isPending}
      />
    </FormGrid>
  );
}

/**
 * Candidates for "Depends on": the OPEN milestones of the board and its
 * related boards, labelled with target date and grouped by board because
 * names are not unique. Editing excludes the milestone itself and keeps every
 * current dependency selectable, also one that is CLOSED and so missing from
 * the OPEN list.
 */
function useDependencyOptions(boardId: string, milestone?: MilestoneDetailResponse) {
  const locale = useLocale();
  const { options, isLoading } = useMilestoneOptions(boardId);

  const result = useMemo<SelectOption[]>(() => {
    const candidates = options.filter((option) => option.value !== milestone?.id);
    const listed = new Set(candidates.map((option) => option.value));
    const missing: SelectOption[] = (milestone?.dependsOn ?? [])
      .filter((dependency) => !listed.has(dependency.milestoneId))
      .map((dependency) => ({
        value: dependency.milestoneId,
        label: `${dependency.name} · ${formatDateOnly(dependency.targetDate, locale)}`,
      }));
    return [...missing, ...candidates];
  }, [options, milestone, locale]);

  return { options: result, isLoading };
}
