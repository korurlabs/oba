package com.korur.oba.apps.kaizen.chat.helper;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party

// 3. Java SDK
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 4. Internal
import com.korur.oba.apps.kaizen.chat.constant.ChatConstant;
import com.korur.oba.apps.kaizen.chat.dto.response.ChatUserResponse;
import com.korur.oba.apps.kaizen.chat.entity.ChatMessageTicketRef;

/**
 * Reads the mention and ticket tokens of a message, finds the ticket keys
 * typed as plain text, and renders a message as plain text.
 *
 * Inline code spans and fenced code blocks are masked first: nothing inside
 * code is a mention, a ticket reference or a ticket key, so it is neither
 * stored as one nor counted against the limits, and the client renderer
 * applies the same rule. Masking replaces every masked character with a
 * space, so positions in the masked text are positions in the original.
 *
 * Stateless and thread-safe; the patterns are compiled constants.
 */
@Component
public class ChatTokenParser {

    private static final char MASK_CHARACTER = ' ';

    /**
     * Any token, for rendering. Group 1 is the target of a user token, group 2
     * the ID of a ticket token; exactly one of them is set.
     */
    private static final Pattern ANY_TOKEN_PATTERN = Pattern.compile(
            ChatConstant.USER_TOKEN_PATTERN.pattern() + "|" + ChatConstant.TICKET_TOKEN_PATTERN.pattern());

    /**
     * The tokens of one message, each list distinct and in order of first appearance.
     *
     * @param userIds          users mentioned directly
     * @param mentionsEveryone true when the message mentions everyone
     * @param mentionsHere     true when the message mentions the members online
     * @param ticketIds        tickets referenced
     */
    public record ChatTokens(List<UUID> userIds, boolean mentionsEveryone, boolean mentionsHere,
                             List<UUID> ticketIds) {
    }

    /**
     * Reads the tokens outside code. A malformed token, such as a user token
     * whose ID is no UUID, is plain text; a repeated token counts once.
     *
     * @param content message text; null reads as empty
     * @return the tokens
     */
    public ChatTokens parse(String content) {
        String masked = maskCode(content);
        Set<UUID> userIds = new LinkedHashSet<>();
        boolean mentionsEveryone = false;
        boolean mentionsHere = false;
        Matcher userMatcher = ChatConstant.USER_TOKEN_PATTERN.matcher(masked);
        while (userMatcher.find()) {
            String target = userMatcher.group(1);
            if (ChatConstant.MENTION_EVERYONE.equals(target)) {
                mentionsEveryone = true;
            } else if (ChatConstant.MENTION_HERE.equals(target)) {
                mentionsHere = true;
            } else {
                userIds.add(UUID.fromString(target));
            }
        }
        Set<UUID> ticketIds = new LinkedHashSet<>();
        Matcher ticketMatcher = ChatConstant.TICKET_TOKEN_PATTERN.matcher(masked);
        while (ticketMatcher.find()) {
            ticketIds.add(UUID.fromString(ticketMatcher.group(1)));
        }
        return new ChatTokens(List.copyOf(userIds), mentionsEveryone, mentionsHere, List.copyOf(ticketIds));
    }

    /**
     * Finds the ticket keys typed as plain text: outside code and outside
     * tokens, because a UUID inside a token can look like a key.
     *
     * @param content message text; null reads as empty
     * @return the keys as typed, distinct and in order of first appearance
     */
    public List<String> extractTicketKeys(String content) {
        String masked = maskTokens(maskCode(content));
        Set<String> keys = new LinkedHashSet<>();
        Matcher matcher = ChatConstant.TICKET_KEY_PATTERN.matcher(masked);
        while (matcher.find()) {
            keys.add(matcher.group());
        }
        return List.copyOf(keys);
    }

    /**
     * Returns the form of a typed key that ticket keys are stored in. Keys are
     * generated in upper case and matched exactly, so "acme-218" finds ACME-218.
     *
     * @param typedKey a key from extractTicketKeys
     * @return the key in upper case
     */
    public String normalizeTicketKey(String typedKey) {
        return typedKey.toUpperCase(Locale.ROOT);
    }

