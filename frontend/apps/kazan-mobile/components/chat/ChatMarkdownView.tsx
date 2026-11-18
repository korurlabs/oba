import * as Clipboard from "expo-clipboard";
import { Check, Copy } from "lucide-react-native";
import * as React from "react";
import { Pressable, ScrollView, View } from "react-native";

import {
  chatMentionTone,
  chatUserDisplayName,
  isOwnChatMessage,
  type ChatMentionTone,
} from "@/components/chat/chatDisplay";
import {
  chatCodeView,
  parseChatMarkdown,
  type ChatBlock,
  type ChatCodeView,
  type ChatInline,
} from "@/components/chat/chatMarkdown";
import { isBlankChatInlines, takeChatMessageLinks } from "@/components/chat/chatMessageLinks";
import { ChatMessageLinkChip } from "@/components/chat/ChatMessageLinkChip";
import {
  chatLinkedTicketKeys,
  chatTicketChipTitle,
  chatTicketRefFinder,
  splitChatTicketKeys,
} from "@/components/chat/chatTicketRefs";
import { Button } from "@/components/ui/button";
import { Text } from "@/components/ui/text";
import { toast } from "@/components/ui/toast";
import { useTranslation } from "@/i18n";
import {
  CHAT_MENTION_EVERYONE,
  CHAT_MENTION_HERE,
  type ChatMessageResponse,
  type ChatTicketRefResponse,
  type ChatUserResponse,
} from "@/lib/schemas/chat";
import { cn } from "@/lib/utils";

export interface ChatMarkdownHandlers {
  /** Opens a ticket the reader may open, by its key. */
  onOpenTicket: (ticketKey: string) => void;
  /** Opens a link the parser accepted as safe. */
  onOpenLink: (url: string) => void;
}

interface RenderContext extends ChatMarkdownHandlers {
  usersById: ReadonlyMap<string, ChatUserResponse>;
  findTicketRef: (ticketId: string) => ChatTicketRefResponse | undefined;
  /** Plain ticket keys the reader can open, as the server listed them. */
  linkedKeys: ReadonlySet<string>;
  meId: string | undefined;
  /** True when the message notifies the reader, which highlights `@everyone` and `@here`. */
  mentionsMe: boolean;
  /** True in the reader's own bubble, whose `primary-light` ground a chip cannot share. */
  own: boolean;
  unknownMember: string;
  unknownTicket: string;
}

/** A narrow no-break space: the chip's padding, which nested text has no other way to draw. */
const CHIP_PAD = "\u202F";

/**
 * The ground and ink of a mention chip, as the web's: the reader's own on the
 * highlight, a member's tinted as a link, a non-member's muted. In the
 * reader's own bubble, already on `primary-light`, the chips sit on `card`.
 */
function mentionChipClass(tone: ChatMentionTone, own: boolean): string {
  switch (tone) {
    case "me":
      return "bg-chat-highlight text-foreground";
    case "member":
      return own ? "bg-card text-link" : "bg-primary-light text-link";
    case "unknown":
      return "bg-card text-muted-foreground";
  }
}

/**
 * A `<#ticketId>` token as the web's ticket chip: the key in mono and the
 * title, tinted as a link when the reader may open the ticket, muted when
 * not, and "#Unknown ticket" without a snapshot.
 */
function TicketChip({
  ticketId,
  context,
}: {
  ticketId: string;
  context: RenderContext;
}): React.ReactElement {
  const ref: ChatTicketRefResponse | undefined = context.findTicketRef(ticketId);
  if (!ref) {
    return (
      <Text className="bg-muted text-muted-foreground">
        {`${CHIP_PAD}#${context.unknownTicket}${CHIP_PAD}`}
      </Text>
    );
  }
  const title: string = chatTicketChipTitle(ref.ticketTitle);
  const openable: boolean = ref.canOpen && !!ref.ticketId;
  return (
    <Text
      accessibilityRole={openable ? "link" : "text"}
      accessibilityLabel={`${ref.ticketKey} ${ref.ticketTitle}`}
      onPress={openable ? () => context.onOpenTicket(ref.ticketKey) : undefined}
      className={openable ? "bg-primary-light text-link" : "bg-muted text-foreground"}
    >
      {CHIP_PAD}
      <Text className="font-mono-medium text-code">{ref.ticketKey}</Text>
      {title ? ` ${title}` : null}
      {CHIP_PAD}
    </Text>
  );
}

/** How long the Copy button shows its confirmation. */
const COPIED_MS = 1500;

