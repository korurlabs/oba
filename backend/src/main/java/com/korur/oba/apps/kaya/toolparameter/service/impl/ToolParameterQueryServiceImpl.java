package com.korur.oba.apps.kaya.toolparameter.service.impl;

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
import com.korur.oba.apps.kaya.toolparameter.constant.ToolParameterConstant;
import com.korur.oba.apps.kaya.toolparameter.constant.service.ToolParameterServiceLogConstant;
import com.korur.oba.apps.kaya.toolparameter.dto.response.ToolParameterDetailResponse;
import com.korur.oba.apps.kaya.toolparameter.entity.ToolParameter;
import com.korur.oba.apps.kaya.toolparameter.mapper.ToolParameterMapper;
import com.korur.oba.apps.kaya.toolparameter.repository.ToolParameterRepository;
import com.korur.oba.apps.kaya.toolparameter.service.ToolParameterQueryService;

/**
 * The {@link ToolParameterQueryService} implementation.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ToolParameterQueryServiceImpl implements ToolParameterQueryService {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(ToolParameterQueryServiceImpl.class);

    private final ToolParameterRepository toolParameterRepository;
    private final BlobQueryService blobQueryService;
    private final ToolParameterMapper toolParameterMapper;
    private final LoggingService loggingService;

    @Override
    public ToolParameterDetailResponse getToolParameter(UUID organizationId, UUID toolParameterId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, ToolParameterServiceLogConstant.READING_TOOL_PARAMETER,
                toolParameterId, organizationId);
        ToolParameter toolParameter = getEntityById(organizationId, toolParameterId);
        Blob blob = blobQueryService.getEntityById(organizationId, toolParameter.getContentBlobId());
        return toolParameterMapper.toDetailResponse(toolParameter, blob.getSize(),
                new String(blob.getContent(), StandardCharsets.UTF_8));
    }

    @Override
    public ToolParameter getEntityById(UUID organizationId, UUID toolParameterId) {
        // Looked up within the organization, so a tool parameter file of another organization answers the same 404 as
        // an unknown id.
        return toolParameterRepository.findByIdAndOrganizationId(toolParameterId, organizationId).orElseThrow(() -> {
            loggingService.warn(LOG_PREFIX, LogLayer.SERVICE, ToolParameterServiceLogConstant.TOOL_PARAMETER_NOT_FOUND,
                    toolParameterId, organizationId);
            return new NotFoundException(ExceptionDomainKayaError.NOT_VERSIONED_RESOURCE_FOUND,
                    Map.of(ToolParameterConstant.FIELD_TOOL_PARAMETER_ID, toolParameterId.toString()));
        });
    }

    @Override
    public List<ToolParameter> getEntitiesByVersionId(UUID organizationId, UUID versionId) {
        loggingService.debug(LOG_PREFIX, LogLayer.SERVICE, ToolParameterServiceLogConstant.LOADING_VERSION_TOOL_PARAMETERS,
                versionId);
        return toolParameterRepository.findByOrganizationIdAndVersionIdOrderByNameAscIdAsc(organizationId, versionId);
    }
}
