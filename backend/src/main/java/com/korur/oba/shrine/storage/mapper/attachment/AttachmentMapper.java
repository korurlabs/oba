package com.korur.oba.shrine.storage.mapper.attachment;

// 2. Third-party
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

// 4. Internal
import com.korur.oba.connect.azure.service.BlobUrlRewriterMapper;
import com.korur.oba.shrine.storage.dto.response.attachment.AttachmentExistsResponse;
import com.korur.oba.shrine.storage.dto.response.attachment.AttachmentResponse;
import com.korur.oba.shrine.storage.entity.attachment.Attachment;

/**
 * MapStruct mapper for converting Attachment entities into transport DTOs.
 *
 * Owns all DTO construction for this domain: per the project's
 * mapper-only-DTO-creation rule, services and controllers are not allowed
 * to call AttachmentResponse / AttachmentExistsResponse constructors
 * directly, so any new conversion belongs here.
 *
 * The url field is rewritten on the way out by BlobUrlRewriterMapper so the
 * browser receives a SAS-signed URL even though the raw URL stored in the
 * database has no credentials.
 */
@Mapper(componentModel = "spring", uses = { BlobUrlRewriterMapper.class })
public interface AttachmentMapper {

    @Mapping(source = "createdAt", target = "createdAt")
    @Mapping(source = "url", target = "url", qualifiedByName = "rewriteBlobUrl")
    AttachmentResponse toResponse(Attachment attachment);

    /**
     * Wraps a primitive boolean as an AttachmentExistsResponse.
     *
     * Implemented as a default method rather than a generated abstract one
     * because MapStruct cannot generate a mapping method whose only input
     * is a primitive type — it raises "Can't generate mapping method with
     * primitive parameter type" at compile time. The mapper-only-DTO-creation
     * rule still requires construction to live on the mapper interface, so
     * doing it here keeps service code free of direct new-expressions while
     * working around the MapStruct limitation.
     *
     * @param exists whether a matching attachment was found
     * @return the wrapping response DTO
     */
    default AttachmentExistsResponse toExistsResponse(boolean exists) {
        return new AttachmentExistsResponse(exists);
    }
}
