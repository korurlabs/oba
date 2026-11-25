package com.korur.oba.apps.kazan.cyberscore.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;

// 3. Java SDK
import java.util.List;

// 4. Internal
import com.korur.oba.apps.kazan.cyberscore.dto.response.CyberscoreResponse;
import com.korur.oba.apps.kazan.cyberscore.dto.response.CyberscoreTrendPointResponse;
import com.korur.oba.apps.kazan.cyberscore.entity.Cyberscore;
import com.korur.oba.apps.kazan.cyberscore.service.CyberscoreTrendPoint;

/**
 * Maps the cyberscore row and trend points to their response DTOs.
 */
@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface CyberscoreMapper {

    /**
     * Maps the score row to its response. The scorable check count is not
     * stored on the row, so the caller counts it.
     */
    CyberscoreResponse toResponse(Cyberscore cyberscore, int scorableCheckCount);

    /**
     * Maps one trend point to its response.
     */
    CyberscoreTrendPointResponse toTrendPointResponse(CyberscoreTrendPoint trendPoint);

    /**
     * Maps trend points to their responses, keeping their order.
     */
    List<CyberscoreTrendPointResponse> toTrendPointResponses(List<CyberscoreTrendPoint> trendPoints);
}
