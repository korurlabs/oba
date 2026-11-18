import { useLocalSearchParams } from "expo-router";
import { Clock, Plus, Ticket, X } from "lucide-react-native";
import * as React from "react";
import { ActivityIndicator, View } from "react-native";

import { BottomSheet, useSheet } from "@/components/kaizen/BottomSheet";
import { EmptyState, InlineError } from "@/components/kaizen/EmptyState";
import { ListRow, ListSection } from "@/components/kaizen/List";
import {
  EMPTY_UPDATE_DRAFT,
  addDraftLines,
  canManageUpdate,
  draftFromUpdate,
  hasUpdateDraftProblems,
  lineSetsTicketProgress,
  ticketsNotOnDraft,
  toUpdateWriteRequest,
  updateDraftProblems,
  type UpdateDraft,
  type UpdateDraftLine,
  type UpdateDraftProblems,
} from "@/components/updates/updateModel";
import { Button } from "@/components/ui/button";
import { Icon } from "@/components/ui/icon";
import { IconButton } from "@/components/ui/icon-button";
import { Input } from "@/components/ui/input";
import { Text } from "@/components/ui/text";
import { TextField } from "@/components/ui/text-field";
import { toast } from "@/components/ui/toast";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import { useChatChannels, useChatMeId } from "@/hooks/queries/chat";
import {
  useCreateUpdate,
  useEditUpdate,
  useSuggestedUpdateTickets,
  useUpdate,
  useUpdateTicketSearch,
} from "@/hooks/queries/updates";
import { useOnlineWriteGuard } from "@/hooks/useOnlineWriteGuard";
import { useTranslation } from "@/i18n";
import { isValidationError } from "@/lib/api";
import { ChatChannelPurpose, type ChatChannelResponse } from "@/lib/schemas/chat";
import {
  UPDATE_DEADLINE_TIME,
  UPDATE_NOTE_MAX,
  UPDATE_TEXT_MAX,
  UPDATE_TICKETS_MAX,
  type UpdateResponse,
  type UpdateTicketOption,
} from "@/lib/schemas/updates";
import { cn } from "@/lib/utils";

/** The request fields the form shows its own errors under; any other goes above the lines. */
const OWN_FIELDS: readonly string[] = ["focus", "questions"];

/**
 * Posts a daily update in the daily updates channel, or with `updateId`
 * edits the author's update. The tickets come from the author's tickets in
 * progress or from a search; each line starts at its ticket's progress and
 * writes its percentage back to the ticket. Post stays off until the server
 * would accept the form; a validation refusal shows under its field.
 */
export default function NewUpdateSheet() {
  const { t } = useTranslation();
  const params = useLocalSearchParams<{ updateId?: string; messageId?: string }>();
  const updateId: string | null = params.updateId || null;
  const messageId: string | null = params.messageId || null;
  const editing: boolean = updateId !== null;
  const meId: string | undefined = useChatMeId();
  const updateQuery = useUpdate(updateId);
  const [draft, setDraft] = React.useState<UpdateDraft>(EMPTY_UPDATE_DRAFT);
  const [seededFrom, setSeededFrom] = React.useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = React.useState<Record<string, string>>({});
  const [done, setDone] = React.useState<boolean>(false);
  const [touched, setTouched] = React.useState<boolean>(false);
  const create = useCreateUpdate();
  const edit = useEditUpdate();
  const online = useOnlineWriteGuard();

  // The edit form starts from the update as it is now, once.
  const update: UpdateResponse | undefined = updateQuery.data;
  if (update && seededFrom !== update.id) {
    setSeededFrom(update.id);
    setDraft(draftFromUpdate(update));
  }
  const notMine: boolean = editing && !!update && !!meId && !canManageUpdate(update, meId);

  const problems: UpdateDraftProblems = updateDraftProblems(draft);
  const blocked: boolean = hasUpdateDraftProblems(problems) || (editing && (!update || notMine));

  const onError = (error: Error): void => {
    // Any other refusal is toasted by the app's error listener.
    if (isValidationError(error)) setFieldErrors(error.fieldErrors);
  };

  const submit = (): void => {
    if (blocked || !online()) return;
    setFieldErrors({});
    const data = toUpdateWriteRequest(draft);
    if (updateId) {
      edit.mutate(
        { updateId, messageId, data },
        {
          onSuccess: () => {
            toast.success(t("updSaved"));
            setDone(true);
          },
          onError,
        }
      );
      return;
    }
    create.mutate(data, {
      onSuccess: () => {
        toast.success(t("updPosted"));
        setDone(true);
      },
      onError,
    });
  };

  const change = (next: UpdateDraft): void => {
    setDraft(next);
    setFieldErrors({});
    setTouched(true);
  };

  const body = (() => {
    if (editing && updateQuery.isPending) {
      return <FormSpinner />;
    }
    if (editing && updateQuery.isError) {
      return (
        <EmptyState
          variant="error"
          title={t("updLoadError")}
          onAction={() => void updateQuery.refetch()}
          actionLabel={t("chatRetry")}
        />
      );
    }
    if (notMine) {
      return <EmptyState title={t("updNotAuthorTitle")} message={t("updNotAuthorBody")} />;
    }
    return (
      <UpdateFields
        draft={draft}
        onChange={change}
        problems={problems}
        fieldErrors={fieldErrors}
        showDeadline={!editing}
      />
    );
  })();

  return (
    <BottomSheet
      title={t(editing ? "updFormEditTitle" : "updFormCreateTitle")}
      cancelLabel={t("chatCancel")}
      confirmLabel={t(editing ? "updFormSave" : "updFormPost")}
      onConfirm={submit}
      confirmDisabled={blocked}
      confirmLoading={create.isPending || edit.isPending}
      dirty={!done && touched}
      scroll
    >
      <DismissWhenDone done={done} />
      {body}
    </BottomSheet>
  );
}