/**
 * A fenced code block: the language and a Copy button for the raw code on
 * top, the code scrolling sideways so lines keep their shape, and only the
 * first 20 lines of a longer block until "Show more", so one paste does not
 * fill the screen. It sits on `card`, which stands out in both bubble colours.
 *
 * The scroller does not grow (a ScrollView grows by default): the bubble
 * takes its height from its content, and a growing child is stretched to the
 * height the list offers the row while that content is measured, which drew
 * the bubble thousands of points tall and empty.
 */
function ChatCodeBlock({ language, code }: { language: string | null; code: string }) {
  const { t } = useTranslation();
  const [expanded, setExpanded] = React.useState<boolean>(false);
  const [copied, setCopied] = React.useState<boolean>(false);
  const view: ChatCodeView = chatCodeView(code, expanded);

  React.useEffect(() => {
    if (!copied) return;
    const timer: ReturnType<typeof setTimeout> = setTimeout(() => setCopied(false), COPIED_MS);
    return () => clearTimeout(timer);
  }, [copied]);

  const copy = (): void => {
    Clipboard.setStringAsync(code)
      .then(() => {
        toast.success(t("chatCodeCopied"));
        setCopied(true);
      })
      .catch((error: unknown) => {
        console.warn(
          "[chat] copying a code block failed",
          error instanceof Error ? error.message : error
        );
        toast.error(t("chatCopyFailed"));
      });
  };

  return (
    <View className="overflow-hidden rounded-md border border-border bg-card">
      <View className="flex-row items-center justify-between gap-2 border-b border-border pl-3">
        <Text
          variant="caption"
          numberOfLines={1}
          className="shrink font-mono text-muted-foreground"
        >
          {language ?? t("chatCodePlain")}
        </Text>
        <Button
          variant="ghost"
          size="sm"
          icon={copied ? Check : Copy}
          accessibilityLabel={t("chatCodeCopyA11y")}
          onPress={copy}
        >
          {copied ? t("chatCopied") : t("chatCodeCopy")}
        </Button>
      </View>
      <ScrollView
        horizontal
        className="grow-0"
        showsHorizontalScrollIndicator={false}
        contentContainerClassName="px-3 py-2"
      >
        <Text className="font-mono text-code text-foreground">{view.text}</Text>
      </ScrollView>
      {view.collapsible ? (
        <Pressable
          accessibilityRole="button"
          accessibilityState={{ expanded }}
          onPress={() => setExpanded((value: boolean) => !value)}
          hitSlop={{ top: 6, bottom: 6 }}
          className="min-h-9 justify-center border-t border-border px-3 active:opacity-60"
        >
          <Text variant="footnote" className="text-link">
            {expanded ? t("chatCodeShowLess") : t("chatCodeShowMore", { count: view.lineCount })}
          </Text>
        </Pressable>
      ) : null}
    </View>
  );
}

/** Plain text, with the ticket keys the reader can open as links (not inside a link). */
function renderPlain(text: string, context: RenderContext, key: string, inLink: boolean) {
  if (inLink) return <React.Fragment key={key}>{text}</React.Fragment>;
  return splitChatTicketKeys(text, context.linkedKeys).map((part, index) =>
    part.kind === "text" ? (
      <React.Fragment key={`${key}-${index}`}>{part.text}</React.Fragment>
    ) : (
      <Text
        key={`${key}-${index}`}
        accessibilityRole="link"
        className="font-mono-medium text-code text-link"
        onPress={() => context.onOpenTicket(part.ticketKey)}
      >
        {part.ticketKey}
      </Text>
    )
  );
}

function renderInlines(
  inlines: readonly ChatInline[],
  context: RenderContext,
  keyPrefix: string,
  inLink: boolean = false
): React.ReactNode[] {
  return inlines.map((inline, index) => {
    const key = `${keyPrefix}-${index}`;
    switch (inline.type) {
      case "text":
        return renderPlain(inline.text, context, key, inLink);
      case "code":
        return (
          <Text key={key} className="bg-card font-mono text-code text-foreground">
            {inline.text}
          </Text>
        );
      case "bold":
        return (
          <Text key={key} className="font-sans-semibold">
            {renderInlines(inline.children, context, key, inLink)}
          </Text>
        );
      case "italic":
        return (
          <Text key={key} className="italic">
            {renderInlines(inline.children, context, key, inLink)}
          </Text>
        );
      case "strike":
        return (
          <Text key={key} className="line-through">
            {renderInlines(inline.children, context, key, inLink)}
          </Text>
        );
      case "link":
        return (
          <Text
            key={key}
            accessibilityRole="link"
            className="text-link underline"
            onPress={() => context.onOpenLink(inline.url)}
          >
            {renderInlines(inline.children, context, key, true)}
          </Text>
        );
      case "mention": {
        const special: boolean =
          inline.target === CHAT_MENTION_EVERYONE || inline.target === CHAT_MENTION_HERE;
        const user: ChatUserResponse | undefined = special
          ? undefined
          : context.usersById.get(inline.target.toLowerCase());
        const name: string = special
          ? inline.target
          : chatUserDisplayName(user, context.unknownMember);
        const tone: ChatMentionTone = chatMentionTone(
          inline.target,
          user,
          context.meId,
          context.mentionsMe
        );
        return (
          <Text key={key} className={cn("font-sans-semibold", mentionChipClass(tone, context.own))}>
            {`${CHIP_PAD}@${name}${CHIP_PAD}`}
          </Text>
        );
      }
      case "ticket":
        return <TicketChip key={key} ticketId={inline.ticketId} context={context} />;
      default:
        return null;
    }
  });
}

