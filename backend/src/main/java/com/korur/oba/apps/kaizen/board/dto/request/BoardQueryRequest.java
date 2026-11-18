package com.korur.oba.apps.kaizen.board.dto.request;

// 1. Framework-level

// 2. Third-party
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK

// 4. Internal
import com.korur.oba.dojo.dto.QueryRequest;

/**
 * Query request for filtering and paginating boards.
 * <p>
 * Extends QueryRequest to inherit pagination fields (page, amount, sortBy,
 * sortDirection).
 * Adds board-specific filters for search.
 */
@Getter
@Setter
@NoArgsConstructor
public class BoardQueryRequest extends QueryRequest {

    /**
     * Search keyword to match against board name or description.
     * Null or blank means no search filter.
     */
    @Schema(description = "Search keyword", nullable = true)
    private String search;

    /**
     * Whether to include child boards.
     */
    @Schema(description = "Include child boards", nullable = true)
    private Boolean includeChildren;
}
