package com.korur.oba.dojo.dto;

import com.korur.oba.dojo.exception.internal.infrastructure.ErrorResponse;

import io.swagger.v3.oas.annotations.media.Schema;

public record CustomResponse<T>(
        @Schema(description = "Indicates if the request was successful", example = "true")
        boolean success,

        @Schema(description = "Message describing the result", example = "Operation completed successfully")
        String message,

        @Schema(description = "Response data, can be null", nullable = true)
        T data,

        @Schema(description = "Pagination details, can be null", nullable = true)
        PaginationDetailsResponse pagination,

        @Schema(description = "Error details, can be null", nullable = true)
        ErrorResponse error
) {}
