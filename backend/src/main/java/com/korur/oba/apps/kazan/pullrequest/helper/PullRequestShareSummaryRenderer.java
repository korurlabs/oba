package com.korur.oba.apps.kazan.pullrequest.helper;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party

// 3. Java SDK
import java.util.List;

// 4. Internal
import com.korur.oba.apps.kazan.pullrequest.constant.PullRequestShareConstant;
import com.korur.oba.apps.kazan.pullrequest.enums.PullRequestShareStatus;

/**
 * Renders a pull request share as the plain text the team pastes into Teams:
 *
 * ACME-241: Mobile menu
 * https://github.com/korurlabs/fabrikam/pull/379
 * Status: Merged for testing in prod
 *
 * The first line is the ticket key and the title, or the title alone when the
 * pull request names no ticket. The status line is the status label, followed
 * by the note when there is one. The text is the same for every reader, so the
 * ticket key is written whether or not the reader can open the ticket. Lines
 * are joined with a plain newline and the text has no trailing newline.
 */
@Component
public class PullRequestShareSummaryRenderer {

    /**
     * Renders the summary text.
     *
     * @param ticketKey the share's upper-case ticket key, nullable
     * @param title     the pull request title as stored on the share
     * @param url       the github.com link of the pull request
     * @param status    the team's status
     * @param note      the share's note, nullable
     * @return the summary text
     */
    public String render(String ticketKey, String title, String url, PullRequestShareStatus status, String note) {
        String heading = ticketKey == null
                ? title
                : ticketKey + PullRequestShareConstant.SUMMARY_KEY_SEPARATOR + title;
        String statusLine = PullRequestShareConstant.SUMMARY_STATUS_PREFIX
                + PullRequestShareConstant.STATUS_LABELS.get(status);
        if (note != null && !note.isBlank()) {
            statusLine = statusLine + PullRequestShareConstant.SUMMARY_NOTE_SEPARATOR + note.strip();
        }
        return String.join(PullRequestShareConstant.SUMMARY_LINE_BREAK, List.of(heading, url, statusLine));
    }
}
