package com.korur.oba.dojo.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record PaginationDetailsResponse(
        @Schema(description = "Total number of elements", example = "100")
        long totalElements,

        @Schema(description = "Total number of pages", example = "10")
        int totalPages,

        @Schema(description = "Current page number (1-based)", example = "1")
        int currentPage,

        @Schema(description = "Page size", example = "10")
        int pageSize,

        @Schema(description = "Is first page", example = "true")
        boolean first,

        @Schema(description = "Is last page", example = "false")
        boolean last,

        @Schema(description = "Is empty", example = "false")
        boolean empty
) {}
