package com.korur.oba.apps.kaizen.pullrequest.helper;

// 1. Framework-level
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// 4. Internal
import com.korur.oba.apps.kaizen.pullrequest.enums.PullRequestShareStatus;

class PullRequestShareSummaryRendererUnitTest {

    private static final String URL = "https://github.com/senseylabs/fabrikam/pull/379";

    private final PullRequestShareSummaryRenderer renderer = new PullRequestShareSummaryRenderer();

    @Test
    void render_keyAndNote_writesKeyedHeadingUrlAndStatusWithNote() {
        // Act
        String text = renderer.render("ACME-241", "Mobile menu", URL, PullRequestShareStatus.MERGED,
                "for testing in prod");

        // Assert
        assertThat(text).isEqualTo("ACME-241: Mobile menu\n" + URL + "\nStatus: Merged for testing in prod");
    }

    @Test
    void render_noKeyNoNote_writesTitleAloneAndBareStatus() {
        // Act
        String text = renderer.render(null, "Mobile menu", URL, PullRequestShareStatus.IN_REVIEW, null);

        // Assert
        assertThat(text).isEqualTo("Mobile menu\n" + URL + "\nStatus: In Review");
    }

    @Test
    void render_blankNote_isTreatedAsNoNote() {
        // Act
        String text = renderer.render("ACME-241", "Mobile menu", URL, PullRequestShareStatus.DRAFT, "   ");

        // Assert
        assertThat(text).endsWith("Status: Draft");
    }

    @Test
    void render_paddedNote_isStrippedAndTextHasNoTrailingNewline() {
        // Act
        String text = renderer.render(null, "Mobile menu", URL, PullRequestShareStatus.DISCUSS, "  needs input  ");

        // Assert
        assertThat(text).endsWith("Status: Discuss needs input").doesNotEndWith("\n");
    }

    @Test
    void render_everyStatus_hasALabel() {
        // Act and Assert
        for (PullRequestShareStatus status : PullRequestShareStatus.values()) {
            assertThat(renderer.render(null, "T", URL, status, null)).contains("Status: ");
            assertThat(renderer.render(null, "T", URL, status, null)).doesNotContain("null");
        }
    }
}
