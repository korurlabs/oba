package com.korur.oba.apps.otak.icp.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.otak.icp.dto.request.IcpCreateRequest;
import com.korur.oba.apps.otak.icp.dto.request.IcpUpdateRequest;
import com.korur.oba.apps.otak.icp.dto.response.IcpDetailResponse;
import com.korur.oba.apps.otak.icp.dto.response.IcpListResponse;
import com.korur.oba.apps.otak.icp.entity.Icp;

/**
 * Maps ICP entities to DTOs and requests to entities. An ICP has no association, so the
 * mapper needs no helper.
 */
@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface IcpMapper {

    /**
     * Maps an ICP create request to a new entity. The service sets the organization from the
     * checked request header.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "organizationId", ignore = true)
    Icp toEntity(IcpCreateRequest request);

    /**
     * Writes every field of an update request onto the ICP; null clears an optional
     * field.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "organizationId", ignore = true)
    void updateEntity(@MappingTarget Icp icp, IcpUpdateRequest request);

    /**
     * Maps an ICP to a list item.
     */
    IcpListResponse toListResponse(Icp icp);

    /**
     * Maps an ICP to the detail response.
     */
    IcpDetailResponse toDetailResponse(Icp icp);
}