    /**
     * Renders a message as plain text: a user token becomes "@First Last", or
     * "@unknown member" for a user who is no active member; the everyone and
     * here tokens become "@everyone" and "@here"; a ticket token becomes the
     * key and title of its snapshot. Tokens inside code stay as written,
     * because the reader sees them literally there too. Markdown is kept.
     *
     * @param content    message text; null reads as empty
     * @param users      users by ID, as on a page; a missing user renders as unknown
     * @param ticketRefs the message's ticket references by ticket ID; a missing
     *                   reference renders as an unknown ticket
     * @return the plain text
     */
    public String renderPlain(String content, Map<UUID, ChatUserResponse> users,
                              Map<UUID, ChatMessageTicketRef> ticketRefs) {
        String text = content == null ? "" : content;
        String masked = maskCode(text);
        StringBuilder rendered = new StringBuilder(text.length());
        Matcher matcher = ANY_TOKEN_PATTERN.matcher(masked);
        int position = 0;
        while (matcher.find()) {
            rendered.append(text, position, matcher.start());
            rendered.append(matcher.group(1) != null
                    ? renderUser(matcher.group(1), users)
                    : renderTicket(UUID.fromString(matcher.group(2)), ticketRefs));
            position = matcher.end();
        }
        rendered.append(text, position, text.length());
        return rendered.toString();
    }

    /**
     * Cuts a text to at most maxLength characters and appends the suffix when
     * it was cut. A surrogate pair is never split.
     *
     * @param text      the text; null reads as empty
     * @param maxLength the most characters kept
     * @param suffix    appended after a cut, for example an ellipsis; may be empty
     * @return the text, or its cut start with the suffix
     */
    public String truncate(String text, int maxLength, String suffix) {
        String value = text == null ? "" : text;
        if (value.length() <= maxLength) {
            return value;
        }
        int end = maxLength;
        if (end > 0 && Character.isHighSurrogate(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end) + suffix;
    }

    /**
     * Replaces fenced code blocks and then inline code spans with spaces. A
     * fence without a closing fence runs to the end of the text, as the client
     * renders it.
     *
     * @param content message text; null reads as empty
     * @return the masked text, as long as the original
     */
    public String maskCode(String content) {
        String text = content == null ? "" : content;
        return mask(mask(text, ChatConstant.FENCED_CODE_PATTERN), ChatConstant.INLINE_CODE_PATTERN);
    }

    private String maskTokens(String text) {
        return mask(text, ANY_TOKEN_PATTERN);
    }

    private static String mask(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return text;
        }
        char[] characters = text.toCharArray();
        do {
            for (int index = matcher.start(); index < matcher.end(); index++) {
                characters[index] = MASK_CHARACTER;
            }
        } while (matcher.find());
        return new String(characters);
    }

    private static String renderUser(String target, Map<UUID, ChatUserResponse> users) {
        if (ChatConstant.MENTION_EVERYONE.equals(target) || ChatConstant.MENTION_HERE.equals(target)) {
            return ChatConstant.RENDERED_MENTION_PREFIX + target;
        }
        ChatUserResponse user = users.get(UUID.fromString(target));
        if (user == null || !user.member()) {
            return ChatConstant.RENDERED_UNKNOWN_MEMBER;
        }
        List<String> nameParts = new ArrayList<>();
        if (user.firstName() != null && !user.firstName().isBlank()) {
            nameParts.add(user.firstName());
        }
        if (user.lastName() != null && !user.lastName().isBlank()) {
            nameParts.add(user.lastName());
        }
        String name = nameParts.isEmpty() ? user.email() : String.join(" ", nameParts);
        return ChatConstant.RENDERED_MENTION_PREFIX + name;
    }

    private static String renderTicket(UUID ticketId, Map<UUID, ChatMessageTicketRef> ticketRefs) {
        ChatMessageTicketRef ref = ticketRefs.get(ticketId);
        if (ref == null) {
            return ChatConstant.RENDERED_UNKNOWN_TICKET;
        }
        return ref.getTicketKey() + ChatConstant.RENDERED_TICKET_SEPARATOR + ref.getTicketTitle();
    }
}
