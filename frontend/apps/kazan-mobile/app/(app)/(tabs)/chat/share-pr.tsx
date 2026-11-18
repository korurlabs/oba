import { usePreventRemove } from "@react-navigation/native";
import { Stack, useLocalSearchParams, useNavigation, useRouter } from "expo-router";
import { GitBranch, Plus, Users } from "lucide-react-native";
import * as React from "react";
import { ActivityIndicator, KeyboardAvoidingView, Platform, ScrollView, View } from "react-native";

import { HeaderCancelButton } from "@/components/account/HeaderCancelButton";
import { HeaderSaveButton } from "@/components/account/HeaderSaveButton";
import { ActionSheet, confirmDiscardChanges } from "@/components/kazan/ActionSheet";
import { EmptyState, InlineError } from "@/components/kazan/EmptyState";
import { ListRow, ListSection } from "@/components/kazan/List";
import {
  PullRequestPicker,
  type PickedPullRequest,
} from "@/components/pull-requests/PullRequestPicker";
import { PullRequestStatusPill } from "@/components/pull-requests/PullRequestPills";
import {
  EMPTY_SHARE_DRAFT,
  PULL_REQUEST_STATUSES,
  SHARE_STATUS_STYLE,
  draftFromShare,
  pullRequestErrorKey,
  pullRequestShareProblems,
  reviewerSummaryNames,
  statusFromGithubState,
  toShareCreateRequest,
  toShareUpdateRequest,
  type PullRequestShareDraft,
  type PullRequestShareProblems,
} from "@/components/pull-requests/pullRequestModel";
import {
  publishShareReviewers,
  withdrawShareReviewers,
  type ShareReviewersSession,
} from "@/components/pull-requests/shareReviewersStore";
import { AvatarStack } from "@/components/ui/avatar";
import { Icon } from "@/components/ui/icon";
import { Input } from "@/components/ui/input";
import { Text } from "@/components/ui/text";
import { TextField } from "@/components/ui/text-field";
import { toast } from "@/components/ui/toast";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import { useChatMembers, useChatTicketSuggestions } from "@/hooks/queries/chat";
import {
  pullRequestErrorCode,
  useCreatePullRequestShare,
  useEditPullRequestShare,
  useGithubPullRequest,
  usePullRequestShare,
} from "@/hooks/queries/pullRequests";
import { useTranslation } from "@/i18n";
import { isValidationError } from "@/lib/api";
import type { ChatTicketSuggestion } from "@/lib/schemas/chat";
import {
  PULL_REQUEST_NOTE_MAX,
  PULL_REQUEST_RELATED_TICKETS_MAX,
  PULL_REQUEST_REVIEWERS_MAX,
  type PullRequestShareResponse,
  type PullRequestShareReviewerResponse,
} from "@/lib/schemas/pullRequests";

/** A new share has no stored reviewers. */
const NO_STORED_REVIEWERS: readonly PullRequestShareReviewerResponse[] = [];

/** A related ticket as the form lists it. */
interface TicketChoice {
  id: string;
  key: string;
  title: string;
  boardName: string | null;
}

/** The pull request a share is about, as the preview shows it. */
interface PullRequestFacts {
  title: string;
  reference: string;
  headRef: string;
  baseRef: string;
  ticketKey: string | null;
}

/**
 * Shares a pull request in the pull requests channel, or with `shareId`
 * edits the author's share, as one scrolling form with Cancel and Send in
 * its bar. Sharing starts with the pull requests found on GitHub; the one
 * picked there presets the status from its GitHub state. Status, note,
 * reviewers (through their own picker) and related tickets follow, the
 * same for both. Send stays off until the server would accept the form;
 * a validation refusal shows under its field. Leaving with unsaved input
 * asks first.
 */
