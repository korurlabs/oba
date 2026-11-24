package com.korur.oba.apps.kazan.ticket.specification;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.Locale;

// 4. Internal
import com.korur.oba.apps.kazan.ticket.entity.Ticket;

/**
 * The search text of the ticket lists. A ticket matches when its title, description or key
 * contains the text, ignoring case. The text is literal: {@code %} and {@code _} match
 * themselves.
 * <p>
 * The list query ({@link TicketSpecification}) and the {@code searchMatch} flag of the list
 * response both use this class, so the sub-tickets the response marks are the ones that
 * matched in the query.
 */
public final class TicketSearchText {

    /** The escape character of {@link #likePattern()}. */
    public static final char LIKE_ESCAPE = '\\';

    private final String text;

    private TicketSearchText(String text) {
        this.text = text;
    }

    /**
     * The search for {@code keyword}, or null when it is null or blank and nothing is searched.
     */
    public static TicketSearchText of(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return new TicketSearchText(keyword.toLowerCase(Locale.ROOT));
    }

    /**
     * The LIKE pattern for a lowercased column, escaped with {@link #LIKE_ESCAPE}.
     */
    public String likePattern() {
        String escaped = text
                .replace(String.valueOf(LIKE_ESCAPE), String.valueOf(LIKE_ESCAPE) + LIKE_ESCAPE)
                .replace("%", LIKE_ESCAPE + "%")
                .replace("_", LIKE_ESCAPE + "_");
        return "%" + escaped + "%";
    }

    /**
     * Whether the ticket's own title, description or key contains the text.
     */
    public boolean matches(Ticket ticket) {
        return contains(ticket.getTitle()) || contains(ticket.getDescription()) || contains(ticket.getKey());
    }

    private boolean contains(String value) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(text);
    }
}
