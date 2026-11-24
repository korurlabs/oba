"use client";

import {
  useCallback,
  useId,
  useMemo,
  useState,
  type KeyboardEvent,
  type ReactNode,
} from "react";
import { useTranslations } from "next-intl";
import { useFieldArray } from "react-hook-form";
import { toast } from "sonner";
import { AlertCircle, ArrowLeft, Loader2, Lock } from "lucide-react";
import { useGetMe } from "@oba/shared/features/users/hooks/use-get-me";
import { Link, useRouter } from "@oba/shared/libs/i18n/navigation";
import { Button } from "@/components/ui/button";
import { Form } from "@/components/ui/form";
import { TextareaField } from "@/components/ui/form-fields";
import { InlineAlert } from "@/components/ui/inline-alert";
import { DAILY_UPDATES_PATH } from "@/features/chat/lib/chat-route";
import { TicketStatus, type TicketListResponse } from "@/features/tickets/schemas";
import {
  lineFromTicket,
  lineFromUpdateItem,
  toUpdateRequest,
  useUpdateForm,
  useUpdatePreviewRequest,
} from "../hooks/use-update-form";
import {
  useCreateUpdate,
  useEditUpdate,
  usePreviewUpdate,
  usePreviewUpdateEdit,
  useUpdate,
} from "../hooks/use-updates";
import { useUpdateSeedTickets, type UnresolvedSeedTicket } from "../hooks/use-update-seed-tickets";
import { useTicketPicker } from "../hooks/use-ticket-picker";
import { copyPreviousUpdate } from "../lib/copy-previous-update";
import {
  UPDATE_TICKETS_MAX,
  type UpdateFormValues,
  type UpdateResponse,
  type UpdateTicketResponse,
} from "../schemas";
import { PreviousUpdatePanel } from "./previous-update-panel";
import { SelectedTicketsCard } from "./selected-tickets-card";
import { TicketPicker } from "./ticket-picker";
import { UpdateMessageCard } from "./update-message-card";
import { UpdatePreview } from "./update-preview";

/**
 * Where "Back to updates" leads: the daily updates channel, where the update
 * appears as a card. An organization without one is sent on to `/chats` by
 * that page.
 */
const UPDATES_FEED_PATH = DAILY_UPDATES_PATH;

/** Saved picker filters of the update screens live under this key, beside the board IDs. */
const UPDATE_PICKER_PREFERENCES_KEY = "updates";

/** A plain create starts with no ticket: the reader ticks every line. */
const EMPTY_CREATE_VALUES: UpdateFormValues = { tickets: [], focus: "", questions: "" };

export type CreateUpdateViewProps =
  | {
      mode?: "create";
      /** Keys of the tickets to start with; without them the card starts empty. */
      ticketKeys?: string[] | null;
    }
  | { mode: "edit"; updateId: string };

/**
 * The screen to write a daily update, or to edit one's own. Create starts
 * with an empty card, or with exactly the tickets picked on a board when it
 * is opened from a ticket selection; edit starts with the stored lines,
 * percentages, notes, focus and questions. Only the author can edit: anyone
 * else gets a notice and no editable field.
 */
export function CreateUpdateView(props: CreateUpdateViewProps) {
  if (props.mode === "edit") {
    return <EditUpdateLoader updateId={props.updateId} />;
  }
  if (props.ticketKeys) {
    return <CreateFromTicketsLoader ticketKeys={props.ticketKeys} />;
  }
  return <UpdateComposer mode="create" defaultValues={EMPTY_CREATE_VALUES} />;
}

// ── Seeding ───────────────────────────────────────────────────────────

/** A problem with the starting tickets, shown above the card. */
type SeedWarning = { title: string; detail?: string };

/**
 * Waits until every chosen ticket has resolved or failed, then mounts the
 * form with the resolved ones: the update starts with exactly the
 * selection. Keys beyond the line limit are not requested.
 */
