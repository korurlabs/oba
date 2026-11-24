package com.korur.oba.apps.kazan.ticket.specification;

// 1. Framework-level
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// 2. Third-party

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kazan.ticket.entity.Ticket;

/**
 * Unit tests for {@link TicketSearchText}: the search the ticket list query and the
 * {@code searchMatch} flag of its response share.
 */
class TicketSearchTextUnitTest {

    private static Ticket ticket(String key, String title, String description) {
        return Ticket.builder().key(key).title(title).description(description).build();
    }

    @Test
    void nullOrBlankKeyword_searchesNothing() {
        assertThat(TicketSearchText.of(null)).isNull();
        assertThat(TicketSearchText.of("   ")).isNull();
    }

    @Test
    void matches_titleDescriptionOrKey_ignoringCase() {
        TicketSearchText search = TicketSearchText.of("NUL char");

        assertThat(search.matches(ticket("KAGI-3", "A nul character fails the upload", null))).isTrue();
        assertThat(search.matches(ticket("KAGI-4", "Progress bar", "<p>Rows with a NUL CHAR</p>"))).isTrue();
        assertThat(search.matches(ticket("KAGI-2", "Row skip reasons", "<p>Too long</p>"))).isFalse();
        assertThat(TicketSearchText.of("kagi-3").matches(ticket("KAGI-3", "Anything", null))).isTrue();
    }

    @Test
    void wildcardCharacters_matchOnlyThemselves() {
        TicketSearchText search = TicketSearchText.of("50%_off");

        assertThat(search.likePattern()).isEqualTo("%50\\%\\_off%");
        assertThat(search.matches(ticket("A-1", "Sale: 50%_off today", null))).isTrue();
        assertThat(search.matches(ticket("A-2", "Sale: 500 off-season", null))).isFalse();
    }

    @Test
    void likePattern_escapesTheEscapeCharacter() {
        assertThat(TicketSearchText.of("a\\b").likePattern()).isEqualTo("%a\\\\b%");
    }
}
