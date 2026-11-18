"use client";

import { useCallback, useId, useMemo, useState, type KeyboardEvent, type ReactNode } from "react";
import { useTranslations } from "next-intl";
import { useWatch, type Control } from "react-hook-form";
import { AlertCircle, ArrowLeft, Info, Loader2, Lock } from "lucide-react";
import { useGetMe } from "@oba/shared/features/users/hooks/use-get-me";
import { Link, useRouter } from "@oba/shared/libs/i18n/navigation";
import { Button } from "@/components/ui/button";
import { Form } from "@/components/ui/form";
import { InlineAlert } from "@/components/ui/inline-alert";
import { memberDisplayName } from "@/features/chat/components/chat-channel-dialog";
import { useChatMembers } from "@/features/chat/hooks/use-chat-messages";
import { PULL_REQUESTS_PATH } from "@/features/chat/lib/chat-route";
import { TicketPicker } from "@/features/updates/components/ticket-picker";
import { useTicketPicker } from "@/features/updates/hooks/use-ticket-picker";
import { useGithubPullRequest } from "../hooks/use-github-pull-requests";
import { useCreatePullRequestShare, useEditPullRequestShare, usePullRequestShare } from "../hooks/use-pull-request-shares";
import {
  EMPTY_SHARE_FORM_VALUES,
  statusFromGithubState,
  toShareCreateRequest,
  toShareUpdateRequest,
  usePullRequestShareForm,
} from "../hooks/use-pull-request-share-form";
import { useRelatedTicketSelection, type RelatedTicketSelection } from "../hooks/use-related-ticket-selection";
import {
  PULL_REQUEST_RELATED_TICKETS_MAX,
  type PullRequestShareFormValues,
  type PullRequestShareResponse,
  type PullRequestShareReviewerResponse,
} from "../schemas";
import { pullRequestErrorKey } from "./pull-request-errors";
import { PullRequestPicker, type PickedPullRequest } from "./pull-request-picker";
import { PullRequestShareFields } from "./pull-request-share-form";
import { PullRequestSharePreview } from "./pull-request-share-preview";
import { RelatedTicketsCard } from "./related-tickets-card";

/** No stored reviewers: a create, whose reviewers all come from the members. */
const NO_STORED_REVIEWERS: PullRequestShareReviewerResponse[] = [];

/**
 * Saved filters of the related ticket picker live under this key, beside the
 * board IDs, apart from the daily update picker's.
 */
const RELATED_TICKET_PICKER_PREFERENCES_KEY = "pullRequests";

/**
 * Where "Back to PR channel" leads: the pull requests channel, where the
 * share appears as a card. An organization without one is sent on to
 * `/chats` by that page.
 */
const PULL_REQUEST_CHANNEL_PATH = PULL_REQUESTS_PATH;

export type SharePullRequestViewProps = { mode?: "create" } | { mode: "edit"; shareId: string };

/**
 * The screen to share a pull request in the PR channel, or to edit one's own
 * share. Create picks the pull request from GitHub; edit shows the pull
 * request as it was shared and changes only status, note, reviewers and
 * related tickets. Only
 * the author can edit: anyone else gets a notice and no editable field.
 */
export function SharePullRequestView(props: SharePullRequestViewProps) {
  if (props.mode === "edit") {
    return <EditShareLoader shareId={props.shareId} />;
  }
  return <CreateShareComposer />;
}

// ── Create ────────────────────────────────────────────────────────────

