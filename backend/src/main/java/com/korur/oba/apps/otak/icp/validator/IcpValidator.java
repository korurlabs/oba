package com.korur.oba.apps.otak.icp.validator;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.Map;
import java.util.UUID;

// 4. Internal
import com.korur.oba.apps.otak.exception.ExceptionDomainOtakError;
import com.korur.oba.apps.otak.icp.constant.IcpConstant;
import com.korur.oba.apps.otak.icp.constant.IcpExceptionLogConstant;
import com.korur.oba.apps.otak.icp.entity.Icp;
import com.korur.oba.apps.otak.icp.repository.IcpRepository;
import com.korur.oba.apps.otak.relation.enums.RelationSize;
import com.korur.oba.dojo.exception.internal.business.ConflictException;
import com.korur.oba.dojo.exception.internal.business.NotFoundException;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

/**
 * Validator for ICP lookups, the company size range and the name rule.
 */
@Component
@RequiredArgsConstructor
public class IcpValidator {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(IcpValidator.class);

    private final IcpRepository icpRepository;
    private final LoggingService loggingService;

    /**
     * Ensures the organization has the ICP and returns it. An ICP of another organization
     * answers the same not found as a missing one, so its id reveals nothing to another
     * tenant.
     *
     * @param organizationId the organization the caller acts in
     * @param icpId          the ICP identifier
     * @return the ICP
     * @throws NotFoundException {@link ExceptionDomainOtakError#NOT_ICP_FOUND} if the
     *         organization has no ICP with the id
     */
    public Icp ensureIcpExists(UUID organizationId, UUID icpId) {
        return icpRepository.findByIdAndOrganizationId(icpId, organizationId)
                .orElseThrow(() -> {
                    loggingService.warn(
                            LOG_PREFIX,
                            LogLayer.VALIDATOR,
                            IcpExceptionLogConstant.ICP_NOT_FOUND,
                            icpId,
                            organizationId);
                    return new NotFoundException(
                            ExceptionDomainOtakError.NOT_ICP_FOUND,
                            Map.of(IcpConstant.FIELD_ICP_ID, icpId));
                });
    }

    /**
     * Ensures the minimum company size is not above the maximum. The bands are declared
     * from small to large, the same order the database check constraint compares, so the
     * ordinal decides. An open bound (null) always passes.
     *
     * @param companySizeMin the smallest head-count band, or null for no lower bound
     * @param companySizeMax the largest head-count band, or null for no upper bound
     * @throws ValidationException {@link ExceptionDomainOtakError#VAL_ICP_SIZE_RANGE} when
     *         the minimum is above the maximum
     */
    public void ensureSizeRange(RelationSize companySizeMin, RelationSize companySizeMax) {
        if (companySizeMin == null || companySizeMax == null) {
            return;
        }
        if (companySizeMin.ordinal() > companySizeMax.ordinal()) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                    IcpExceptionLogConstant.ICP_SIZE_RANGE_INVALID, companySizeMin, companySizeMax);
            throw new ValidationException(
                    ExceptionDomainOtakError.VAL_ICP_SIZE_RANGE,
                    Map.of(IcpConstant.FIELD_COMPANY_SIZE_MIN, companySizeMin,
                            IcpConstant.FIELD_COMPANY_SIZE_MAX, companySizeMax));
        }
    }

    /**
     * Ensures no other ICP of the organization has the name, case ignored, so an ICP can
     * be picked by its name without ambiguity.
     *
     * @param organizationId the organization that owns the ICPs
     * @param name           the trimmed name to check
     * @param exceptIcpId    the ICP that may keep its own name on an update, or null on create
     * @throws ConflictException {@link ExceptionDomainOtakError#STA_ICP_NAME_TAKEN} when
     *         another ICP of the organization has the name
     */
    public void ensureNameFree(UUID organizationId, String name, UUID exceptIcpId) {
        boolean taken = exceptIcpId == null
                ? icpRepository.existsByOrganizationIdAndNameIgnoreCase(organizationId, name)
                : icpRepository.existsByOrganizationIdAndNameIgnoreCaseAndIdNot(organizationId, name, exceptIcpId);
        if (taken) {
            loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR,
                    IcpExceptionLogConstant.ICP_NAME_TAKEN, organizationId, name);
            throw new ConflictException(
                    ExceptionDomainOtakError.STA_ICP_NAME_TAKEN,
                    Map.of(IcpConstant.FIELD_NAME, name));
        }
    }
}
