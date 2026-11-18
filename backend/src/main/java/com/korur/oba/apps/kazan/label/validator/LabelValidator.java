package com.korur.oba.apps.kazan.label.validator;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.Map;
import java.util.UUID;

import com.korur.oba.apps.kazan.label.constant.LabelConstant;
import com.korur.oba.apps.kazan.label.constant.LabelExceptionLogConstant;
import com.korur.oba.apps.kazan.label.entity.Label;
import com.korur.oba.apps.kazan.label.repository.LabelRepository;
// 4. Internal
import com.korur.oba.apps.kazan.exception.ExceptionDomainKazanError;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

/**
 * Validator for label lookups.
 */
@Component
@RequiredArgsConstructor
public class LabelValidator {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(LabelValidator.class);

    private final LabelRepository labelRepository;
    private final LoggingService loggingService;

    /**
     * Ensures the label exists on the specified board and returns it.
     * Used by label update/delete endpoints to enforce board-scoped access.
     *
     * @param boardId ID of the board from the URL path
     * @param labelId ID of the label
     * @return the label entity
     * @throws NotFoundException if the label does not exist on the given board
     */
    public Label ensureLabelExists(UUID boardId, UUID labelId) {
        return labelRepository.findByBoard_IdAndId(boardId, labelId)
                .orElseThrow(() -> {
                    loggingService.error(
                            LOG_PREFIX,
                            LogLayer.VALIDATOR,
                            LabelExceptionLogConstant.LABEL_NOT_FOUND,
                            labelId);
                    return new NotFoundException(
                            ExceptionDomainKazanError.NOT_LABEL_FOUND,
                            Map.of(LabelConstant.FIELD_LABEL_ID, labelId));
                });
    }

    /**
     * Ensures the label exists on a board of the organization and returns it.
     * Used by ticket create and update, where a label of a related board may be attached.
     * A label of another organization answers the same not found as a missing one.
     *
     * @param organizationId ID of the ticket board's organization
     * @param labelId        ID of the label
     * @return the label entity
     * @throws NotFoundException if the label does not exist on a board of the organization
     */
    public Label ensureLabelInOrganization(UUID organizationId, UUID labelId) {
        return labelRepository.findByIdAndBoard_OrganizationId(labelId, organizationId)
                .orElseThrow(() -> {
                    loggingService.error(
                            LOG_PREFIX,
                            LogLayer.VALIDATOR,
                            LabelExceptionLogConstant.LABEL_NOT_FOUND,
                            labelId);
                    return new NotFoundException(
                            ExceptionDomainKazanError.NOT_LABEL_FOUND,
                            Map.of(LabelConstant.FIELD_LABEL_ID, labelId));
                });
    }
}
