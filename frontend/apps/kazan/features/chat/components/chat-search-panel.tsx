"use client";

import { useCallback, useRef, type ReactNode } from "react";
import { useTranslations } from "next-intl";
import {
  AlertCircle,
  CornerDownRight,
  Loader2,
  MessagesSquare,
  Search,
  SearchX,
} from "lucide-react";
import { SenseySearchInput } from "@oba/shared/components/sensey-search-input";
import { Button } from "@/components/ui/button";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { getDisplayName, UserAvatar } from "@/components/ui/user-avatar";
import { useDateFormat } from "@oba/shared/i18n/use-date-format";
import { useChatOpenChannelId } from "../hooks/use-chat-channels";
import { useChatMembers, useChatSearch, useChatThread } from "../hooks/use-chat-messages";
import { useChatParams } from "../hooks/use-chat-params";
import { chatMessagePlainText } from "../lib/chat-message-text";
import { CHAT_SEARCH_MIN, type ChatMessageResponse } from "../schemas";

/** The author filter's value for "every author"; a Select item cannot have an empty value. */
const ANY_AUTHOR = "any";

/** Characters of context kept before a match, so the match is visible in a clamped snippet. */
const SNIPPET_LEAD = 40;

/**
 * The text with the first case-insensitive occurrence of `query` marked. The
 * server matches the same way, on the stored text; a match that lies inside a
 * token (an ID) is not visible as text, so the snippet is shown unmarked then.
 */
function highlight(text: string, query: string | undefined): ReactNode {
  const needle = query?.trim().toLowerCase();
  const index = needle ? text.toLowerCase().indexOf(needle) : -1;
  if (!needle || index < 0) return text;
  const start = Math.max(0, index - SNIPPET_LEAD);
  return (
    <>
      {start > 0 && "…"}
      {text.slice(start, index)}
      <mark className="bg-primary/15 text-foreground rounded-sm font-semibold">
        {text.slice(index, index + needle.length)}
      </mark>
      {text.slice(index + needle.length)}
    </>
  );
}

/** "in thread {title}" for a message inside a thread; the title comes from the cached thread. */
function ThreadName({ threadId }: { threadId: string }) {
  const t = useTranslations("chat.found");
  const { data: thread } = useChatThread(threadId);
  // Until the thread is in (or when it cannot be read) the row still says it is in a thread.
  return (
    <span className="inline-flex min-w-0 items-center gap-1">
      <MessagesSquare className="h-3 w-3 shrink-0" aria-hidden />
      <span className="truncate">
        {thread ? t("inThread", { title: thread.title }) : t("inAThread")}
      </span>
    </span>
  );
}

interface ChatFoundMessageProps {
  message: ChatMessageResponse;
  /** Marked in the snippet. */
  query?: string;
  onJump: (message: ChatMessageResponse) => void;
}

/**
 * A message found by the search or listed among the pins: author, time, the
 * thread it is in, a snippet and Jump.
 */
export function ChatFoundMessage({ message, query, onJump }: ChatFoundMessageProps) {
  const t = useTranslations("chat.found");
  const tMessage = useTranslations("chat.message");
  const dates = useDateFormat();
  const author = message.author;
  const authorUser = author
    ? {
        firstName: author.firstName ?? undefined,
        lastName: author.lastName ?? undefined,
        email: author.email,
      }
    : {};
  const name = author ? getDisplayName(authorUser) : tMessage("unknownMember");
  const text = chatMessagePlainText(message, tMessage("unknownMember"));

  return (
    <div className="group/found hover:bg-muted/60 flex gap-2.5 rounded-md px-2 py-2">
      <UserAvatar user={authorUser} size="sm" showName={false} />
      <div className="flex min-w-0 flex-1 flex-col gap-0.5">
        <div className="flex min-w-0 items-baseline gap-2">
          <span className="text-foreground truncate text-sm font-medium">{name}</span>
          <time dateTime={message.createdAt} className="text-muted-foreground shrink-0 text-xs">
            {dates.formatDateTime(message.createdAt)}
          </time>
        </div>
        {message.threadId && (
          <div className="text-muted-foreground flex min-w-0 text-xs">
            <ThreadName threadId={message.threadId} />
          </div>
        )}
        <p className="text-foreground/90 line-clamp-3 text-sm break-words">
          {text ? (
            highlight(text, query)
          ) : (
            <span className="text-muted-foreground italic">{t("noText")}</span>
          )}
        </p>
      </div>
      <Button
        variant="outline"
        size="sm"
        className="h-7 shrink-0 gap-1 self-start text-xs"
        onClick={() => onJump(message)}
        aria-label={t("jumpTo", { name })}
      >
        <CornerDownRight className="h-3.5 w-3.5" />
        {t("jump")}
      </Button>
    </div>
  );
}

