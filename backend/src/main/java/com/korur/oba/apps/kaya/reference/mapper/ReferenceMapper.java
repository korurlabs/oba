package com.korur.oba.apps.kaya.reference.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaya.reference.dto.response.ReferenceDetailResponse;
import com.korur.oba.apps.kaya.reference.entity.Reference;

/**
 * Maps a {@link Reference} with its size and its text or base64 content to a {@link ReferenceDetailResponse}.
 */
@Mapper(componentModel = "spring")
public interface ReferenceMapper {

    @Mapping(target = "id", source = "reference.id")
    @Mapping(target = "versionId", source = "reference.versionId")
    @Mapping(target = "name", source = "reference.name")
    @Mapping(target = "mediaType", source = "reference.mediaType")
    @Mapping(target = "sizeBytes", source = "sizeBytes")
    @Mapping(target = "content", source = "content")
    @Mapping(target = "contentBase64", source = "contentBase64")
    @Mapping(target = "createdAt", source = "reference.createdAt")
    @Mapping(target = "updatedAt", source = "reference.updatedAt")
    ReferenceDetailResponse toDetailResponse(Reference reference, Integer sizeBytes, String content,
                                             String contentBase64);
}
