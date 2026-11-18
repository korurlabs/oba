package com.korur.oba.apps.kaizen.chat.helper;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// 3. Java SDK
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaizen.chat.dto.response.ChatUserResponse;
import com.korur.oba.apps.kaizen.chat.entity.ChatMessage;
import com.korur.oba.apps.kaizen.chat.entity.ChatMessageTicketRef;
import com.korur.oba.apps.kaizen.chat.helper.ChatTokenParser.ChatTokens;
import com.korur.oba.apps.kaizen.chat.mock_data.ChatMocks;
import com.korur.oba.apps.kaizen.ticket.entity.Ticket;

class ChatTokenParserUnitTest {

    private final ChatTokenParser parser = new ChatTokenParser();

    @Nested
    class Parse {

        @Test
        void userEveryoneHereAndTicketTokens_areReadInOrder() {
            // Arrange
            UUID first = UUID.randomUUID();
            UUID second = UUID.randomUUID();
            UUID ticket = UUID.randomUUID();
            String content = "Hi " + ChatMocks.userToken(first) + " and " + ChatMocks.userToken(second)
                    + " <@everyone> <@here> see " + ChatMocks.ticketToken(ticket);

            // Act
            ChatTokens tokens = parser.parse(content);

            // Assert
            assertThat(tokens.userIds()).containsExactly(first, second);
            assertThat(tokens.mentionsEveryone()).isTrue();
            assertThat(tokens.mentionsHere()).isTrue();
            assertThat(tokens.ticketIds()).containsExactly(ticket);
        }

        @Test
        void repeatedTokens_countOnce() {
            UUID user = UUID.randomUUID();
            UUID ticket = UUID.randomUUID();
            String content = ChatMocks.userToken(user) + ChatMocks.userToken(user)
                    + ChatMocks.ticketToken(ticket) + ChatMocks.ticketToken(ticket);

            ChatTokens tokens = parser.parse(content);

            assertThat(tokens.userIds()).containsExactly(user);
            assertThat(tokens.ticketIds()).containsExactly(ticket);
        }

        @Test
        void malformedTokens_areIgnored() {
            ChatTokens tokens = parser.parse("<@not-a-uuid> <#123> <@Everyone> <@ here>");

            assertThat(tokens.userIds()).isEmpty();
            assertThat(tokens.ticketIds()).isEmpty();
            assertThat(tokens.mentionsEveryone()).isFalse();
            assertThat(tokens.mentionsHere()).isFalse();
        }

        @Test
        void tokensInInlineCode_areIgnored() {
            UUID user = UUID.randomUUID();

            ChatTokens tokens = parser.parse("`" + ChatMocks.userToken(user) + "` and `<@everyone>`");

            assertThat(tokens.userIds()).isEmpty();
            assertThat(tokens.mentionsEveryone()).isFalse();
        }

        @Test
        void tokensInFencedCodeWithLanguage_areIgnored() {
            UUID ticket = UUID.randomUUID();

            ChatTokens tokens = parser.parse("```java\n" + ChatMocks.ticketToken(ticket) + "\n```\n<@here>");

            assertThat(tokens.ticketIds()).isEmpty();
            assertThat(tokens.mentionsHere()).isTrue();
        }

        @Test
        void tokensAfterUnclosedFence_areIgnored() {
            UUID user = UUID.randomUUID();

            ChatTokens tokens = parser.parse("look\n```\n" + ChatMocks.userToken(user));

            assertThat(tokens.userIds()).isEmpty();
        }

        @Test
        void nullContent_hasNoTokens() {
            ChatTokens tokens = parser.parse(null);

            assertThat(tokens.userIds()).isEmpty();
            assertThat(tokens.ticketIds()).isEmpty();
        }
    }

    @Nested
    class ExtractTicketKeys {

        @Test
        void typedKeys_areReturnedAsTypedAndDistinct() {
            assertThat(parser.extractTicketKeys("Fixed acme-218 and ACME-300, also acme-218 again"))
                    .containsExactly("acme-218", "ACME-300");
        }

        @Test
        void keysInCodeAndInsideTokens_areIgnored() {
            UUID ticket = UUID.fromString("abcdef12-3456-4789-8abc-def123456789");

            assertThat(parser.extractTicketKeys("`ACME-1` ```\nFPX-2\n``` " + ChatMocks.ticketToken(ticket)))
                    .isEmpty();
        }

        @Test
        void normalizeTicketKey_upperCases() {
            assertThat(parser.normalizeTicketKey("acme-218")).isEqualTo("ACME-218");
        }
    }

    @Nested
    class RenderPlain {

        @Test
        void tokens_renderAsNamesAndTicketSnapshots() {
            // Arrange
            UUID member = UUID.randomUUID();
            UUID noName = UUID.randomUUID();
            ChatMessage message = ChatMocks.message(ChatMocks.user("Ada", "Lovelace"), UUID.randomUUID(), "");
            Ticket ticket = ChatMocks.ticket("ACME-218", "Mobile menu");
            ChatMessageTicketRef ticketRef = ChatMocks.ticketRef(message, ticket, 0);
            Map<UUID, ChatUserResponse> users = Map.of(
                    member, new ChatUserResponse(member, "Ada", "Lovelace", "ada@example.com", true),
                    noName, new ChatUserResponse(noName, null, " ", "nn@example.com", true));
            String content = ChatMocks.userToken(member) + " " + ChatMocks.userToken(noName) + " <@here> "
                    + ChatMocks.ticketToken(ticket.getId());

            // Act
            String rendered = parser.renderPlain(content, users, Map.of(ticket.getId(), ticketRef));

            // Assert
            assertThat(rendered).isEqualTo("@Ada Lovelace @nn@example.com @here ACME-218 Mobile menu");
        }

        @Test
        void formerMemberMissingUserAndMissingTicket_renderAsUnknown() {
            UUID former = UUID.randomUUID();
            UUID missing = UUID.randomUUID();
            Map<UUID, ChatUserResponse> users = Map.of(
                    former, new ChatUserResponse(former, "Old", "Member", "old@example.com", false));
            String content = ChatMocks.userToken(former) + " " + ChatMocks.userToken(missing) + " "
                    + ChatMocks.ticketToken(UUID.randomUUID());

            String rendered = parser.renderPlain(content, users, Map.of());

            assertThat(rendered).isEqualTo("@unknown member @unknown member #unknown ticket");
        }

        @Test
        void tokensInCode_stayAsWritten() {
            UUID user = UUID.randomUUID();
            String content = "`" + ChatMocks.userToken(user) + "`";

            assertThat(parser.renderPlain(content, Map.of(), Map.of())).isEqualTo(content);
        }
    }

    @Nested
    class Truncate {

        @Test
        void shortText_isKept() {
            assertThat(parser.truncate("abc", 3, "…")).isEqualTo("abc");
        }

        @Test
        void longText_isCutWithSuffix() {
            assertThat(parser.truncate("abcd", 3, "…")).isEqualTo("abc…");
        }

        @Test
        void cutInsideSurrogatePair_keepsThePairWhole() {
            // "ab" followed by one emoji of two UTF-16 units; a cut at 3 would split it.
            String text = "ab😀c";

            assertThat(parser.truncate(text, 3, "")).isEqualTo("ab");
        }

        @Test
        void nullText_isEmpty() {
            assertThat(parser.truncate(null, 3, "…")).isEmpty();
        }
    }
}