export default function SharePullRequestScreen() {
  const { t } = useTranslation();
  const router = useRouter();
  const navigation = useNavigation();
  const params = useLocalSearchParams<{ shareId?: string; messageId?: string }>();
  const shareId: string | null = params.shareId || null;
  const messageId: string | null = params.messageId || null;
  const editing: boolean = shareId !== null;
  const shareQuery = usePullRequestShare(shareId);
  const [picked, setPicked] = React.useState<PickedPullRequest | null>(null);
  const [draft, setDraft] = React.useState<PullRequestShareDraft>(EMPTY_SHARE_DRAFT);
  const [tickets, setTickets] = React.useState<Map<string, TicketChoice>>(new Map());
  const [seededFrom, setSeededFrom] = React.useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = React.useState<Record<string, string>>({});
  const [touched, setTouched] = React.useState<boolean>(false);
  const create = useCreatePullRequestShare();
  const edit = useEditPullRequestShare();

  // The edit form starts from the share as it is now, once.
  const share: PullRequestShareResponse | undefined = shareQuery.data;
  if (share && seededFrom !== share.id) {
    setSeededFrom(share.id);
    setDraft(draftFromShare(share));
    setTickets(
      new Map(
        share.relatedTickets.map((ticket) => [
          ticket.ticketId,
          {
            id: ticket.ticketId,
            key: ticket.ticketKey,
            title: ticket.ticketTitle,
            boardName: ticket.boardName,
          },
        ])
      )
    );
  }

  const problems: PullRequestShareProblems = pullRequestShareProblems(
    draft,
    editing || picked !== null
  );
  const blocked: boolean = Object.keys(problems).length > 0 || (editing && !share);
  const dirty: boolean = picked !== null || touched;
  const leaving = React.useRef<boolean>(false);

  usePreventRemove(dirty, ({ data }) => {
    if (leaving.current) {
      navigation.dispatch(data.action);
      return;
    }
    confirmDiscardChanges(() => {
      leaving.current = true;
      navigation.dispatch(data.action);
    });
  });

  const close = (): void => {
    if (router.canGoBack()) router.back();
  };

  const finish = (message: string): void => {
    leaving.current = true;
    toast.success(message);
    close();
  };

  const onError = (error: Error): void => {
    // Any other refusal is toasted by the app's error listener.
    if (isValidationError(error)) setFieldErrors(error.fieldErrors);
  };

  const submit = (): void => {
    if (blocked) return;
    setFieldErrors({});
    if (shareId) {
      edit.mutate(
        { shareId, data: toShareUpdateRequest(draft), messageId },
        {
          onSuccess: () => finish(t("prUpdated")),
          onError,
        }
      );
      return;
    }
    if (!picked) return;
    create.mutate(toShareCreateRequest(draft, picked), {
      onSuccess: () => finish(t("prShared")),
      onError,
    });
  };

  const change = (next: PullRequestShareDraft): void => {
    setDraft(next);
    setFieldErrors({});
    setTouched(true);
  };

  // The reviewer picker is its own route, so it reads and changes the
  // reviewers through the store; the latest draft is applied when it does.
  const applyReviewers = React.useRef<(reviewerIds: string[]) => void>(() => undefined);
  applyReviewers.current = (reviewerIds: string[]) => change({ ...draft, reviewerIds });
  const reviewerKey: string = draft.reviewerIds.join(",");
  const storedReviewers: readonly PullRequestShareReviewerResponse[] =
    share?.reviewers ?? NO_STORED_REVIEWERS;
  const reviewerSession = React.useRef<ShareReviewersSession | null>(null);
  React.useEffect(() => {
    const next: ShareReviewersSession = {
      reviewerIds: reviewerKey ? reviewerKey.split(",") : [],
      storedReviewers,
      change: (reviewerIds) => applyReviewers.current(reviewerIds),
    };
    reviewerSession.current = next;
    publishShareReviewers(next);
  }, [reviewerKey, storedReviewers]);
  React.useEffect(() => () => withdrawShareReviewers(reviewerSession.current), []);

  const body = (() => {
    if (editing && shareQuery.isPending) {
      return <ShareFormSkeleton />;
    }
    if (editing && shareQuery.isError) {
      const key = pullRequestErrorKey(pullRequestErrorCode(shareQuery.error));
      return (
        <EmptyState
          variant="error"
          title={t(key ?? "prShareLoadError")}
          onAction={key ? undefined : () => void shareQuery.refetch()}
          actionLabel={key ? undefined : t("chatRetry")}
        />
      );
    }
    const fields = (
      <ShareFields
        picked={picked}
        share={share ?? null}
        draft={draft}
        onChange={change}
        tickets={tickets}
        onTicketsChange={setTickets}
        onOpenReviewers={() => router.push("/chat/share-pr-reviewers")}
        problems={problems}
        fieldErrors={fieldErrors}
      />
    );
    if (editing) return fields;
    return (
      <>
        <PullRequestPicker
          picked={picked}
          onPick={(next, summary) => {
            setPicked(next);
            // Each pull request picked presets the status from its GitHub state again.
            change({ ...draft, status: statusFromGithubState(summary.state) });
          }}
        />
        {fields}
      </>
    );
  })();

  const confirmLabel: string = t(editing ? "prFormSave" : "prFormSend");

  return (
    <>
      <Stack.Screen
        options={{
          title: t(editing ? "prFormEditTitle" : "prFormCreateTitle"),
          headerLeft: () => <HeaderCancelButton label={t("chatCancel")} onPress={close} />,
          headerRight: () => (
            <HeaderSaveButton
              label={confirmLabel}
              accessibilityLabel={confirmLabel}
              disabled={blocked}
              saving={create.isPending || edit.isPending}
              onPress={submit}
            />
          ),
        }}
      />
      <KeyboardAvoidingView
        behavior={Platform.OS === "ios" ? "padding" : undefined}
        className="flex-1 bg-screen"
      >
        <ScrollView
          contentInsetAdjustmentBehavior="automatic"
          keyboardShouldPersistTaps="handled"
          keyboardDismissMode="interactive"
          contentContainerClassName="pb-10"
        >
          {body}
        </ScrollView>
      </KeyboardAvoidingView>
    </>
  );
}

