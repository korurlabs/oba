import { MessageSquareText } from "lucide-react-native";
import * as React from "react";
import { Pressable, View } from "react-native";

import { chatAuthorName, chatMessageSnippet } from "@/components/chat/chatDisplay";
import { Avatar } from "@/components/ui/avatar";
import { Icon } from "@/components/ui/icon";
import { Skeleton } from "@/components/ui/skeleton";
import { Text } from "@/components/ui/text";
import { useChatMessage, useChatThread } from "@/hooks/queries/chat";
import { useTranslation } from "@/i18n";
import type { ChatMessageLink } from "@/lib/kaizenLinks";
import { cn } from "@/lib/utils";

/** About one line of the linked message. */
const SNIPPET_LENGTH = 80;

/** The avatar of the linked message's author, smaller than a quote's. */
const AUTHOR_AVATAR_PX = 18;

const CHIP_CLASS = "flex-row items-center gap-2 rounded-md border border-border bg-card p-2";

/**
 * A link to a chat message, shown below the text that held it as a preview
 * of the message: the author, the start of the text and the thread when the
 * message is in one. A tap opens the message in the app as the link would.
 * A message that is deleted or not readable here shows as unavailable and
 * opens nothing; a preview that failed for another reason still opens the
 * link, which may be fine.
 */
export function ChatMessageLinkChip({
  link,
  onOpenLink,
}: {
  link: ChatMessageLink;
  onOpenLink: (url: string) => void;
}) {
  const { t } = useTranslation();
  const { data: message, isPending, isError, error } = useChatMessage(link.messageId);
  const threadId: string | null = message?.threadId ?? link.threadId;
  const { data: thread } = useChatThread(message && threadId ? threadId : null);

  React.useEffect(() => {
    if (!isError) return;
    console.warn(
      "[chat] the preview of a message link could not be loaded",
      link.messageId,
      error instanceof Error ? error.message : error
    );
  }, [isError, error, link.messageId]);

  if (message === null || message?.deleted) {
    return (
      <View accessibilityRole="text" className={cn(CHIP_CLASS, "border-l-[3px]")}>
        <Icon as={MessageSquareText} size={14} className="text-muted-foreground" />
        <Text variant="footnote" className="italic text-muted-foreground">
          {t("chatLinkUnavailable")}
        </Text>
      </View>
    );
  }

  const open = (): void => onOpenLink(link.href);

  if (isPending || isError || !message) {
    const label: string = isError ? t("chatLinkError") : t("chatLinkLoading");
    return (
      <Pressable
        onPress={open}
        accessibilityRole="link"
        accessibilityLabel={label}
        className={cn(CHIP_CLASS, "border-l-[3px] border-l-primary active:opacity-60")}
      >
        <Icon as={MessageSquareText} size={14} className="text-muted-foreground" />
        {isError ? (
          <Text variant="footnote" numberOfLines={1} className="shrink text-muted-foreground">
            {label}
          </Text>
        ) : (
          <Skeleton width={160} height={12} />
        )}
      </Pressable>
    );
  }

  const author: string = chatAuthorName(message, t("chatUnknownMember"));
  const snippet: string =
    chatMessageSnippet(message, t("chatUnknownMember"), SNIPPET_LENGTH) || t("chatQuoteAttachment");
  const inThread: string | null = thread ? t("chatLinkInThread", { title: thread.title }) : null;

  return (
    <Pressable
      onPress={open}
      accessibilityRole="link"
      accessibilityLabel={[t("chatLinkJump", { name: author }), snippet, inThread]
        .filter(Boolean)
        .join(", ")}
      className={cn(CHIP_CLASS, "border-l-[3px] border-l-primary active:opacity-60")}
    >
      <Avatar name={author} size={AUTHOR_AVATAR_PX} />
      <View className="min-w-0 shrink grow">
        <Text variant="footnote" numberOfLines={1} className="font-sans-semibold text-foreground">
          {author}
        </Text>
        <Text variant="footnote" numberOfLines={1} className="text-muted-foreground">
          {snippet}
        </Text>
      </View>
      {inThread ? (
        <Text
          variant="caption"
          numberOfLines={1}
          className="max-w-[40%] shrink text-muted-foreground"
        >
          {inThread}
        </Text>
      ) : null}
    </Pressable>
  );
}
