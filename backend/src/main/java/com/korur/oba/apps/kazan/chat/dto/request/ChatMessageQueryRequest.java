package com.korur.oba.apps.kazan.chat.dto.request;

// 1. Framework-level
import org.springframework.format.annotation.DateTimeFormat;

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 3. Java SDK
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kazan.chat.constant.ChatConstant;
import com.korur.oba.apps.kazan.pullrequest.enums.PullRequestShareStatus;

/**
 * Keyset query of a channel or thread page. At most one cursor is given; with
 * none the newest page is returned. It does not extend {@link com.korur.oba.dojo.dto.QueryRequest}, because a
 * chat page is keyed by a message, not by a page number, so a message posted
 * while reading never shifts the next page.
 */
@Getter
@Setter
@NoArgsConstructor
public class ChatMessageQueryRequest {

    /**
     * Channel to read. Required for a channel page; a thread page takes its
     * channel from the thread and ignores it.
     */
    @Schema(description = "Channel to read; required for a channel page, ignored for a thread page", nullable = true,
            example = "3b4c5d6e-7f80-4a91-b2c3-d4e5f6a7b8c9")
    private UUID channelId;

    /**
     * Return messages strictly older than this message.
     */
    @Schema(description = "Return messages strictly older than this message", nullable = true,
            example = "8f1c2d3e-4a5b-4c6d-9e7f-0a1b2c3d4e5f")
    private UUID before;

    /**
     * Return messages strictly newer than this message.
     */
    @Schema(description = "Return messages strictly newer than this message", nullable = true,
            example = "8f1c2d3e-4a5b-4c6d-9e7f-0a1b2c3d4e5f")
    private UUID after;

    /**
     * Return half a page on each side of this message, the message included.
     */
    @Schema(description = "Return half a page on each side of this message, the message included", nullable = true,
            example = "8f1c2d3e-4a5b-4c6d-9e7f-0a1b2c3d4e5f")
    private UUID around;

    /**
     * Keep only messages posted on this day in Europe/Istanbul, the day the
     * daily updates report on. Applies to a channel page; a thread page
     * ignores it.
     */
    @Schema(description = "Keep only messages posted on this day (YYYY-MM-DD) in Europe/Istanbul; channel page only",
            nullable = true, example = "2026-11-03")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate day;

    /**
     * Keep only messages of this author. Applies to a channel page; a thread
     * page ignores it.
     */
    @Schema(description = "Keep only messages of this author; channel page only", nullable = true,
            example = "3c4d5e6f-7a8b-4c9d-8e0f-1a2b3c4d5e6f")
    private UUID authorId;

    /**
     * Keep only pull request share entries whose live share has one of these
     * statuses; plain messages and placeholders of deleted thread roots are
     * dropped. Null or empty applies no filter. Applies to a channel page; a
     * thread page ignores it.
     */
    @Schema(description = "Keep only pull request share entries whose share has one of these statuses, "
            + "comma-separated; null or empty applies no filter; channel page only", nullable = true,
            example = "IN_REVIEW")
    private List<PullRequestShareStatus> pullRequestStatuses;

    /**
     * Page size.
     */
    @Schema(description = "Page size", nullable = true, example = "50")
    @Min(value = ChatConstant.MIN_PAGE_SIZE, message = "Limit must be at least 1")
    @Max(value = ChatConstant.MAX_PAGE_SIZE, message = "Limit must be at most 50")
    private Integer limit = ChatConstant.DEFAULT_PAGE_SIZE;
}