function CreateFromTicketsLoader({ ticketKeys }: { ticketKeys: string[] }) {
  const requestedKeys = ticketKeys.slice(0, UPDATE_TICKETS_MAX);
  const seed = useUpdateSeedTickets(requestedKeys);

  if (seed.isLoading) {
    return <ScreenState mode="create" kind="loading" />;
  }

  return (
    <CreateFromTicketsForm
      tickets={seed.tickets}
      unresolved={seed.unresolved}
      leftOutKeys={ticketKeys.slice(UPDATE_TICKETS_MAX)}
    />
  );
}

function CreateFromTicketsForm({
  tickets,
  unresolved,
  leftOutKeys,
}: {
  tickets: TicketListResponse[];
  unresolved: UnresolvedSeedTicket[];
  leftOutKeys: string[];
}) {
  const t = useTranslations("updates.form.fromSelection");

  // Seeded once: a later refetch of a ticket never overwrites the card.
  const [seed] = useState(() => {
    // Two spellings of one key resolve to the same ticket; the server
    // refuses an update that lists a ticket twice.
    const seen = new Set<string>();
    const unique = tickets.filter((ticket) => {
      if (seen.has(ticket.id)) return false;
      seen.add(ticket.id);
      return true;
    });

    const warnings: SeedWarning[] = [];
    if (unresolved.length > 0) {
      warnings.push({
        title: t("unresolved", { count: unresolved.length }),
        detail: unresolved
          .map(({ ticketKey, message }) => (message ? `${ticketKey} (${message})` : ticketKey))
          .join(", "),
      });
    }
    if (leftOutKeys.length > 0) {
      warnings.push({
        title: t("overLimit", { count: leftOutKeys.length, max: UPDATE_TICKETS_MAX }),
        detail: leftOutKeys.join(", "),
      });
    }

    return {
      defaultValues: {
        tickets: unique.map(lineFromTicket),
        focus: "",
        questions: "",
      } satisfies UpdateFormValues,
      warnings,
    };
  });

  return (
    <UpdateComposer
      mode="create"
      defaultValues={seed.defaultValues}
      seedWarnings={seed.warnings}
    />
  );
}

function EditUpdateLoader({ updateId }: { updateId: string }) {
  const t = useTranslations("updates.form");
  const updateQuery = useUpdate(updateId);
  const meQuery = useGetMe();
  const me = meQuery.data?.data;
  const update = updateQuery.data?.data;

  if (updateQuery.isError) {
    return (
      <ScreenState
        mode="edit"
        kind="error"
        title={t("loadError")}
        message={updateQuery.error.message}
      />
    );
  }
  if (meQuery.isError) {
    return (
      <ScreenState mode="edit" kind="error" title={t("meError")} message={meQuery.error.message} />
    );
  }
  if (!update || !me) {
    return <ScreenState mode="edit" kind="loading" />;
  }
  if (update.author.userId !== me.id) {
    return (
      <ScreenState
        mode="edit"
        kind="notAuthor"
        title={t("notAuthor")}
        message={t("notAuthorDescription")}
      />
    );
  }

  return <EditUpdateForm update={update} />;
}

function EditUpdateForm({ update }: { update: UpdateResponse }) {
  // Seeded once: the form ignores later changes of these starting values.
  const [seed] = useState(() => {
    const editable = update.items.filter(
      (item): item is UpdateTicketResponse & { ticketId: string } => item.ticketId !== null,
    );
    return {
      defaultValues: {
        tickets: editable.map(lineFromUpdateItem),
        focus: update.focus ?? "",
        questions: update.questions ?? "",
      } satisfies UpdateFormValues,
      keptLines: update.items.filter((item) => item.ticketId === null),
    };
  });

  return (
    <UpdateComposer
      mode="edit"
      updateId={update.id}
      defaultValues={seed.defaultValues}
      keptLines={seed.keptLines}
    />
  );
}

// ── Form ──────────────────────────────────────────────────────────────

type UpdateComposerProps = {
  defaultValues: UpdateFormValues;
  /** Shown above the card when starting tickets could not be loaded or were left out. */
  seedWarnings?: SeedWarning[];
} & (
  | { mode: "create" }
  | { mode: "edit"; updateId: string; keptLines: UpdateTicketResponse[] }
);

