import { useLocalSearchParams, useRouter } from "expo-router";
import { ArrowLeft, SearchX, UserRound, X } from "lucide-react-native";
import * as React from "react";
import {
  FlatList,
  Keyboard,
  Platform,
  ScrollView,
  View,
  type ListRenderItemInfo,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";

import { chatMemberName } from "@/components/chat/chatDisplay";
import { ChatFoundMessage, ChatLoadMore } from "@/components/chat/ChatFoundMessage";
import {
  chatFoundDestination,
  chatSearchText,
  type ChatFoundDestination,
} from "@/components/chat/chatFoundModel";
import { requestChatScreen } from "@/components/chat/chatScreenRequests";
import { EmptyState } from "@/components/kaizen/EmptyState";
import { ListRow, ListSection } from "@/components/kaizen/List";
import { FilterChip } from "@/components/ui/chip";
import { IconButton } from "@/components/ui/icon-button";
import { Input } from "@/components/ui/input";
import { SkeletonRow } from "@/components/ui/skeleton";
import { Text } from "@/components/ui/text";
import { useThemeColors } from "@/components/ui/use-theme-colors";
import {
  useChatChannelMembers,
  useChatChannels,
  useChatMembers,
  useChatSearch,
} from "@/hooks/queries/chat";
import { useTranslation } from "@/i18n";
import {
  CHAT_SEARCH_MAX,
  CHAT_SEARCH_MIN,
  ChatChannelVisibility,
  type ChatChannelResponse,
  type ChatMemberResponse,
  type ChatMessageResponse,
} from "@/lib/schemas/chat";

/** Typing pauses this long before the search runs, so a word is one request. */
const SEARCH_DEBOUNCE_MS = 300;

function useDebounced(value: string, delay: number): string {
  const [debounced, setDebounced] = React.useState<string>(value);
  React.useEffect(() => {
    const timer: ReturnType<typeof setTimeout> = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(timer);
  }, [value, delay]);
  return debounced;
}

/** "# general" for a public channel, the bare name for a private one. */
function channelLabel(channel: ChatChannelResponse): string {
  return channel.visibility === ChatChannelVisibility.PRIVATE ? channel.name : `# ${channel.name}`;
}

const keyExtractor = (message: ChatMessageResponse): string => message.id;

/**
 * Message search, pushed from a channel's Search action (`channelId`: that
 * channel and its threads) or from ChatHome (every channel the reader can
 * read). Matches come newest first, 25 at a time, optionally of one author.
 * A result opens in its channel, or in its thread over the channel.
 */
export default function ChatSearchScreen() {
  const { t } = useTranslation();
  const router = useRouter();
  const colors = useThemeColors();
  const params = useLocalSearchParams<{ channelId?: string }>();
  const channelId: string | null = params.channelId || null;
  const channels = useChatChannels();
  const channelsById: ReadonlyMap<string, ChatChannelResponse> = React.useMemo(
    () => new Map((channels.data ?? []).map((channel) => [channel.id, channel])),
    [channels.data]
  );
  const channel: ChatChannelResponse | undefined = channelId
    ? channelsById.get(channelId)
    : undefined;
  const channelMembers = useChatChannelMembers(channel);
  const organizationMembers = useChatMembers();
  const authorsQuery = channelId ? channelMembers : organizationMembers;
  const authors: ChatMemberResponse[] = authorsQuery.data ?? [];

  const [input, setInput] = React.useState<string>("");
  const [authorId, setAuthorId] = React.useState<string | null>(null);
  const [pickingAuthor, setPickingAuthor] = React.useState<boolean>(false);
  const text: string | null = chatSearchText(useDebounced(input, SEARCH_DEBOUNCE_MS));
  const query = useChatSearch({ q: text ?? "", channelId, authorId });
  const messages: ChatMessageResponse[] = React.useMemo(
    () => query.data?.pages.flatMap((page) => page.items) ?? [],
    [query.data]
  );

  const author: ChatMemberResponse | undefined = authorId
    ? authors.find((member) => member.userId === authorId)
    : undefined;
  const authorLabel: string = authorId
    ? author
      ? chatMemberName(author)
      : t("chatSearchSelectedAuthor")
    : t("chatSearchAnyAuthor");

  const onJump = React.useCallback(
    (message: ChatMessageResponse) => {
      Keyboard.dismiss();
      const destination: ChatFoundDestination = chatFoundDestination(message, channelId);
      if (destination.kind === "request") {
        // The channel under this screen shows the message once it is on top again.
        requestChatScreen({
          kind: "jump",
          channelId: destination.channelId,
          threadId: destination.threadId,
          messageId: destination.messageId,
        });
        router.back();
        return;
      }
      router.push({
        pathname: "/chat/channel/[channelId]",
        params: destination.threadId
          ? {
              channelId: destination.channelId,
              thread: destination.threadId,
              message: destination.messageId,
            }
          : { channelId: destination.channelId, message: destination.messageId },
      });
    },
    [channelId, router]
  );

  const renderItem = React.useCallback(
    ({ item }: ListRenderItemInfo<ChatMessageResponse>) => {
      const found: ChatChannelResponse | undefined = channelId
        ? undefined
        : channelsById.get(item.channelId);
      return (
        <ChatFoundMessage
          message={item}
          query={text}
          channelName={found ? channelLabel(found) : null}
          onJump={onJump}
        />
      );
    },
    [channelId, channelsById, text, onJump]
  );

  const field = (
    <Input
      value={input}
      onChangeText={setInput}
      placeholder={t("chatSearchPlaceholder")}
      placeholderTextColor={colors.mutedForeground}
      accessibilityLabel={t("chatSearchPlaceholder")}
      autoFocus
      autoCorrect={false}
      autoCapitalize="none"
      returnKeyType="search"
      clearButtonMode="while-editing"
      maxLength={CHAT_SEARCH_MAX}
      onFocus={() => setPickingAuthor(false)}
      className="flex-1"
    />
  );

  const resultsLabel: string = (() => {
    const count: number = messages.length;
    if (channel) {
      const name: string = channelLabel(channel);
      return query.hasNextPage
        ? t("chatSearchResultsChannelMore", { count, channel: name })
        : t("chatSearchResultsChannel", { count, channel: name });
    }
    return query.hasNextPage
      ? t("chatSearchResultsAllMore", { count })
      : t("chatSearchResultsAll", { count });
  })();

  const body: React.ReactElement = (() => {
    if (pickingAuthor) {
      return (
        <ScrollView
          contentInsetAdjustmentBehavior="automatic"
          keyboardShouldPersistTaps="handled"
          contentContainerClassName="pb-10"
        >
          <ListSection header={t("chatSearchAuthor")}>
            <ListRow
              title={t("chatSearchAnyAuthor")}
              accessory="check"
              checked={authorId === null}
              onPress={() => {
                setAuthorId(null);
                setPickingAuthor(false);
              }}
            />
            {authors.map((member) => (
              <ListRow
                key={member.userId}
                title={chatMemberName(member)}
                avatar={{ name: chatMemberName(member) }}
                accessory="check"
                checked={authorId === member.userId}
                onPress={() => {
                  setAuthorId(member.userId);
                  setPickingAuthor(false);
                }}
              />
            ))}
          </ListSection>
          {authorsQuery.isError ? (
            <EmptyState
              variant="error"
              title={t("chatSearchAuthorsError")}
              onAction={() => void authorsQuery.refetch()}
              className="mt-4"
            />
          ) : null}
        </ScrollView>
      );
    }
    if (text === null) {
      return <EmptyState title={t("chatSearchHint", { min: CHAT_SEARCH_MIN })} className="mt-16" />;
    }
    if (query.isPending) {
      return (
        <ListSection>
          {[0, 1, 2, 3].map((index) => (
            <SkeletonRow key={index} />
          ))}
        </ListSection>
      );
    }
    if (query.isError && messages.length === 0) {
      return (
        <EmptyState
          variant="error"
          title={t("chatSearchError")}
          onAction={() => void query.refetch()}
          className="mt-16"
        />
      );
    }
    if (messages.length === 0) {
      return (
        <EmptyState
          icon={SearchX}
          title={t("chatSearchEmpty", { query: text })}
          className="mt-16"
        />
      );
    }
    return (
      <FlatList
        data={messages}
        keyExtractor={keyExtractor}
        renderItem={renderItem}
        keyboardShouldPersistTaps="handled"
        keyboardDismissMode="on-drag"
        contentInsetAdjustmentBehavior="automatic"
        contentContainerClassName="pb-10"
        ListHeaderComponent={
          <Text variant="footnote" className="px-4 pb-1 pt-2 text-muted-foreground">
            {resultsLabel}
          </Text>
        }
        ListFooterComponent={
          <ChatLoadMore
            hasNextPage={query.hasNextPage}
            isFetchingNextPage={query.isFetchingNextPage}
            isFetchNextPageError={query.isFetchNextPageError}
            fetchNextPage={query.fetchNextPage}
          />
        }
      />
    );
  })();

  return (
    <SafeAreaView
      // iOS has the navigation bar above; Android is a full-screen search view with its own back arrow.
      edges={Platform.OS === "ios" ? [] : ["top"]}
      className="flex-1 bg-screen"
    >
      <View className="flex-row items-center gap-2 px-4 pb-2 pt-2">
        {Platform.OS === "ios" ? null : (
          <IconButton icon={ArrowLeft} label={t("chatSearchBack")} onPress={() => router.back()} />
        )}
        {field}
        {/* iOS draws its own clear button inside the field; Android has none. */}
        {Platform.OS === "android" && input.length > 0 ? (
          <IconButton icon={X} label={t("kitClearSearch")} onPress={() => setInput("")} />
        ) : null}
      </View>
      <View className="flex-row px-4 pb-2">
        <FilterChip
          label={authorLabel}
          icon={UserRound}
          selected={authorId !== null}
          dropdown
          accessibilityLabel={`${t("chatSearchAuthor")}: ${authorLabel}`}
          onPress={() => {
            Keyboard.dismiss();
            setPickingAuthor((open: boolean) => !open);
          }}
        />
      </View>
      {body}
    </SafeAreaView>
  );
}