function DismissWhenDone({ done }: { done: boolean }) {
  const { dismiss } = useSheet();
  React.useEffect(() => {
    if (done) dismiss();
  }, [done, dismiss]);
  return null;
}

function FormSpinner() {
  const colors = useThemeColors();
  return <ActivityIndicator className="py-8" color={colors.mutedForeground} />;
}

function UpdateFields({
  draft,
  onChange,
  problems,
  fieldErrors,
  showDeadline,
}: {
  draft: UpdateDraft;
  onChange: (draft: UpdateDraft) => void;
  problems: UpdateDraftProblems;
  fieldErrors: Record<string, string>;
  showDeadline: boolean;
}) {
  const { t } = useTranslation();
  const [picking, setPicking] = React.useState<boolean>(false);
  const full: boolean = draft.lines.length >= UPDATE_TICKETS_MAX;
  // The server's messages for the lines and the body as a whole; focus and questions show under their fields.
  const otherErrors: string[] = Object.entries(fieldErrors)
    .filter(([field]) => !OWN_FIELDS.includes(field))
    .map(([, message]) => message);

  const changeLine = (ticketId: string, patch: Partial<UpdateDraftLine>): void => {
    onChange({
      ...draft,
      lines: draft.lines.map((line) => (line.ticketId === ticketId ? { ...line, ...patch } : line)),
    });
  };

  const removeLine = (ticketId: string): void => {
    onChange({ ...draft, lines: draft.lines.filter((line) => line.ticketId !== ticketId) });
  };

  const addTickets = (options: readonly UpdateTicketOption[]): void => {
    onChange(addDraftLines(draft, options).draft);
  };

  return (
    <View className="gap-6 px-4 pb-8 pt-4">
      <View className="gap-2">
        <View className="min-h-8 flex-row items-center justify-between gap-3">
          <Text variant="subhead" accessibilityRole="header" className="font-sans-semibold">
            {t("updFormWorkedOn")}
          </Text>
          {full ? null : (
            <Button
              variant="ghost"
              size="sm"
              icon={Plus}
              accessibilityState={{ expanded: picking }}
              onPress={() => setPicking((open) => !open)}
              className="-mr-3"
            >
              {t("updFormAddTicket")}
            </Button>
          )}
        </View>

        {picking && !full ? (
          <TicketSearch draft={draft} onAdd={addTickets} onDone={() => setPicking(false)} />
        ) : null}

        {draft.lines.length === 0 ? (
          <NoTicketBox draft={draft} onAdd={addTickets} />
        ) : (
          <>
            {draft.lines.map((line) => (
              <LineFields
                key={line.ticketId}
                line={line}
                problems={problems.lines[line.ticketId]}
                onChange={(patch) => changeLine(line.ticketId, patch)}
                onRemove={() => removeLine(line.ticketId)}
              />
            ))}
            {full ? null : (
              <InProgressButton draft={draft} onAdd={addTickets} className="self-start" />
            )}
          </>
        )}

        {problems.tickets && draft.lines.length > UPDATE_TICKETS_MAX ? (
          <Text variant="footnote" accessibilityRole="alert" className="text-destructive">
            {t(problems.tickets)}
          </Text>
        ) : null}
        {otherErrors.map((message, index) => (
          <Text
            key={index}
            variant="footnote"
            accessibilityRole="alert"
            className="text-destructive"
          >
            {message}
          </Text>
        ))}
        <Text variant="footnote" className="text-muted-foreground">
          {t("updFormTicketsHelper")}
        </Text>
      </View>

      <TextField
        label={t("updFormFocus")}
        value={draft.focus}
        onChangeText={(focus: string) => onChange({ ...draft, focus })}
        placeholder={t("updFormFocusPlaceholder")}
        multiline
        rows={3}
        error={
          fieldErrors.focus ??
          (problems.focus ? t(problems.focus, { max: UPDATE_TEXT_MAX }) : undefined)
        }
      />
      <TextField
        label={t("updFormQuestions")}
        value={draft.questions}
        onChangeText={(questions: string) => onChange({ ...draft, questions })}
        placeholder={t("updFormQuestionsPlaceholder")}
        multiline
        rows={3}
        error={
          fieldErrors.questions ??
          (problems.questions ? t(problems.questions, { max: UPDATE_TEXT_MAX }) : undefined)
        }
      />

      {showDeadline ? <PostingNote /> : null}
    </View>
  );
}