function UpdateComposer(props: UpdateComposerProps) {
  const { mode, defaultValues, seedWarnings } = props;
  const updateId = props.mode === "edit" ? props.updateId : undefined;
  const keptLines = props.mode === "edit" ? props.keptLines : [];

  const t = useTranslations("updates");
  const router = useRouter();
  const { data: meResponse } = useGetMe();
  const [created, setCreated] = useState<UpdateResponse | null>(null);
  const formId = useId();

  const form = useUpdateForm(defaultValues);
  const { fields, append, remove } = useFieldArray({
    control: form.control,
    name: "tickets",
    keyName: "fieldKey",
  });

  // ── Preview ──────────────────────────────────────────────────────
  const { request, isValid } = useUpdatePreviewRequest(form.control);
  const createPreview = usePreviewUpdate(mode === "create" ? request : null);
  const editPreview = usePreviewUpdateEdit(updateId, mode === "edit" ? request : null);
  const preview = mode === "create" ? createPreview : editPreview;

  const blockedReason = fields.length === 0 ? "noTickets" : !isValid ? "invalid" : null;

  const progressSyncIds = useMemo(() => {
    const ids = new Set<string>();
    if (blockedReason) return ids;
    for (const item of preview.data?.data?.items ?? []) {
      if (item.progressSynced && item.ticketId) ids.add(item.ticketId);
    }
    return ids;
  }, [blockedReason, preview.data?.data?.items]);

  // ── Selection ────────────────────────────────────────────────────
  const selectedIds = useMemo(() => fields.map((field) => field.ticketId), [fields]);

  const handleSelectionChange = useCallback(
    (added: TicketListResponse[], removedIds: string[]) => {
      if (removedIds.length > 0) {
        const removed = new Set(removedIds);
        const indices = form
          .getValues("tickets")
          .flatMap((line, index) => (removed.has(line.ticketId) ? [index] : []));
        if (indices.length > 0) remove(indices);
      }
      if (added.length > 0) {
        append(added.map(lineFromTicket), { shouldFocus: false });
      }
    },
    [form, append, remove],
  );

  // ── Ticket list ──────────────────────────────────────────────────
  // Owned here, not by the picker, so the card's in-progress action reads
  // the same list, under the same filters, as the table.
  const picker = useTicketPicker(UPDATE_PICKER_PREFERENCES_KEY);

  // The reader's in-progress tickets among every row the list holds under the
  // current filters and search, scrolled into view or not, that are not on
  // the card yet. Empty while the list loads or after it failed.
  const addableInProgress = useMemo(() => {
    const meUserId = picker.meUserId;
    if (picker.isLoading || picker.isError || !meUserId) return [];
    const selected = new Set(selectedIds);
    return picker.tickets.filter(
      (ticket) =>
        ticket.status === TicketStatus.IN_PROGRESS &&
        (ticket.assignees ?? []).some((assignee) => assignee.userId === meUserId) &&
        !selected.has(ticket.id),
    );
  }, [picker.isLoading, picker.isError, picker.meUserId, picker.tickets, selectedIds]);

  const handleAddMyInProgress = useCallback(() => {
    handleSelectionChange(addableInProgress as TicketListResponse[], []);
  }, [addableInProgress, handleSelectionChange]);

  // ── Copy from the previous update ────────────────────────────────
  // Replaced on every copy, so the alert names what the latest copy left out.
  const [copyWarnings, setCopyWarnings] = useState<SeedWarning[]>([]);

  const handleCopyPrevious = useCallback(
    (previous: UpdateResponse, ticketsByKey: ReadonlyMap<string, TicketListResponse>) => {
      const { added, focus, leftOut } = copyPreviousUpdate({
        previous,
        current: form.getValues(),
        ticketsByKey,
      });
      if (added.length > 0) append(added, { shouldFocus: false });
      // Validated at once, so an error raised on the empty focus clears.
      if (focus !== null) form.setValue("focus", focus, { shouldDirty: true, shouldValidate: true });

      const warnings: SeedWarning[] = [];
      if (leftOut.unavailable.length > 0) {
        warnings.push({
          title: t("form.previous.leftOutUnavailable", { count: leftOut.unavailable.length }),
          detail: leftOut.unavailable.join(", "),
        });
      }
      if (leftOut.done.length > 0) {
        warnings.push({
          title: t("form.previous.leftOutDone", { count: leftOut.done.length }),
          detail: leftOut.done.join(", "),
        });
      }
      if (leftOut.overLimit.length > 0) {
        warnings.push({
          title: t("form.previous.leftOutOverLimit", {
            count: leftOut.overLimit.length,
            max: UPDATE_TICKETS_MAX,
          }),
          detail: leftOut.overLimit.join(", "),
        });
      }
      setCopyWarnings(warnings);

      if (added.length > 0 || focus !== null) {
        toast.success(t("form.previous.copied", { count: added.length }));
      } else {
        toast.info(t("form.previous.nothingToCopy"));
      }
    },
    [form, append, t],
  );

  // ── Submit ───────────────────────────────────────────────────────
  const createUpdate = useCreateUpdate();
  const editUpdate = useEditUpdate();
  const isPending = createUpdate.isPending || editUpdate.isPending;

  // Errors are toasted by the mutation hooks; the form stays as it is so
  // the values can be corrected and sent again.
  const handleSubmit = form.handleSubmit((values) => {
    const data = toUpdateRequest(values);
    if (mode === "create") {
      createUpdate.mutate(data, {
        // The feed shows the new update too, should the answer carry none.
        onSuccess: (response) => {
          if (response.data) setCreated(response.data);
          else router.push(UPDATES_FEED_PATH);
        },
      });
      return;
    }
    if (updateId) {
      editUpdate.mutate(
        { updateId, data },
        { onSuccess: () => router.push(UPDATES_FEED_PATH) },
      );
    }
  });

  const submitDisabled =
    isPending ||
    fields.length === 0 ||
    fields.length > UPDATE_TICKETS_MAX ||
    (mode === "edit" && !form.formState.isDirty);

  if (created) {
    return <CreatedUpdate update={created} currentUserId={meResponse?.data?.id} />;
  }

  // The ticket picker stays outside the form and the header button submits it
  // by id, so only Confirm or Save posts the update: Enter in the ticket
  // search must not publish it, nor write the card's percentages to tickets.
  return (
    <Form {...form}>
      <div className="flex h-full flex-col">
        <ScreenHeader mode={mode}>
          <Button type="submit" form={formId} size="sm" disabled={submitDisabled}>
            {isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
            {mode === "create" ? t("form.confirm") : t("form.save")}
          </Button>
        </ScreenHeader>

        <div className="flex-1 overflow-auto">
          <div className="grid gap-4 p-4 lg:p-6 xl:grid-cols-[minmax(0,1fr)_minmax(22rem,28rem)]">
            <div className="flex min-w-0 flex-col gap-4">
              {mode === "create" && <PreviousUpdatePanel onCopy={handleCopyPrevious} />}
              <TicketPicker
                picker={picker}
                selectedIds={selectedIds}
                onSelectionChange={handleSelectionChange}
                title={t("form.pickerTitle")}
                description={t("form.pickerDescription")}
                saveFiltersTooltip={t("form.saveFiltersTooltip")}
              />
            </div>

            <form
              id={formId}
              onSubmit={handleSubmit}
              onKeyDown={preventImplicitSubmit}
              className="flex min-w-0 flex-col gap-4"
              noValidate
            >
              {[...(seedWarnings ?? []), ...copyWarnings].map((warning) => (
                <InlineAlert
                  key={warning.title}
                  variant="warning"
                  icon={<AlertCircle />}
                  description={
                    <>
                      <span className="text-foreground font-medium">{warning.title}</span>
                      {warning.detail && <> {warning.detail}</>}
                    </>
                  }
                />
              ))}

              <SelectedTicketsCard
                fields={fields}
                onRemove={remove}
                progressSyncIds={progressSyncIds}
                keptLines={keptLines}
                addableInProgressCount={addableInProgress.length}
                onAddMyInProgress={handleAddMyInProgress}
              />

              <section className="border-border bg-card space-y-4 rounded-lg border px-4 py-3">
                <TextareaField<UpdateFormValues>
                  name="focus"
                  label={t("sections.focus")}
                  placeholder={t("form.focusPlaceholder")}
                  control={form.control}
                  rows={3}
                  required
                />
                <TextareaField<UpdateFormValues>
                  name="questions"
                  label={t("sections.questions")}
                  placeholder={t("form.questionsPlaceholder")}
                  control={form.control}
                  rows={3}
                />
              </section>

              <UpdatePreview
                preview={preview.data?.data}
                blockedReason={blockedReason}
                isFetching={preview.isFetching}
                error={preview.error}
                onRetry={() => void preview.refetch()}
              />
            </form>
          </div>
        </div>
      </div>
    </Form>
  );
}

/**
 * Enter in a percentage or note input would submit the form implicitly and
 * post the update. Textareas keep Enter for a new line, and buttons keep it
 * as a click.
 */
function preventImplicitSubmit(event: KeyboardEvent<HTMLFormElement>) {
  if (event.key === "Enter" && event.target instanceof HTMLInputElement) {
    event.preventDefault();
  }
}

// ── After create ──────────────────────────────────────────────────────

function CreatedUpdate({
  update,
  currentUserId,
}: {
  update: UpdateResponse;
  currentUserId: string | undefined;
}) {
  const t = useTranslations("updates.form");
  return (
    <div className="flex h-full flex-col">
      <ScreenHeader mode="create" />
      <div className="flex-1 overflow-auto">
        <div className="mx-auto w-full max-w-3xl space-y-4 px-4 py-6 lg:px-6">
          <div>
            <h2 className="text-foreground text-base font-semibold">{t("posted")}</h2>
            <p className="text-muted-foreground text-sm">{t("postedDescription")}</p>
          </div>
          <UpdateMessageCard update={update} currentUserId={currentUserId} />
          <Button variant="outline" asChild>
            <Link href={UPDATES_FEED_PATH}>
              <ArrowLeft className="mr-2 h-4 w-4" />
              {t("backToUpdates")}
            </Link>
          </Button>
        </div>
      </div>
    </div>
  );
}

// ── Chrome and states ─────────────────────────────────────────────────

function ScreenHeader({ mode, children }: { mode: "create" | "edit"; children?: ReactNode }) {
  const t = useTranslations("updates.form");
  return (
    <div className="border-border bg-card flex items-center gap-2 border-b px-4 py-2 lg:px-6">
      <Button variant="ghost" size="sm" className="-ml-2 gap-1.5" asChild>
        <Link href={UPDATES_FEED_PATH} aria-label={t("backToUpdates")}>
          <ArrowLeft className="h-4 w-4" />
          <span className="hidden sm:inline">{t("backToUpdates")}</span>
        </Link>
      </Button>
      <h1 className="text-foreground truncate text-sm font-semibold">
        {mode === "create" ? t("createTitle") : t("editTitle")}
      </h1>
      <div className="ml-auto flex items-center gap-2">{children}</div>
    </div>
  );
}

type ScreenStateProps =
  | { mode: "create" | "edit"; kind: "loading" }
  | { mode: "create" | "edit"; kind: "error" | "notAuthor"; title: string; message?: string };

function ScreenState(props: ScreenStateProps) {
  const t = useTranslations("updates");
  return (
    <div className="flex h-full flex-col">
      <ScreenHeader mode={props.mode} />
      {props.kind === "loading" ? (
        <div className="flex flex-1 flex-col items-center justify-center">
          <Loader2 className="text-muted-foreground h-8 w-8 animate-spin" />
          <p className="text-muted-foreground mt-3 text-sm">{t("form.loading")}</p>
        </div>
      ) : (
        <div className="flex flex-1 flex-col items-center justify-center p-6 text-center">
          {props.kind === "notAuthor" ? (
            <Lock className="text-muted-foreground h-8 w-8" />
          ) : (
            <AlertCircle className="text-destructive h-8 w-8" />
          )}
          <p className="text-foreground mt-3 text-sm font-medium">{props.title}</p>
          {props.message && (
            <p className="text-muted-foreground mt-1 max-w-md text-xs">{props.message}</p>
          )}
          <Button variant="outline" className="mt-4" asChild>
            <Link href={UPDATES_FEED_PATH}>{t("form.backToUpdates")}</Link>
          </Button>
        </div>
      )}
    </div>
  );
}