interface ChatSearchPanelProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Shows the message: in the channel around it, or in its thread's panel. */
  onJump: (message: ChatMessageResponse) => void;
}

/**
 * The channel header's search: messages of the channel and its threads that
 * contain the text, newest first, 25 at a time, optionally of one author.
 * The text and the author live in the URL (`q`, `author`), so a search
 * survives a reload; it runs from 2 characters, debounced by the input.
 */
export function ChatSearchPanel({ open, onOpenChange, onJump }: ChatSearchPanelProps) {
  const t = useTranslations("chat.search");
  const [{ q, author }, setParams] = useChatParams();
  const inputRef = useRef<HTMLInputElement>(null);

  const onSearchChange = useCallback(
    (value: string | undefined) => void setParams({ q: value ?? null }),
    [setParams]
  );
  const jump = (message: ChatMessageResponse) => {
    onOpenChange(false);
    onJump(message);
  };

  return (
    <Popover open={open} onOpenChange={onOpenChange}>
      <PopoverTrigger asChild>
        <Button
          variant="outline"
          size="sm"
          className="border-border h-8 gap-1.5 text-xs"
          aria-label={t("button")}
        >
          <Search className="h-3.5 w-3.5" />
          <span className="hidden sm:inline">{t("button")}</span>
        </Button>
      </PopoverTrigger>
      <PopoverContent
        align="end"
        className="flex w-[min(28rem,calc(100vw-2rem))] flex-col gap-2 p-3"
        onOpenAutoFocus={(event) => {
          event.preventDefault();
          inputRef.current?.focus();
        }}
      >
        <h2 className="text-foreground text-sm font-semibold">{t("title")}</h2>
        <div className="flex flex-col gap-2 sm:flex-row">
          <SenseySearchInput
            value={q}
            inputRef={inputRef}
            placeholder={t("placeholder")}
            onSearchChange={onSearchChange}
            className="min-w-0 flex-1"
          />
          <AuthorFilter
            enabled={open}
            value={author}
            onChange={(authorId) => void setParams({ author: authorId })}
          />
        </div>
        <SearchResults q={q} author={author} onJump={jump} />
      </PopoverContent>
    </Popover>
  );
}