/**
 * The empty "Worked on" box. Nothing is preselected: the author adds the
 * tickets in progress with one tap or picks them with "Add ticket".
 */
function NoTicketBox({
  draft,
  onAdd,
}: {
  draft: UpdateDraft;
  onAdd: (options: readonly UpdateTicketOption[]) => void;
}) {
  const { t } = useTranslation();
  return (
    <View className="items-center gap-1 rounded-lg border border-dashed border-border px-4 py-5">
      <Icon as={Ticket} size={20} className="mb-1 text-foreground" />
      <Text variant="subhead" className="text-center">
        {t("updFormNoTicketTitle")}
      </Text>
      <Text variant="footnote" className="text-center text-muted-foreground">
        {t("updFormNoTicketBody")}
      </Text>
      <View className="pt-2">
        <InProgressButton draft={draft} onAdd={onAdd} />
      </View>
    </View>
  );
}

/**
 * Where the update goes and by when. The channel is the organization's daily
 * updates channel; while the channel list is not loaded the note gives the
 * deadline alone.
 */
function PostingNote() {
  const { t } = useTranslation();
  const channels = useChatChannels();
  const channel: ChatChannelResponse | undefined = channels.data?.find(
    (item) => item.purpose === ChatChannelPurpose.DAILY_UPDATES
  );
  return (
    <View className="flex-row items-start gap-2 rounded-lg bg-muted px-3 py-3">
      <Icon as={Clock} size={16} className="mt-px text-muted-foreground" />
      <Text variant="footnote" className="flex-1 text-foreground">
        {channel
          ? t("updFormPostsTo", { channel: channel.name, time: UPDATE_DEADLINE_TIME })
          : t("updFormPostBy", { time: UPDATE_DEADLINE_TIME })}
      </Text>
    </View>
  );
}

