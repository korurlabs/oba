package com.korur.oba.shrine.legalentity.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;

// 3. Java SDK

// 4. Internal
import com.korur.oba.shrine.legalentity.dto.response.LegalEntityDetailResponse;
import com.korur.oba.shrine.legalentity.dto.response.LegalEntityListResponse;
import com.korur.oba.shrine.legalentity.entity.LegalEntity;

/**
 * Maps a {@link LegalEntity} to a {@link LegalEntityListResponse} or a
 * {@link LegalEntityDetailResponse}. MapStruct generates the implementation as a Spring bean
 * with constructor injection.
 *
 * There is no request mapping: create and update write every field trimmed through one helper
 * of {@link com.korur.oba.shrine.legalentity.service.impl.LegalEntityServiceImpl}, so both
 * store the same columns the same way. A legal entity has no association to another entity
 * (its organization is a plain id), so no MapperHelper is needed.
 */
@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface LegalEntityMapper {

    /**
     * Maps a legal entity to a list item.
     *
     * @param legalEntity the stored legal entity
     * @return the list item
     */
    LegalEntityListResponse toListResponse(LegalEntity legalEntity);

    /**
     * Maps a legal entity to the detail response with every field.
     *
     * @param legalEntity the stored legal entity
     * @return the detail response
     */
    LegalEntityDetailResponse toDetailResponse(LegalEntity legalEntity);
}
