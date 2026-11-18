package com.korur.oba.apps.kaizen.dailyupdate.helper;

// 1. Framework-level
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// 3. Java SDK
import java.util.List;

// 4. Internal
import com.korur.oba.apps.kaizen.dailyupdate.entity.DailyUpdateTicket;
import com.korur.oba.apps.kaizen.dailyupdate.mock_data.DailyUpdateMocks;

class DailyUpdateSummaryRendererUnitTest {

    private final DailyUpdateSummaryRenderer renderer = new DailyUpdateSummaryRenderer();

    private static DailyUpdateTicket line(String key, String title, int percentage, String note) {
        DailyUpdateTicket item = DailyUpdateMocks.ticketlessItem(key, title, percentage, 0);
        item.setNote(note);
        return item;
    }

    @Nested
    class Render {

        @Test
        void oneTicketWithoutNoteEmptyFocusAndQuestions_rendersFourPartFormatWithNone() {
            // Arrange
            List<DailyUpdateTicket> items = List.of(line("ACME-218", "Mobile Menu Figma Design", 30, null));

            // Act
            String text = renderer.render(items, null, null);

            // Assert
            assertThat(text).isEqualTo(String.join("\n",
                    "1. Which ticket am I working on?",
                    "   - ACME-218: Mobile Menu Figma Design",
                    "2. What will I focus on today?",
                    "   - None",
                    "3. Percentage status of ticket(s)",
                    "   - ACME-218: 30%",
                    "4. Any questions to my colleagues?",
                    "   - None"));
        }

        @Test
        void twoTicketsWithNotes_rendersNotesAsSubBulletsInListOrder() {
            // Arrange
            List<DailyUpdateTicket> items = List.of(
                    line("ACME-218", "Mobile Menu Figma Design", 30, "Waiting for the final icons"),
                    line("ACME-300", "Checkout flow", 100, null));

            // Act
            String text = renderer.render(items, "Finish the menu", "Who reviews the icons?");

            // Assert
            assertThat(text).isEqualTo(String.join("\n",
                    "1. Which ticket am I working on?",
                    "   - ACME-218: Mobile Menu Figma Design",
                    "     - Waiting for the final icons",
                    "   - ACME-300: Checkout flow",
                    "2. What will I focus on today?",
                    "   - Finish the menu",
                    "3. Percentage status of ticket(s)",
                    "   - ACME-218: 30%",
                    "   - ACME-300: 100%",
                    "4. Any questions to my colleagues?",
                    "   - Who reviews the icons?"));
        }

        @Test
        void multiLineFocusQuestionsAndNote_everyNonBlankLineBecomesOwnBulletStripped() {
            // Arrange
            List<DailyUpdateTicket> items = List.of(
                    line("ACME-1", "Title", 5, "first\r\n\n   second  \n"));

            // Act
            String text = renderer.render(items, "  one\n\n two \r\n three", "q1\nq2");

            // Assert
            assertThat(text).isEqualTo(String.join("\n",
                    "1. Which ticket am I working on?",
                    "   - ACME-1: Title",
                    "     - first",
                    "     - second",
                    "2. What will I focus on today?",
                    "   - one",
                    "   - two",
                    "   - three",
                    "3. Percentage status of ticket(s)",
                    "   - ACME-1: 5%",
                    "4. Any questions to my colleagues?",
                    "   - q1",
                    "   - q2"));
        }

        @Test
        void blankFocusAndQuestions_renderNone() {
            // Arrange
            List<DailyUpdateTicket> items = List.of(line("ACME-1", "Title", 0, "  \n "));

            // Act
            String text = renderer.render(items, "  \n  ", "");

            // Assert
            assertThat(text).doesNotEndWith("\n");
            assertThat(text).contains("2. What will I focus on today?\n   - None\n3.");
            assertThat(text).endsWith("4. Any questions to my colleagues?\n   - None");
            assertThat(text).doesNotContain("     - ");
        }

        @Test
        void noItems_rendersNoneUnderTicketsAndPercentages() {
            // Act
            String text = renderer.render(List.of(), "focus", null);

            // Assert
            assertThat(text).isEqualTo(String.join("\n",
                    "1. Which ticket am I working on?",
                    "   - None",
                    "2. What will I focus on today?",
                    "   - focus",
                    "3. Percentage status of ticket(s)",
                    "   - None",
                    "4. Any questions to my colleagues?",
                    "   - None"));
        }
    }
}