/** One ticket line: its key and title, the percentage, and an optional note. */
function LineFields({
  line,
  problems,
  onChange,
  onRemove,
}: {
  line: UpdateDraftLine;
  problems: UpdateDraftProblems["lines"][string] | undefined;
  onChange: (patch: Partial<UpdateDraftLine>) => void;
  onRemove: () => void;
}) {
  const { t } = useTranslation();
  return (
    <View className="gap-2 rounded-lg border border-border bg-card p-3">
      <View className="flex-row items-start gap-2">
        <View className="flex-1 gap-0.5">
          <Text variant="subhead" numberOfLines={2}>
            <Text variant="subhead" className="font-mono-medium">{`${line.ticketKey} `}</Text>
            {line.ticketTitle}
          </Text>
          {line.boardName ? (
            <Text variant="caption" numberOfLines={1} className="text-muted-foreground">
              {line.boardName}
            </Text>
          ) : null}
        </View>
        <IconButton
          icon={X}
          label={t("updFormRemove", { key: line.ticketKey })}
          onPress={onRemove}
        />
      </View>
      <TextField
        label={t("updFormPercentage")}
        value={line.percentage}
        onChangeText={(percentage: string) => onChange({ percentage })}
        keyboardType="number-pad"
        maxLength={3}
        suffix="%"
        helper={lineSetsTicketProgress(line) ? t("updFormSetsProgress") : undefined}
        error={problems?.percentage ? t(problems.percentage) : undefined}
      />
      <TextField
        label={t("updFormNote")}
        value={line.note}
        onChangeText={(note: string) => onChange({ note })}
        placeholder={t("updFormNotePlaceholder")}
        multiline
        rows={2}
        error={problems?.note ? t(problems.note, { max: UPDATE_NOTE_MAX }) : undefined}
      />
    </View>
  );
}

/**
 * "Add my in-progress tickets (3)": the author's tickets in progress not on
 * the form yet. Hidden when there are none, so nothing invites a tap that
 * adds nothing.
 */
function InProgressButton({
  draft,
  onAdd,
  className,
}: {
  draft: UpdateDraft;
  onAdd: (options: readonly UpdateTicketOption[]) => void;
  className?: string;
}) {
  const { t } = useTranslation();
  const colors = useThemeColors();
  const suggested = useSuggestedUpdateTickets(true);
  if (suggested.isError && !suggested.data) {
    return (
      <InlineError
        message={t("updFormSuggestedError")}
        onRetry={() => void suggested.refetch()}
        className={className}
      />
    );
  }
  if (suggested.isPending) {
    return <ActivityIndicator className={cn("h-8", className)} color={colors.mutedForeground} />;
  }
  const missing: UpdateTicketOption[] = ticketsNotOnDraft(draft, suggested.data ?? []);
  if (missing.length === 0) return null;
  return (
    <Button
      variant="secondary"
      size="sm"
      icon={Plus}
      onPress={() => onAdd(missing)}
      className={className}
    >
      {t("updFormAddInProgress", { count: missing.length })}
    </Button>
  );
}

/**
 * The ticket picker behind "Add ticket": a search by key or title. A tap
 * adds the ticket at its own progress and closes the picker.
 */
function TicketSearch({
  draft,
  onAdd,
  onDone,
}: {
  draft: UpdateDraft;
  onAdd: (options: readonly UpdateTicketOption[]) => void;
  onDone: () => void;
}) {
  const { t } = useTranslation();
  const colors = useThemeColors();
  const [search, setSearch] = React.useState<string>("");
  const searchText: string = search.trim();
  const results = useUpdateTicketSearch(searchText ? searchText : null);
  const options: UpdateTicketOption[] = ticketsNotOnDraft(draft, results.data ?? []);

  return (
    <View>
      <Input
        value={search}
        onChangeText={setSearch}
        placeholder={t("updFormSearchTickets")}
        accessibilityLabel={t("updFormSearchTickets")}
        autoFocus
        autoCapitalize="characters"
        autoCorrect={false}
        returnKeyType="search"
      />
      {searchText ? (
        results.isError && !results.data ? (
          <InlineError
            message={t("prFormTicketsError")}
            onRetry={() => void results.refetch()}
            className="mt-2"
          />
        ) : !results.data ? (
          <ActivityIndicator className="py-3" color={colors.mutedForeground} />
        ) : options.length === 0 ? (
          <Text variant="footnote" className="pt-2 text-muted-foreground">
            {t("prFormTicketsNone")}
          </Text>
        ) : (
          // The results reach the sheet's edges, as an inset grouped list does elsewhere.
          <ListSection className="-mx-4 mt-2">
            {options.map((option) => (
              <ListRow
                key={option.id}
                title={`${option.key} ${option.title}`}
                subtitle={option.board?.name ?? undefined}
                value={`${Math.round(option.percentage)}%`}
                accessory="none"
                onPress={() => {
                  onAdd([option]);
                  setSearch("");
                  onDone();
                }}
              />
            ))}
          </ListSection>
        )
      ) : null}
    </View>
  );
}
