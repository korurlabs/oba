package com.korur.oba.dojo.exception.internal.infrastructure;

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;

// 3. Java SDK
import java.util.Map;


@Schema(description = "Error response details")
public record ErrorResponse(
        @Schema(description = "Error code", example = "GEN_001")
        String code,

        @Schema(description = "Detailed error message", example = "An unexpected internal server error occurred.")
        String message,

        @Schema(description = "Detailed error values", example = "{}")
        Map<String, Object> errorValues
) {

}
