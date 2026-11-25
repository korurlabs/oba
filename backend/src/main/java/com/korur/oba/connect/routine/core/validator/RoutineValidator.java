package com.korur.oba.connect.routine.core.validator;

// 1. Framework-level
import org.springframework.stereotype.Component;

// 2. Third-party
import lombok.RequiredArgsConstructor;

// 3. Java SDK
import java.util.Map;

// 4. Internal
import com.korur.oba.connect.routine.core.constant.RoutineConstant;
import com.korur.oba.connect.routine.core.constant.exception.RoutineExceptionLogConstant;
import com.korur.oba.connect.routine.core.enums.RoutineProvider;
import com.korur.oba.connect.routine.core.exception.ExceptionDomainRoutineError;
import com.korur.oba.connect.routine.core.router.RoutineRouter;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;

/**
 * Validates routine input against the vendor's format before it is saved, so a routine that could
 * never fire is refused when it is set rather than failing silently later.
 *
 * The format itself belongs to the vendor's client, resolved through {@link RoutineRouter}.
 */
@Component
@RequiredArgsConstructor
public class RoutineValidator {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(RoutineValidator.class);

    private final RoutineRouter routineRouter;
    private final LoggingService loggingService;

    /**
     * Refuses a routine id that does not have the vendor's format.
     *
     * @param provider  the vendor
     * @param routineId the routine id
     * @throws ValidationException RTN_VAL_001 naming the provider
     */
    public void ensureValidRoutineId(RoutineProvider provider, String routineId) {
        if (routineRouter.resolveClient(provider).isValidRoutineId(routineId)) {
            return;
        }
        loggingService.warn(LOG_PREFIX, LogLayer.VALIDATOR, RoutineExceptionLogConstant.ROUTINE_ID_INVALID, provider);
        throw new ValidationException(ExceptionDomainRoutineError.VAL_ROUTINE_ID_FORMAT,
                Map.of(RoutineConstant.FIELD_PROVIDER, provider.name()));
    }
}
