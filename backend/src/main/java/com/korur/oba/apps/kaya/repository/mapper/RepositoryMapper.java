package com.korur.oba.apps.kaya.repository.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;

// 3. Java SDK
import java.util.List;

// 4. Internal
import com.korur.oba.apps.kaya.repository.dto.request.RepositoryUpdateRequest;
import com.korur.oba.apps.kaya.repository.dto.response.RepositoryResponse;
import com.korur.oba.apps.kaya.repository.entity.Repository;

/**
 * Maps repositories to responses and applies repository updates.
 */
@Mapper(componentModel = "spring")
public interface RepositoryMapper {

    RepositoryResponse toResponse(Repository repository);

    List<RepositoryResponse> toResponses(List<Repository> repositories);

    /**
     * Applies the given name, ticket URL template and status; a null field keeps the current value. The name is trimmed, and an empty ticket URL template removes the template.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "organizationId", ignore = true)
    @Mapping(target = "remoteUrl", ignore = true)
    @Mapping(target = "name", source = "name", qualifiedByName = "strip")
    @Mapping(target = "ticketUrlTemplate", source = "ticketUrlTemplate", qualifiedByName = "emptyToNull")
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(@MappingTarget Repository repository, RepositoryUpdateRequest request);

    @Named("strip")
    default String strip(String value) {
        return value.strip();
    }

    @Named("emptyToNull")
    default String emptyToNull(String value) {
        return value.isEmpty() ? null : value;
    }
}
