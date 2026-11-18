package com.korur.oba.apps.kazan.dailyupdate.helper;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party

// 3. Java SDK
import java.util.ArrayList;
import java.util.List;

// 4. Internal
import com.korur.oba.apps.kazan.dailyupdate.constant.DailyUpdateConstant;
import com.korur.oba.apps.kazan.dailyupdate.entity.DailyUpdateTicket;

/**
 * Renders a daily update as the plain text the team pastes into Teams, in the
 * team's four-part format:
 *
 * 1. Which ticket am I working on?
 *    - ACME-218: Mobile Menu Figma Design
 *      - each non-blank line of the line's note
 * 2. What will I focus on today?
 *    - each non-blank line of focus, or None
 * 3. Percentage status of ticket(s)
 *    - ACME-218: 30%
 * 4. Any questions to my colleagues?
 *    - each non-blank line of questions, or None
 *
 * The text is built from every line of the update, so every reader copies the
 * same text. Lines appear in the order of the given list, which callers keep in
 * position order. Lines are joined with a plain newline and the text has no
 * trailing newline.
 */
@Component
public class DailyUpdateSummaryRenderer {

    /**
     * Renders the summary text.
     *
     * @param items     ticket lines in display order
     * @param focus     focus text, nullable
     * @param questions questions text, nullable
     * @return the summary text
     */
    public String render(List<DailyUpdateTicket> items, String focus, String questions) {
        List<String> lines = new ArrayList<>();

        lines.add(DailyUpdateConstant.SUMMARY_HEADING_TICKETS);
        if (items.isEmpty()) {
            lines.add(DailyUpdateConstant.SUMMARY_BULLET + DailyUpdateConstant.SUMMARY_NONE);
        }
        for (DailyUpdateTicket item : items) {
            lines.add(DailyUpdateConstant.SUMMARY_BULLET + item.getTicketKey()
                    + DailyUpdateConstant.SUMMARY_KEY_SEPARATOR + item.getTicketTitle());
            for (String noteLine : nonBlankLines(item.getNote())) {
                lines.add(DailyUpdateConstant.SUMMARY_SUB_BULLET + noteLine);
            }
        }

        lines.add(DailyUpdateConstant.SUMMARY_HEADING_FOCUS);
        addTextBullets(lines, focus);

        lines.add(DailyUpdateConstant.SUMMARY_HEADING_PERCENTAGES);
        if (items.isEmpty()) {
            lines.add(DailyUpdateConstant.SUMMARY_BULLET + DailyUpdateConstant.SUMMARY_NONE);
        }
        for (DailyUpdateTicket item : items) {
            lines.add(DailyUpdateConstant.SUMMARY_BULLET + item.getTicketKey()
                    + DailyUpdateConstant.SUMMARY_KEY_SEPARATOR + item.getPercentage()
                    + DailyUpdateConstant.SUMMARY_PERCENT_SUFFIX);
        }

        lines.add(DailyUpdateConstant.SUMMARY_HEADING_QUESTIONS);
        addTextBullets(lines, questions);

        return String.join(DailyUpdateConstant.SUMMARY_LINE_BREAK, lines);
    }

    /**
     * Adds one bullet per non-blank line of a free text, or a None bullet when
     * the text has none.
     */
    private static void addTextBullets(List<String> lines, String text) {
        List<String> textLines = nonBlankLines(text);
        if (textLines.isEmpty()) {
            lines.add(DailyUpdateConstant.SUMMARY_BULLET + DailyUpdateConstant.SUMMARY_NONE);
            return;
        }
        for (String textLine : textLines) {
            lines.add(DailyUpdateConstant.SUMMARY_BULLET + textLine);
        }
    }

    /**
     * Splits a text at any line break and keeps its non-blank lines, stripped
     * of surrounding whitespace, so indentation typed by the author cannot
     * break the bullet layout. Null gives no lines.
     */
    private static List<String> nonBlankLines(String text) {
        if (text == null) {
            return List.of();
        }
        return text.lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .toList();
    }
}
