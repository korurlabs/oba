package com.korur.oba.apps.kazan.cyberscore.dto.request;

// 1. Framework-level

// 2. Third-party
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kazan.cyberscore.constant.CyberscoreConstant;

/**
 * Query parameters of the cyberscore trend. The window is clamped rather than
 * refused, so any whole number gives a trend.
 */
@Schema(description = "Query parameters of the cyberscore trend")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class CyberscoreTrendQueryRequest {

    @Schema(description = "Window length in days, clamped to 1 to 365; 30 when absent", nullable = true,
            example = "30")
    private Integer days = CyberscoreConstant.DEFAULT_TREND_DAYS;
}
