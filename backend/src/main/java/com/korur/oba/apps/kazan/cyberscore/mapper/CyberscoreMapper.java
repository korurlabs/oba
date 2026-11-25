package com.korur.oba.apps.kazan.cyberscore.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

// 3. Java SDK
import java.util.List;

// 4. Internal
import com.korur.oba.apps.kazan.cyberscore.dto.derived.CyberscoreCalculation;
import com.korur.oba.apps.kazan.cyberscore.dto.derived.CyberscoreTrendPoint;
import com.korur.oba.apps.kazan.cyberscore.dto.response.CyberscoreResponse;
import com.korur.oba.apps.kazan.cyberscore.dto.response.CyberscoreTrendPointResponse;
import com.korur.oba.apps.kazan.cyberscore.entity.Cyberscore;

/**
 * Maps the cyberscore row and trend points to their response DTOs.
 */
@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface CyberscoreMapper {

    /**
     * Maps the score row to its response. Percentage and level come from the
     * row; the percentage per category is not stored, so it comes from a
     * calculation over the current checks, and the caller counts the scorable
     * checks.
     */
    @Mapping(target = "scorePercentage", source = "cyberscore.scorePercentage")
    @Mapping(target = "scoreLevel", source = "cyberscore.scoreLevel")
    @Mapping(target = "breakdownByCategory", source = "calculation.breakdownByCategory")
    @Mapping(target = "lastCalculatedAt", source = "cyberscore.lastCalculatedAt")
    CyberscoreResponse toResponse(Cyberscore cyberscore, CyberscoreCalculation calculation, int scorableCheckCount);

    /**
     * Maps one trend point to its response.
     */
    CyberscoreTrendPointResponse toTrendPointResponse(CyberscoreTrendPoint trendPoint);

    /**
     * Maps trend points to their responses, keeping their order.
     */
    List<CyberscoreTrendPointResponse> toTrendPointResponses(List<CyberscoreTrendPoint> trendPoints);
}
