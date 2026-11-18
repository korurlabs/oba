package com.korur.oba.apps.kaizen.dailyupdate.constant;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;

// 4. Internal
import com.korur.oba.shrine.organization.enums.OrganizationMemberRole;

/**
 * Field names, limits and summary text constants for the daily update domain.
 */
public final class DailyUpdateConstant {

    private DailyUpdateConstant() {
        // Private constructor to prevent instantiation
    }

    /**
     * Field name constant for daily update ID.
     */
    public static final String FIELD_DAILY_UPDATE_ID = "dailyUpdateId";

    /**
     * Field name constant for ticket ID.
     */
    public static final String FIELD_TICKET_ID = "ticketId";

    /**
     * Entity field name of the creation time, by which the feed is sorted.
     */
    public static final String FIELD_CREATED_AT = "createdAt";

    /**
     * Field name constant for author ID.
     */
    public static final String FIELD_AUTHOR_ID = "authorId";

    /**
     * Field name constant for the list window start.
     */
    public static final String FIELD_FROM = "from";

    /**
     * Field name constant for the list window end.
     */
    public static final String FIELD_TO = "to";

    /**
     * Field name constant for the reported day of the not-posted list.
     */
    public static final String FIELD_DATE = "date";

    /**
     * Field name constant for the reporter's current day, against which the reported day is checked.
     */
    public static final String FIELD_TODAY = "today";

    /**
     * Field name constant for the ticket lines of a request.
     */
    public static final String FIELD_TICKETS = "tickets";

    /**
     * Field name constant for the percentage of a ticket line.
     */
    public static final String FIELD_PERCENTAGE = "percentage";

    /**
     * Field name constant for the note of a ticket line.
     */
    public static final String FIELD_NOTE = "note";

    /**
     * Field name constant for the focus text.
     */
    public static final String FIELD_FOCUS = "focus";

    /**
     * Field name constant for the questions text.
     */
    public static final String FIELD_QUESTIONS = "questions";

    /**
     * Most ticket lines one update may hold, on create and edit.
     */
    public static final int MAX_TICKETS = 30;

    /**
     * Most active tickets suggested for a new update; equal to MAX_TICKETS so
     * that every suggestion fits in one update.
     */
    public static final int MAX_SUGGESTED_TICKETS = MAX_TICKETS;

    /**
     * Longest note of one ticket line, in characters.
     */
    public static final int MAX_NOTE_LENGTH = 500;

    /**
     * Longest focus or questions text, in characters.
     */
    public static final int MAX_TEXT_LENGTH = 2000;

    /**
     * Lowest percentage of a ticket line.
     */
    public static final int MIN_PERCENTAGE = 0;

    /**
     * Highest percentage of a ticket line.
     */
    public static final int MAX_PERCENTAGE = 100;

    /**
     * Heading of the summary section that lists the ticket lines and their notes.
     */
    public static final String SUMMARY_HEADING_TICKETS = "1. Which ticket am I working on?";

    /**
     * Heading of the summary section that holds the focus text.
     */
    public static final String SUMMARY_HEADING_FOCUS = "2. What will I focus on today?";

    /**
     * Heading of the summary section that lists the percentage of each ticket.
     */
    public static final String SUMMARY_HEADING_PERCENTAGES = "3. Percentage status of ticket(s)";

    /**
     * Heading of the summary section that holds the questions text.
     */
    public static final String SUMMARY_HEADING_QUESTIONS = "4. Any questions to my colleagues?";

    /**
     * Bullet text of a summary section with nothing in it.
     */
    public static final String SUMMARY_NONE = "None";

    /**
     * Prefix of a bullet directly under a summary heading.
     */
    public static final String SUMMARY_BULLET = "   - ";

    /**
     * Prefix of a note bullet under a ticket bullet.
     */
    public static final String SUMMARY_SUB_BULLET = "     - ";

    /**
     * Separator between a ticket key and its title or percentage in a bullet.
     */
    public static final String SUMMARY_KEY_SEPARATOR = ": ";

    /**
     * Suffix of a percentage in the summary.
     */
    public static final String SUMMARY_PERCENT_SUFFIX = "%";

    /**
     * Line break between the lines of the summary; a plain newline, so the text
     * pastes the same on every platform.
     */
    public static final String SUMMARY_LINE_BREAK = "\n";

    /**
     * Time of day in {@link com.korur.oba.dojo.time.BusinessCalendar#ZONE} by
     * which a member's first update of the day must be posted; an update at
     * this time or later is late.
     */
    public static final LocalTime POSTING_DEADLINE = LocalTime.of(10, 30);

    /**
     * Organization roles expected to post an update every workday. GUEST is
     * left out because guests take no part in the team's daily work.
     */
    public static final Set<OrganizationMemberRole> EXPECTED_POSTER_ROLES = Set.of(
            OrganizationMemberRole.OWNER,
            OrganizationMemberRole.ADMIN,
            OrganizationMemberRole.MEMBER);

    /**
     * Field name constant for the nudged member.
     */
    public static final String FIELD_RECIPIENT_ID = "recipientId";

    /**
     * Field name constant for the business day of a nudge.
     */
    public static final String FIELD_DAY = "day";

    /**
     * Unique key of the nudges table on (organization_id, recipient_id, day):
     * the database guard of one nudge per recipient, organization and day.
     */
    public static final String NUDGE_UNIQUE_CONSTRAINT = "daily_update_nudges_organization_id_recipient_id_day_uk";

    /**
     * Separator between a sender's first and last name in the nudge push.
     */
    public static final String SENDER_NAME_SEPARATOR = " ";

    /**
     * Format of the posting deadline in the nudge push, 24-hour "10:30", the
     * way the team reads Istanbul time.
     */
    public static final DateTimeFormatter NUDGE_DEADLINE_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
}