function ShareFormSkeleton() {
  const colors = useThemeColors();
  return <ActivityIndicator className="py-8" color={colors.mutedForeground} />;
}

function PullRequestPreview({ facts }: { facts: PullRequestFacts }) {
  const { t } = useTranslation();
  return (
    <View className="mx-4 mt-3 gap-1 rounded-lg border border-border bg-card p-3">
      <Text variant="caption" className="font-sans-medium text-muted-foreground">
        {t("prFormPreview")}
      </Text>
      <Text variant="subhead" className="font-sans-semibold">
        {facts.ticketKey ? `${facts.ticketKey}: ${facts.title}` : facts.title}
      </Text>
      <Text variant="caption" className="text-muted-foreground">
        {facts.reference}
      </Text>
      {facts.headRef ? (
        <View className="flex-row items-center gap-1">
          <Icon as={GitBranch} size={12} className="text-muted-foreground" />
          <Text
            variant="caption"
            numberOfLines={1}
            className="flex-1 font-mono text-muted-foreground"
          >
            {`${facts.headRef} → ${facts.baseRef}`}
          </Text>
        </View>
      ) : null}
    </View>
  );
}

/** The picked pull request read from GitHub, with its branches and ticket key. */
function PickedPreview({ picked }: { picked: PickedPullRequest }) {
  const { t } = useTranslation();
  const colors = useThemeColors();
  const query = useGithubPullRequest(picked.githubRepositoryId, picked.number);
  if (query.isPending) return <ActivityIndicator className="py-4" color={colors.mutedForeground} />;
  if (query.isError) {
    return (
      <InlineError
        message={t(pullRequestErrorKey(pullRequestErrorCode(query.error)) ?? "prPickerError")}
        onRetry={() => void query.refetch()}
        className="mx-4 mt-3"
      />
    );
  }
  const pullRequest = query.data;
  return (
    <PullRequestPreview
      facts={{
        title: pullRequest.title,
        reference: `#${pullRequest.number} · ${pullRequest.authorLogin}`,
        headRef: pullRequest.headRef,
        baseRef: pullRequest.baseRef,
        ticketKey: pullRequest.ticketKey,
      }}
    />
  );
}