function CreateShareComposer() {
  const t = useTranslations("pullRequests.form");
  const router = useRouter();
  const formId = useId();
  const form = usePullRequestShareForm(EMPTY_SHARE_FORM_VALUES);
  const [repositoryId, setRepositoryId] = useState<number | undefined>(undefined);
  const [selected, setSelected] = useState<PickedPullRequest | null>(null);

  // The GitHub state presets the status each time another pull request is chosen.
  const handleSelectedChange = useCallback(
    (picked: PickedPullRequest | null) => {
      setSelected(picked);
      if (picked) {
        form.setValue("status", statusFromGithubState(picked.summary.state), {
          shouldDirty: true,
          shouldValidate: true,
        });
      }
    },
    [form],
  );

  const detailQuery = useGithubPullRequest(selected?.githubRepositoryId, selected?.summary.number);
  const detail = detailQuery.data?.data;
  const reviewerNames = useReviewerNames(form.control);
  const relatedTickets = useRelatedTicketSelection(form);
  const ticketPicker = useTicketPicker(RELATED_TICKET_PICKER_PREFERENCES_KEY);
  const { status, note } = useWatchedPreviewFields(form.control);

  const createShare = useCreatePullRequestShare();

  // Errors are toasted by the mutation hook; the form stays as it is so the
  // values can be corrected and sent again.
  const handleSubmit = form.handleSubmit((values) => {
    if (!selected) return;
    const data = toShareCreateRequest(values, {
      githubRepositoryId: selected.githubRepositoryId,
      number: selected.summary.number,
    });
    createShare.mutate(data, { onSuccess: () => router.push(PULL_REQUEST_CHANNEL_PATH) });
  });

  let preview: ReactNode;
  if (!selected) {
    preview = (
      <PullRequestSharePreview
        kind="empty"
        status={status}
        note={note}
        reviewerNames={reviewerNames}
        relatedTickets={relatedTickets.chosen}
      />
    );
  } else if (detailQuery.isError) {
    preview = (
      <PullRequestSharePreview
        kind="error"
        error={detailQuery.error}
        isFetching={detailQuery.isFetching}
        onRetry={() => void detailQuery.refetch()}
        status={status}
        note={note}
        reviewerNames={reviewerNames}
        relatedTickets={relatedTickets.chosen}
      />
    );
  } else if (!detail) {
    preview = (
      <PullRequestSharePreview
        kind="loading"
        status={status}
        note={note}
        reviewerNames={reviewerNames}
        relatedTickets={relatedTickets.chosen}
      />
    );
  } else {
    preview = (
      <PullRequestSharePreview
        kind="ready"
        facts={{
          repositoryFullName: selected.repositoryFullName,
          number: detail.number,
          title: detail.title,
          url: detail.url,
          authorLogin: detail.authorLogin,
          headRef: detail.headRef,
          baseRef: detail.baseRef,
          ticketKey: detail.ticketKey,
          state: detail.state,
        }}
        status={status}
        note={note}
        reviewerNames={reviewerNames}
        relatedTickets={relatedTickets.chosen}
      />
    );
  }

  // The pickers stay outside the form and the header button submits it by
  // id, so only Send posts the share: Enter in a picker search must not.
  return (
    <Form {...form}>
      <div className="bg-background flex h-full flex-col">
        <ScreenHeader mode="create">
          <Button
            type="submit"
            form={formId}
            size="sm"
            disabled={createShare.isPending || !selected || tooManyTickets(relatedTickets)}
          >
            {createShare.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
            {t("send")}
          </Button>
        </ScreenHeader>

        <div className="flex-1 overflow-auto">
          <div className="grid gap-4 p-4 lg:p-6 xl:grid-cols-[minmax(0,1fr)_minmax(22rem,28rem)]">
            <div className="flex min-w-0 flex-col gap-4">
              <PullRequestPicker
                repositoryId={repositoryId}
                onRepositoryChange={setRepositoryId}
                selected={selected}
                onSelectedChange={handleSelectedChange}
              />
              <RelatedTicketPicker picker={ticketPicker} selection={relatedTickets} />
            </div>
            <form
              id={formId}
              onSubmit={handleSubmit}
              onKeyDown={preventImplicitSubmit}
              className="flex min-w-0 flex-col gap-4"
              noValidate
            >
              <PullRequestShareFields control={form.control} disabled={createShare.isPending} />
              <RelatedTicketsCard selection={relatedTickets} disabled={createShare.isPending} />
              {preview}
            </form>
          </div>
        </div>
      </div>
    </Form>
  );
}

// ── Edit ──────────────────────────────────────────────────────────────

function EditShareLoader({ shareId }: { shareId: string }) {
  const t = useTranslations("pullRequests");
  const shareQuery = usePullRequestShare(shareId);
  const meQuery = useGetMe();
  const me = meQuery.data?.data;
  const share = shareQuery.data?.data;

  if (shareQuery.isError) {
    const errorKey = pullRequestErrorKey(shareQuery.error);
    return (
      <ScreenState
        mode="edit"
        kind="error"
        title={t("form.loadError")}
        message={errorKey ? t(`errors.${errorKey}`) : shareQuery.error.message}
      />
    );
  }
  if (meQuery.isError) {
    return <ScreenState mode="edit" kind="error" title={t("form.meError")} message={meQuery.error.message} />;
  }
  if (!share || !me) {
    return <ScreenState mode="edit" kind="loading" />;
  }
  if (share.authorId !== me.id) {
    return (
      <ScreenState
        mode="edit"
        kind="notAuthor"
        title={t("form.notAuthor")}
        message={t("form.notAuthorDescription")}
      />
    );
  }

  return <EditShareForm share={share} />;
}

function EditShareForm({ share }: { share: PullRequestShareResponse }) {
  const t = useTranslations("pullRequests.form");
  const router = useRouter();
  const formId = useId();

  // Seeded once: the form ignores later refetches of the share.
  const [defaultValues] = useState<PullRequestShareFormValues>(() => ({
    status: share.status,
    note: share.note ?? "",
    reviewerIds: share.reviewers.map((reviewer) => reviewer.userId),
    relatedTicketIds: share.relatedTickets.map((ticket) => ticket.ticketId),
  }));
  const form = usePullRequestShareForm(defaultValues);
  const reviewerNames = useReviewerNames(form.control, share.reviewers);
  const relatedTickets = useRelatedTicketSelection(form, share.relatedTickets);
  const ticketPicker = useTicketPicker(RELATED_TICKET_PICKER_PREFERENCES_KEY);
  const { status, note } = useWatchedPreviewFields(form.control);

  const editShare = useEditPullRequestShare();
  const handleSubmit = form.handleSubmit((values) => {
    editShare.mutate(
      { shareId: share.id, data: toShareUpdateRequest(values) },
      { onSuccess: () => router.push(PULL_REQUEST_CHANNEL_PATH) },
    );
  });

  return (
    <Form {...form}>
      <div className="bg-background flex h-full flex-col">
        <ScreenHeader mode="edit">
          <Button
            type="submit"
            form={formId}
            size="sm"
            disabled={editShare.isPending || !form.formState.isDirty || tooManyTickets(relatedTickets)}
          >
            {editShare.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
            {t("save")}
          </Button>
        </ScreenHeader>

        {/* The ticket picker stays outside the form, so Enter in its search
            cannot save the share. */}
        <div className="flex-1 overflow-auto">
          <div className="grid gap-4 p-4 lg:p-6 xl:grid-cols-[minmax(0,1fr)_minmax(22rem,28rem)]">
            <RelatedTicketPicker picker={ticketPicker} selection={relatedTickets} />
            <form
              id={formId}
              onSubmit={handleSubmit}
              onKeyDown={preventImplicitSubmit}
              className="flex min-w-0 flex-col gap-4"
              noValidate
            >
              <InlineAlert variant="info" icon={<Info />} description={t("fixedFacts")} />
              <PullRequestShareFields
                control={form.control}
                disabled={editShare.isPending}
                storedReviewers={share.reviewers}
              />
              <RelatedTicketsCard selection={relatedTickets} disabled={editShare.isPending} />
              <PullRequestSharePreview
                kind="ready"
                facts={{
                  repositoryFullName: share.repositoryFullName,
                  number: share.number,
                  title: share.title,
                  url: share.url,
                  authorLogin: share.githubAuthorLogin,
                  headRef: share.headRef,
                  baseRef: share.baseRef,
                  ticketKey: share.ticketKey,
                }}
                status={status}
                note={note}
                reviewerNames={reviewerNames}
                relatedTickets={relatedTickets.chosen}
              />
            </form>
          </div>
        </div>
      </div>
    </Form>
  );
}

// ── Shared ────────────────────────────────────────────────────────────

/** Above the limit the card shows an alert and the share cannot be sent. */
function tooManyTickets(selection: RelatedTicketSelection): boolean {
  return selection.selectedIds.length > PULL_REQUEST_RELATED_TICKETS_MAX;
}

/** The ticket picker of the share screens, ticking the share's related tickets. */
function RelatedTicketPicker({
  picker,
  selection,
}: {
  picker: ReturnType<typeof useTicketPicker>;
  selection: RelatedTicketSelection;
}) {
  const t = useTranslations("pullRequests.form");
  return (
    <TicketPicker
      picker={picker}
      selectedIds={selection.selectedIds}
      onSelectionChange={selection.onSelectionChange}
      title={t("relatedTicketsPickerTitle")}
      description={t("relatedTicketsPickerDescription")}
      saveFiltersTooltip={t("relatedTicketsSaveFilters")}
    />
  );
}

function useWatchedPreviewFields(control: Control<PullRequestShareFormValues>) {
  const status = useWatch({ control, name: "status" });
  const note = useWatch({ control, name: "note" }) ?? "";
  return { status, note };
}

/**
 * Names of the chosen reviewers in their order, from the organization's
 * members, else from the share's stored reviewers for one who has left.
 */
function useReviewerNames(
  control: Control<PullRequestShareFormValues>,
  storedReviewers: PullRequestShareReviewerResponse[] = NO_STORED_REVIEWERS,
): string[] {
  const reviewerIds = useWatch({ control, name: "reviewerIds" });
  const { data: members } = useChatMembers();
  return useMemo(() => {
    const names = new Map<string, string>();
    for (const reviewer of storedReviewers) {
      names.set(
        reviewer.userId,
        [reviewer.firstName, reviewer.lastName].filter(Boolean).join(" ") || reviewer.userId,
      );
    }
    for (const member of members ?? []) names.set(member.userId, memberDisplayName(member));
    return (reviewerIds ?? []).map((userId) => names.get(userId) ?? userId);
  }, [reviewerIds, members, storedReviewers]);
}

/**
 * Enter in the reviewer search would submit the form implicitly and post the
 * share. Textareas keep Enter for a new line, and buttons keep
 * it as a click.
 */
function preventImplicitSubmit(event: KeyboardEvent<HTMLFormElement>) {
  if (event.key === "Enter" && event.target instanceof HTMLInputElement) {
    event.preventDefault();
  }
}

function ScreenHeader({ mode, children }: { mode: "create" | "edit"; children?: ReactNode }) {
  const t = useTranslations("pullRequests.form");
  return (
    <div className="border-border bg-card flex items-center gap-2 border-b px-4 py-2 lg:px-6">
      <Button variant="ghost" size="sm" className="-ml-2 gap-1.5" asChild>
        <Link href={PULL_REQUEST_CHANNEL_PATH} aria-label={t("backToChannel")}>
          <ArrowLeft className="h-4 w-4" />
          <span className="hidden sm:inline">{t("backToChannel")}</span>
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
  const t = useTranslations("pullRequests.form");
  return (
    <div className="bg-background flex h-full flex-col">
      <ScreenHeader mode={props.mode} />
      {props.kind === "loading" ? (
        <div className="flex flex-1 flex-col items-center justify-center">
          <Loader2 className="text-muted-foreground h-8 w-8 animate-spin" />
          <p className="text-muted-foreground mt-3 text-sm">{t("loading")}</p>
        </div>
      ) : (
        <div className="flex flex-1 flex-col items-center justify-center p-6 text-center">
          {props.kind === "notAuthor" ? (
            <Lock className="text-muted-foreground h-8 w-8" />
          ) : (
            <AlertCircle className="text-destructive h-8 w-8" />
          )}
          <p className="text-foreground mt-3 text-sm font-medium">{props.title}</p>
          {props.message && <p className="text-muted-foreground mt-1 max-w-md text-xs">{props.message}</p>}
          <Button variant="outline" className="mt-4" asChild>
            <Link href={PULL_REQUEST_CHANNEL_PATH}>{t("backToChannel")}</Link>
          </Button>
        </div>
      )}
    </div>
  );
}
