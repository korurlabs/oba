package com.korur.oba.apps.kaya.version.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.Mapper;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaya.version.dto.response.ItemOverrideResponse;
import com.korur.oba.apps.kaya.version.entity.ItemOverride;

@Mapper(componentModel = "spring")
public interface ItemOverrideMapper {

    ItemOverrideResponse toResponse(ItemOverride override);
}
