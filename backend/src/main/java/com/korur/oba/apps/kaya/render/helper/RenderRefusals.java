package com.korur.oba.apps.kaya.render.helper;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.Map;

// 4. Internal
import com.korur.oba.apps.kaya.core.exception.enums.ExceptionDomainKayaError;
import com.korur.oba.dojo.exception.internal.business.ValidationException;
import com.korur.oba.apps.kaya.render.constant.RenderConstant;
import com.korur.oba.apps.kaya.validation.dto.embedded.Violation;

/**
 * The two refusals of a parse. Both are a {@link ValidationException}, so the transaction that
 * stores the files rolls back and the stored files and their index stay as they were. Each names the
 * path and the reason under {@link RenderConstant#FIELD_PATH} and {@link RenderConstant#FIELD_REASON}.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class RenderRefusals {

    private RenderRefusals() {
    }

    /**
     * A file that cannot be stored: unreadable frontmatter, JSON or YAML, a typed field missing or of
     * the wrong type, or a value the database would refuse.
     *
     * @param path   the delivered path
     * @param reason what is wrong, in words
     * @return the exception with {@link ExceptionDomainKayaError#RENDER_INVALID_FILE}
     */
    public static ValidationException invalidFile(String path, String reason) {
        return new ValidationException(ExceptionDomainKayaError.RENDER_INVALID_FILE,
                Map.of(RenderConstant.FIELD_PATH, path, RenderConstant.FIELD_REASON, reason));
    }

    /**
     * A file that breaks a delivery rule of {@link com.korur.oba.apps.kaya.validation.service.ValidationQueryService}.
     *
     * @param path       the delivered path of the first violation
     * @param violations every violation found
     * @return the exception with {@link ExceptionDomainKayaError#RENDER_INVALID_FILE} and the violations
     */
    public static ValidationException violations(String path, List<Violation> violations) {
        return new ValidationException(ExceptionDomainKayaError.RENDER_INVALID_FILE,
                Map.of(RenderConstant.FIELD_PATH, path, RenderConstant.FIELD_REASON, RenderConstant.VALIDATION_FAILED_REASON,
                        RenderConstant.FIELD_VIOLATIONS, violations));
    }

    /**
     * A path no kind is delivered at.
     *
     * @param path   the path
     * @param reason why it is not delivered
     * @return the exception with {@link ExceptionDomainKayaError#RENDER_NOT_DELIVERABLE}
     */
    public static ValidationException notDeliverable(String path, String reason) {
        return new ValidationException(ExceptionDomainKayaError.RENDER_NOT_DELIVERABLE,
                Map.of(RenderConstant.FIELD_PATH, path, RenderConstant.FIELD_REASON, reason));
    }
}
