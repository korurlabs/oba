package com.korur.oba.apps.kaya.script.service.impl;

// 1. Framework-level
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.nio.charset.StandardCharsets;
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
import com.korur.oba.apps.kaya.script.constant.ScriptConstant;
import com.korur.oba.apps.kaya.script.constant.service.ScriptServiceLogConstant;
import com.korur.oba.apps.kaya.script.dto.response.ScriptDetailResponse;
import com.korur.oba.apps.kaya.script.entity.Script;
import com.korur.oba.apps.kaya.script.mapper.ScriptMapper;
import com.korur.oba.apps.kaya.script.repository.ScriptRepository;
import com.korur.oba.apps.kaya.script.service.ScriptQueryService;

/**
 * The {@link ScriptQueryService} implementation.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScriptQueryServiceImpl implements ScriptQueryService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(ScriptQueryServiceImpl.class);

    private final ScriptRepository scriptRepository;
    private final BlobQueryService blobQueryService;
    private final ScriptMapper scriptMapper;
    private final LoggingService loggingService;

    @Override
    public ScriptDetailResponse getScript(UUID organizationId, UUID scriptId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, ScriptServiceLogConstant.READING_SCRIPT,
                scriptId, organizationId);
        Script script = getEntityById(organizationId, scriptId);
        Blob blob = blobQueryService.getEntityById(organizationId, script.getContentBlobId());
        return scriptMapper.toDetailResponse(script, blob.getSize(),
                new String(blob.getContent(), StandardCharsets.UTF_8));
    }

    @Override
    public Script getEntityById(UUID organizationId, UUID scriptId) {
        // Looked up within the organization, so a script of another organization answers the same 404 as
        // an unknown id.
        return scriptRepository.findByIdAndOrganizationId(scriptId, organizationId).orElseThrow(() -> {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, ScriptServiceLogConstant.SCRIPT_NOT_FOUND,
                    scriptId, organizationId);
            return new NotFoundException(ExceptionDomainKayaError.NOT_VERSIONED_RESOURCE_FOUND,
                    Map.of(ScriptConstant.FIELD_SCRIPT_ID, scriptId.toString()));
        });
    }

    @Override
    public List<Script> getEntitiesByVersionId(UUID organizationId, UUID versionId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, ScriptServiceLogConstant.LOADING_VERSION_SCRIPTS,
                versionId);
        return scriptRepository.findByOrganizationIdAndVersionIdOrderByNameAscIdAsc(organizationId, versionId);
    }
}
