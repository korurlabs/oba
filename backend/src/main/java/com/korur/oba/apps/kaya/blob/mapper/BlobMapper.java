package com.korur.oba.apps.kaya.blob.mapper;

// 1. Framework-level

// 2. Third-party
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

// 3. Java SDK

// 4. Internal
import com.korur.oba.apps.kaya.blob.dto.response.BlobUploadResponse;

/**
 * Builds the {@link BlobUploadResponse} of an upload.
 */
@Mapper(componentModel = "spring")
public interface BlobMapper {

    @Mapping(target = "sha256", source = "sha256")
    @Mapping(target = "size", source = "size")
    @Mapping(target = "stored", source = "stored")
    BlobUploadResponse toUploadResponse(String sha256, int size, boolean stored);
}