function ShareFields({
  picked,
  share,
  draft,
  onChange,
  tickets,
  onTicketsChange,
  onOpenReviewers,
  problems,
  fieldErrors,
}: {
  picked: PickedPullRequest | null;
  share: PullRequestShareResponse | null;
  draft: PullRequestShareDraft;
  onChange: (draft: PullRequestShareDraft) => void;
  tickets: Map<string, TicketChoice>;
  onTicketsChange: (tickets: Map<string, TicketChoice>) => void;
  onOpenReviewers: () => void;
  problems: PullRequestShareProblems;
  fieldErrors: Record<string, string>;
}) {
  const { t } = useTranslation();
  const colors = useThemeColors();
  const membersQuery = useChatMembers();
  const [ticketSearch, setTicketSearch] = React.useState<string>("");
  const [addingTickets, setAddingTickets] = React.useState<boolean>(false);
  const [statusMenu, setStatusMenu] = React.useState<boolean>(false);
  const searchText: string = ticketSearch.trim();
  const suggestions = useChatTicketSuggestions(searchText ? searchText : null);

  // The chosen reviewers' names, for the row's avatars and its hint; a
  // reviewer who left the organization is named by the name the share keeps.
  const reviewerNames: { name: string }[] = React.useMemo(
    () =>
      reviewerSummaryNames(draft.reviewerIds, membersQuery.data ?? [], share?.reviewers ?? []).map(
        (name) => ({ name })
      ),
    [draft.reviewerIds, membersQuery.data, share]
  );

  const removeTicket = (id: string): void => {
    onChange({
      ...draft,
      relatedTicketIds: draft.relatedTicketIds.filter((value) => value !== id),
    });
  };

  const addTicket = (suggestion: ChatTicketSuggestion): void => {
    if (draft.relatedTicketIds.includes(suggestion.id)) return;
    if (draft.relatedTicketIds.length >= PULL_REQUEST_RELATED_TICKETS_MAX) return;
    const next = new Map(tickets);
    next.set(suggestion.id, {
      id: suggestion.id,
      key: suggestion.key,
      title: suggestion.title,
      boardName: suggestion.board?.name ?? null,
    });
    onTicketsChange(next);
    onChange({ ...draft, relatedTicketIds: [...draft.relatedTicketIds, suggestion.id] });
    setTicketSearch("");
  };

  const fieldError = (field: string, local?: string): string | null =>
    fieldErrors[field] ?? local ?? null;

  // Picking a pull request presets the status, so a missing one is pointed out only after that.
  const statusError: string | null = fieldError(
    "status",
    problems.status && (picked || share) ? t(problems.status) : undefined
  );
  const ticketsFull: boolean = draft.relatedTicketIds.length >= PULL_REQUEST_RELATED_TICKETS_MAX;
  const reviewerError: string | null = fieldError(
    "reviewerIds",
    problems.reviewers ? t(problems.reviewers) : undefined
  );

  return (
    <View className="pb-8">
      {share ? (
        <PullRequestPreview
          facts={{
            title: share.title,
            reference: `${share.repositoryFullName} #${share.number} · ${share.githubAuthorLogin}`,
            headRef: share.headRef,
            baseRef: share.baseRef,
            ticketKey: share.ticketKey,
          }}
        />
      ) : picked ? (
        <PickedPreview picked={picked} />
      ) : null}

      <ListSection error={statusError}>
        <ListRow
          title={t("prFormStatus")}
          value={draft.status ? undefined : t("prFormStatusChoose")}
          trailing={draft.status ? <PullRequestStatusPill status={draft.status} /> : undefined}
          accessibilityValue={
            draft.status ? t(SHARE_STATUS_STYLE[draft.status].labelKey) : undefined
          }
          invalid={!!statusError}
          onPress={() => setStatusMenu(true)}
        />
      </ListSection>
      <ActionSheet
        visible={statusMenu}
        onClose={() => setStatusMenu(false)}
        title={t("prFormStatus")}
        actions={PULL_REQUEST_STATUSES.map((status) => ({
          label: t(SHARE_STATUS_STYLE[status].labelKey),
          selected: draft.status === status,
          onPress: () => onChange({ ...draft, status }),
        }))}
        cancelLabel={t("chatClose")}
      />

      <View className="px-4 pt-6">
        <TextField
          label={t("prFormNote")}
          value={draft.note}
          onChangeText={(note: string) => onChange({ ...draft, note })}
          placeholder={t("prFormNotePlaceholder")}
          multiline
          rows={2}
          helper={t("prFormNoteCounter", { count: draft.note.length, max: PULL_REQUEST_NOTE_MAX })}
          error={fieldError(
            "note",
            problems.note ? t(problems.note, { max: PULL_REQUEST_NOTE_MAX }) : undefined
          )}
        />
      </View>

      <ListSection
        footer={t("prFormReviewersHint", {
          count: draft.reviewerIds.length,
          max: PULL_REQUEST_REVIEWERS_MAX,
        })}
        error={reviewerError}
      >
        <ListRow
          icon={Users}
          title={t("prFormReviewers")}
          value={
            draft.reviewerIds.length === 0
              ? t("prFormReviewersNone")
              : reviewerNames.length === 0
                ? String(draft.reviewerIds.length)
                : undefined
          }
          trailing={
            reviewerNames.length > 0 ? (
              <AvatarStack people={reviewerNames} total={draft.reviewerIds.length} size="sm" />
            ) : undefined
          }
          accessibilityHint={
            reviewerNames.length > 0
              ? reviewerNames.map((person) => person.name).join(", ")
              : undefined
          }
          invalid={!!reviewerError}
          onPress={onOpenReviewers}
        />
      </ListSection>

      <ListSection
        header={t("prFormRelatedTickets")}
        footer={t("prFormRelatedTicketsHint", { max: PULL_REQUEST_RELATED_TICKETS_MAX })}
        error={fieldError("relatedTicketIds")}
      >
        {draft.relatedTicketIds.map((id) => {
          const ticket: TicketChoice | undefined = tickets.get(id);
          return (
            <ListRow
              key={id}
              title={ticket ? `${ticket.key} ${ticket.title}` : id}
              subtitle={ticket?.boardName ?? undefined}
              accessory="checkbox"
              checked
              onCheckedChange={() => removeTicket(id)}
              onPress={() => removeTicket(id)}
            />
          );
        })}
        {ticketsFull ? null : (
          <ListRow
            icon={Plus}
            title={t("prFormAddTickets")}
            accessory="none"
            onPress={() => setAddingTickets(true)}
          />
        )}
      </ListSection>
      {ticketsFull || !addingTickets ? null : (
        <View className="gap-1 px-4 pt-2">
          <Input
            autoFocus
            value={ticketSearch}
            onChangeText={setTicketSearch}
            placeholder={t("prFormTicketsSearch")}
            accessibilityLabel={t("prFormTicketsSearch")}
            autoCapitalize="characters"
            autoCorrect={false}
            returnKeyType="search"
          />
        </View>
      )}
      {!ticketsFull && searchText ? (
        suggestions.isError && !suggestions.data ? (
          <InlineError
            message={t("prFormTicketsError")}
            onRetry={() => void suggestions.refetch()}
            className="mx-4 mt-2"
          />
        ) : !suggestions.data ? (
          <ActivityIndicator className="py-3" color={colors.mutedForeground} />
        ) : suggestions.data.length === 0 ? (
          <Text variant="footnote" className="px-8 pt-2 text-muted-foreground">
            {t("prFormTicketsNone")}
          </Text>
        ) : (
          <ListSection>
            {suggestions.data
              .filter((suggestion) => !draft.relatedTicketIds.includes(suggestion.id))
              .map((suggestion) => (
                <ListRow
                  key={suggestion.id}
                  title={`${suggestion.key} ${suggestion.title}`}
                  subtitle={suggestion.board?.name ?? undefined}
                  accessory="none"
                  onPress={() => addTicket(suggestion)}
                />
              ))}
          </ListSection>
        )
      ) : null}
    </View>
  );
}
