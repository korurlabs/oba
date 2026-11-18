package com.korur.oba.apps.kaya.reference.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.kaya.core.exception.enums.ExceptionDomainKayaError;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.apps.kaya.blob.entity.Blob;
import com.korur.oba.apps.kaya.blob.service.BlobQueryService;
import com.korur.oba.apps.kaya.reference.constant.ReferenceConstant;
import com.korur.oba.apps.kaya.reference.constant.service.ReferenceServiceLogConstant;
import com.korur.oba.apps.kaya.reference.dto.response.ReferenceDetailResponse;
import com.korur.oba.apps.kaya.reference.entity.Reference;
import com.korur.oba.apps.kaya.reference.mapper.ReferenceMapper;
import com.korur.oba.apps.kaya.reference.repository.ReferenceRepository;
import com.korur.oba.apps.kaya.reference.service.ReferenceQueryService;

/**
 * The {@link ReferenceQueryService} implementation.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReferenceQueryServiceImpl implements ReferenceQueryService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(ReferenceQueryServiceImpl.class);

    private final ReferenceRepository referenceRepository;
    private final BlobQueryService blobQueryService;
    private final ReferenceMapper referenceMapper;
    private final LoggingService loggingService;

    @Override
    public ReferenceDetailResponse getReference(UUID organizationId, UUID referenceId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, ReferenceServiceLogConstant.READING_REFERENCE,
                referenceId, organizationId);
        Reference reference = getEntityById(organizationId, referenceId);
        Blob blob = blobQueryService.getEntityById(organizationId, reference.getContentBlobId());
        String text = utf8Text(blob.getContent());
        String base64 = text == null ? Base64.getEncoder().encodeToString(blob.getContent()) : null;
        return referenceMapper.toDetailResponse(reference, blob.getSize(), text, base64);
    }

    @Override
    public Reference getEntityById(UUID organizationId, UUID referenceId) {
        // Looked up within the organization, so a reference of another organization answers the same 404 as
        // an unknown id.
        return referenceRepository.findByIdAndOrganizationId(referenceId, organizationId).orElseThrow(() -> {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, ReferenceServiceLogConstant.REFERENCE_NOT_FOUND,
                    referenceId, organizationId);
            return new NotFoundException(ExceptionDomainKayaError.NOT_VERSIONED_RESOURCE_FOUND,
                    Map.of(ReferenceConstant.FIELD_REFERENCE_ID, referenceId.toString()));
        });
    }

    @Override
    public List<Reference> getEntitiesByVersionId(UUID organizationId, UUID versionId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, ReferenceServiceLogConstant.LOADING_VERSION_REFERENCES,
                versionId);
        return referenceRepository.findByOrganizationIdAndVersionIdOrderByNameAscIdAsc(organizationId, versionId);
    }

    /**
     * Decodes the bytes as strict UTF-8.
     *
     * @param content the file bytes
     * @return the text, or null when the bytes are not valid UTF-8
     */
    private static String utf8Text(byte[] content) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return decoder.decode(ByteBuffer.wrap(content)).toString();
        } catch (CharacterCodingException e) {
            // rule-10-no-op -- reason: binary content is a valid reference file; the caller returns it as base64.
            return null;
        }
    }
}