/**
 * Inline text with the links to chat messages it holds shown below it as
 * previews of those messages; text that held only such a link shows only
 * the preview.
 */
function renderTextWithMessageLinks(
  inlines: readonly ChatInline[],
  context: RenderContext,
  key: string,
  className?: string
): React.ReactNode {
  const { inlines: rest, links } = takeChatMessageLinks(inlines);
  const text: React.ReactNode = (
    <Text key={key} className={className}>
      {renderInlines(rest, context, key)}
    </Text>
  );
  if (links.length === 0) return text;
  return (
    <View key={key} className={cn("gap-1.5", className)}>
      {isBlankChatInlines(rest) ? null : text}
      {links.map((link) => (
        <ChatMessageLinkChip key={link.messageId} link={link} onOpenLink={context.onOpenLink} />
      ))}
    </View>
  );
}

function renderBlock(block: ChatBlock, context: RenderContext, key: string): React.ReactNode {
  switch (block.type) {
    case "paragraph":
      return renderTextWithMessageLinks(block.inlines, context, key);
    case "code":
      return <ChatCodeBlock key={key} language={block.language} code={block.code} />;
    case "quote":
      return (
        <View key={key} className="gap-1 border-l-2 border-border pl-3">
          {block.blocks.map((child, index) => renderBlock(child, context, `${key}-${index}`))}
        </View>
      );
    case "list":
      return (
        <View key={key} className="gap-0.5">
          {block.items.map((item, index) => (
            <View key={`${key}-${index}`} className="flex-row gap-2">
              <Text className="text-muted-foreground">
                {block.ordered ? `${block.start + index}.` : "•"}
              </Text>
              {renderTextWithMessageLinks(item, context, `${key}-${index}`, "shrink")}
            </View>
          ))}
        </View>
      );
    default:
      return null;
  }
}

type ChatMarkdownViewProps = ChatMarkdownHandlers & {
  message: ChatMessageResponse;
  meId: string | undefined;
};

/**
 * A message's text as the web app renders it: paragraphs, quotes, lists and
 * code, with mentions as name chips (the reader's own highlighted), ticket
 * tokens as the web's ticket chips (key and title) and plain ticket keys the
 * server linked as links; both open the ticket when the reader may see it.
 *
 * The text is not selectable: a long press on a message opens its actions,
 * and selectable text would start the system's selection with its Copy
 * bubble under the same press, over the actions sheet. The sheet's Copy
 * text action copies the message instead.
 */
function ChatMarkdownViewImpl({ message, meId, onOpenTicket, onOpenLink }: ChatMarkdownViewProps) {
  const { t } = useTranslation();
  const blocks: ChatBlock[] = React.useMemo(
    () => parseChatMarkdown(message.content),
    [message.content]
  );
  const context: RenderContext = {
    usersById: new Map(
      Object.entries(message.users).map(([id, user]) => [id.toLowerCase(), user] as const)
    ),
    findTicketRef: chatTicketRefFinder(message.content, message.ticketRefs),
    linkedKeys: chatLinkedTicketKeys(message.linkedTickets),
    meId,
    mentionsMe: message.mentionsMe,
    own: isOwnChatMessage(message, meId),
    unknownMember: t("chatUnknownMember"),
    unknownTicket: t("chatUnknownTicket"),
    onOpenTicket,
    onOpenLink,
  };
  return (
    <View className="gap-1.5">
      {blocks.map((block, index) => renderBlock(block, context, `b${index}`))}
    </View>
  );
}

export const ChatMarkdownView = React.memo(ChatMarkdownViewImpl);
