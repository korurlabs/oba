"use client";

import { useState, type FormEvent, type ReactNode } from "react";

import { Button } from "@oba/ui/sky/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@oba/ui/sky/dialog";
import { Field, Input, NativeSelect, Textarea } from "@oba/ui/sky/field";
import { useRepositories } from "@/features/sessions/hooks/use-sessions";
import { describeError } from "@/lib/api/error-text";

import { useCreateLesson, useUpdateLesson } from "../hooks/use-lessons";
import {
  LESSON_LIMITS,
  lessonRequestSchema,
  type LessonDetailResponse,
  type LessonRequest,
} from "../schemas";
import { ORGANIZATION_WIDE_LABEL, repositoryChoices } from "./lesson-data";

type LessonFormProps = {
  lesson: LessonDetailResponse | undefined;
  pending: boolean;
  /** Saves the lesson; a refusal comes back through `onRefused` while the form stays open. */
  onSave: (data: LessonRequest, onRefused: (failure: unknown) => void) => void;
  onCancel: () => void;
};

/** Mounted only while the dialog is open, so each opening starts from the lesson as it is now. */
function LessonForm({ lesson, pending, onSave, onCancel }: LessonFormProps) {
  const repositories = useRepositories();
  const [title, setTitle] = useState(lesson?.title ?? "");
  const [body, setBody] = useState(lesson?.body ?? "");
  const [repositoryId, setRepositoryId] = useState(lesson?.repository?.id ?? "");
  const [error, setError] = useState<string | null>(null);

  const submit = (event: FormEvent) => {
    event.preventDefault();
    const parsed = lessonRequestSchema.safeParse({
      title,
      body,
      repositoryId: repositoryId === "" ? null : repositoryId,
    });
    if (!parsed.success) {
      setError(parsed.error.issues[0]?.message ?? "Check the values entered.");
      return;
    }
    setError(null);
    onSave(parsed.data, (failure: unknown) => setError(describeError(failure)));
  };

  return (
    <form onSubmit={submit} className="flex flex-col gap-4">
      <DialogHeader>
        <DialogTitle>{lesson ? "Edit lesson" : "New lesson"}</DialogTitle>
        <DialogDescription className="sr-only">
          The title, the text and the repository of the lesson.
        </DialogDescription>
      </DialogHeader>
      <Field label="Title">
        <Input
          value={title}
          maxLength={LESSON_LIMITS.titleMax}
          autoFocus
          onChange={(event) => setTitle(event.target.value)}
        />
      </Field>
      <Field label="Text">
        <Textarea
          value={body}
          maxLength={LESSON_LIMITS.bodyMax}
          rows={10}
          placeholder="Markdown"
          onChange={(event) => setBody(event.target.value)}
        />
      </Field>
      <Field label="Repository">
        <NativeSelect
          value={repositoryId}
          onChange={(event) => setRepositoryId(event.target.value)}
        >
          <option value="">{ORGANIZATION_WIDE_LABEL}</option>
          {repositoryChoices(repositories.data?.data ?? [], lesson?.repository).map((choice) => (
            <option key={choice.id} value={choice.id}>
              {choice.name}
            </option>
          ))}
        </NativeSelect>
      </Field>
      {repositories.error !== null && (
        <p className="text-warning-text text-sm" role="status">
          The repositories could not be loaded: {describeError(repositories.error)}
        </p>
      )}
      {error !== null && (
        <p className="text-error text-sm" role="alert">
          {error}
        </p>
      )}
      <DialogFooter>
        <Button type="button" variant="outline" onClick={onCancel} disabled={pending}>
          Cancel
        </Button>
        <Button type="submit" disabled={pending}>
          {lesson ? "Save" : "Create"}
        </Button>
      </DialogFooter>
    </form>
  );
}

export type LessonFormDialogProps = {
  /** The lesson to edit; without one the dialog creates a new lesson. */
  lesson?: LessonDetailResponse;
  trigger: ReactNode;
};

/**
 * Writes a new lesson or edits one in place. The server decides who may save
 * and refuses an archived repository; the dialog stays open and shows the
 * refusal, which is why it cannot close while a save runs.
 */
export function LessonFormDialog({ lesson, trigger }: LessonFormDialogProps) {
  const create = useCreateLesson();
  const update = useUpdateLesson();
  const [open, setOpen] = useState(false);
  const pending = create.isPending || update.isPending;

  const save = (data: LessonRequest, onRefused: (failure: unknown) => void) => {
    const callbacks = { onSuccess: () => setOpen(false), onError: onRefused };
    if (lesson) update.mutate({ lessonId: lesson.id, data }, callbacks);
    else create.mutate(data, callbacks);
  };

  return (
    <Dialog
      open={open}
      onOpenChange={(next: boolean) => {
        if (next || !pending) setOpen(next);
      }}
    >
      <DialogTrigger asChild>{trigger}</DialogTrigger>
      <DialogContent>
        <LessonForm
          lesson={lesson}
          pending={pending}
          onSave={save}
          onCancel={() => setOpen(false)}
        />
      </DialogContent>
    </Dialog>
  );
}