function AuthorFilter({
  enabled,
  value,
  onChange,
}: {
  enabled: boolean;
  value: string | null;
  onChange: (authorId: string | null) => void;
}) {
  const t = useTranslations("chat.search");
  const { data: members, isError } = useChatMembers({ enabled });

  return (
    <Select
      value={value ?? ANY_AUTHOR}
      onValueChange={(next) => onChange(next === ANY_AUTHOR ? null : next)}
    >
      <SelectTrigger size="sm" className="h-9 sm:w-44" aria-label={t("author")}>
        <SelectValue placeholder={t("anyAuthor")} />
      </SelectTrigger>
      <SelectContent>
        <SelectItem value={ANY_AUTHOR}>{t("anyAuthor")}</SelectItem>
        {/* A filter from the URL stays selectable while the members load or when they failed. */}
        {value && !members?.some((member) => member.userId === value) && (
          <SelectItem value={value}>
            {isError ? t("authorsLoadError") : t("selectedAuthor")}
          </SelectItem>
        )}
        {members?.map((member) => (
          <SelectItem key={member.userId} value={member.userId}>
            {getDisplayName({
              firstName: member.firstName ?? undefined,
              lastName: member.lastName ?? undefined,
              email: member.email ?? undefined,
            })}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}

function SearchResults({
  q,
  author,
  onJump,
}: {
  q: string | null;
  author: string | null;
  onJump: (message: ChatMessageResponse) => void;
}) {
  const t = useTranslations("chat.search");
  const text = (q ?? "").trim();
  const channelId = useChatOpenChannelId();
  const {
    data,
    error,
    isPending,
    isError,
    isFetching,
    refetch,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
    isFetchNextPageError,
  } = useChatSearch(text, author, channelId);

  if (text.length < CHAT_SEARCH_MIN) {
    return (
      <p className="text-muted-foreground py-6 text-center text-xs">
        {t("hint", { min: CHAT_SEARCH_MIN })}
      </p>
    );
  }

  if (isPending) {
    return (
      <div className="text-muted-foreground flex items-center justify-center gap-2 py-8 text-xs">
        <Loader2 className="h-4 w-4 animate-spin" />
        {t("loading")}
      </div>
    );
  }

  if (isError && !data) {
    return (
      <div role="alert" className="flex flex-col items-center gap-2 py-6 text-center">
        <AlertCircle className="text-destructive h-5 w-5" />
        <p className="text-foreground text-xs font-medium">{t("loadError")}</p>
        {error?.message && <p className="text-muted-foreground text-xs">{error.message}</p>}
        <Button
          variant="outline"
          size="sm"
          className="h-7"
          disabled={isFetching}
          onClick={() => void refetch()}
        >
          {t("retry")}
        </Button>
      </div>
    );
  }

  const messages = data.pages.flatMap((page) => page.items);
  if (messages.length === 0) {
    return (
      <div className="text-muted-foreground flex flex-col items-center gap-2 py-8 text-center text-xs">
        <SearchX className="h-5 w-5" />
        {t("empty")}
      </div>
    );
  }

  return (
    <div className="flex max-h-[60vh] flex-col gap-1 overflow-y-auto">
      <ul className="flex flex-col" aria-label={t("results")}>
        {messages.map((message) => (
          <li key={message.id}>
            <ChatFoundMessage message={message} query={text} onJump={onJump} />
          </li>
        ))}
      </ul>
      <ChatLoadMore
        hasNextPage={hasNextPage}
        isFetchingNextPage={isFetchingNextPage}
        isFetchNextPageError={isFetchNextPageError}
        fetchNextPage={fetchNextPage}
        labels={{ loadMore: t("loadMore"), loadMoreError: t("loadMoreError"), retry: t("retry") }}
      />
    </div>
  );
}

interface LoadMoreProps {
  hasNextPage: boolean;
  isFetchingNextPage: boolean;
  isFetchNextPageError: boolean;
  fetchNextPage: () => Promise<unknown>;
  labels: { loadMore: string; loadMoreError: string; retry: string };
}

/** "Load more" below a newest-first list, or the error of the last attempt with Retry. */
export function ChatLoadMore({
  hasNextPage,
  isFetchingNextPage,
  isFetchNextPageError,
  fetchNextPage,
  labels,
}: LoadMoreProps) {
  if (isFetchNextPageError) {
    return (
      <div role="alert" className="flex items-center justify-center gap-2 py-1 text-xs">
        <AlertCircle className="text-destructive h-4 w-4 shrink-0" />
        <span className="text-foreground">{labels.loadMoreError}</span>
        <Button variant="outline" size="sm" className="h-7" onClick={() => void fetchNextPage()}>
          {labels.retry}
        </Button>
      </div>
    );
  }
  if (!hasNextPage) return null;
  return (
    <Button
      variant="ghost"
      size="sm"
      className="h-7 self-center text-xs"
      disabled={isFetchingNextPage}
      onClick={() => void fetchNextPage()}
    >
      {isFetchingNextPage && <Loader2 className="h-3.5 w-3.5 animate-spin" />}
      {labels.loadMore}
    </Button>
  );
}
