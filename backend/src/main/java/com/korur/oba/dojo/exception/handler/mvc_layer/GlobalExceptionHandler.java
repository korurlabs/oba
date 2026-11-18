package com.korur.oba.dojo.exception.handler.mvc_layer;

// 1. Framework-level
import lombok.RequiredArgsConstructor;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

// 2. Third-party
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

// 3. Java SDK
import java.util.HashMap;
import java.util.Map;

// 4. Internal
import com.korur.oba.dojo.dto.CustomResponse;
import com.korur.oba.dojo.exception.constant.ExceptionConstant;
import com.korur.oba.dojo.exception.constant.ExceptionHandlerLogConstant;
import com.korur.oba.dojo.exception.enums.ExceptionApiError;
import com.korur.oba.dojo.exception.enums.ExceptionInternalError;
import com.korur.oba.dojo.exception.enums.ExceptionNotFoundError;
import com.korur.oba.dojo.exception.enums.ExceptionStateError;
import com.korur.oba.dojo.exception.enums.ExceptionValidationError;
import com.korur.oba.dojo.exception.internal.infrastructure.ApplicationException;
import com.korur.oba.dojo.exception.internal.infrastructure.ErrorResponse;
import com.korur.oba.dojo.logging.enums.LogLayer;
import com.korur.oba.dojo.logging.enums.LogPrefix;
import com.korur.oba.dojo.logging.service.LoggingService;
import com.korur.oba.dojo.util.ResponseUtil;

/**
 * Global exception handler for the Village application.
 *
 * <p>Intercepts exceptions thrown across all controllers and translates them into standardized
 * {@link CustomResponse} HTTP responses via {@link ResponseUtil}.
 *
 * <h2>Handled exceptions by group:</h2>
 *
 * <h3>1. Syntax / Parse errors</h3>
 * <table>
 *   <tr><th>Exception</th><th>Status</th><th>When</th></tr>
 *   <tr>
 *     <td>{@link HttpMessageNotReadableException}</td>
 *     <td>400</td>
 *     <td>Malformed JSON or invalid field value in {@code @RequestBody}</td>
 *   </tr>
 * </table>
 *
 * <h3>2. Validation errors</h3>
 * <table>
 *   <tr><th>Exception</th><th>Status</th><th>When</th></tr>
 *   <tr>
 *     <td>{@link MethodArgumentNotValidException}</td>
 *     <td>400</td>
 *     <td>{@code @Valid} fails on {@code @RequestBody} or {@code @ModelAttribute} DTO</td>
 *   </tr>
 *   <tr>
 *     <td>{@link ConstraintViolationException}</td>
 *     <td>400</td>
 *     <td>{@code @Validated} service bean method params fail (safety net)</td>
 *   </tr>
 *   <tr>
 *     <td>{@link HandlerMethodValidationException}</td>
 *     <td>400</td>
 *     <td>Spring 6 replacement for {@code ConstraintViolationException} at controller level (safety net)</td>
 *   </tr>
 *   <tr>
 *     <td>{@link MultipartException}</td>
 *     <td>400 / 413</td>
 *     <td>File upload fails — 413 if file too large, 400 for other multipart errors</td>
 *   </tr>
 *   <tr>
 *     <td>{@link HttpMediaTypeNotSupportedException}</td>
 *     <td>415</td>
 *     <td>Wrong {@code Content-Type} header (e.g., XML instead of JSON)</td>
 *   </tr>
 *   <tr>
 *     <td>{@link MethodArgumentTypeMismatchException}</td>
 *     <td>400</td>
 *     <td>Path variable, query param, or header cannot be converted (e.g., invalid UUID)</td>
 *   </tr>
 *   <tr>
 *     <td>{@link MissingServletRequestParameterException}</td>
 *     <td>400</td>
 *     <td>Required {@code @RequestParam} is missing from the request</td>
 *   </tr>
 *   <tr>
 *     <td>{@link MissingRequestHeaderException}</td>
 *     <td>400</td>
 *     <td>Required {@code @RequestHeader} is missing (e.g., {@code X-Organization-ID})</td>
 *   </tr>
 *   <tr>
 *     <td>{@link HttpRequestMethodNotSupportedException}</td>
 *     <td>405</td>
 *     <td>Wrong HTTP method for the endpoint (e.g., POST on a GET-only path)</td>
 *   </tr>
 * </table>
 *
 * <h3>3. Application (business logic)</h3>
 * <p>All subclasses of {@link ApplicationException} are caught by a single handler. Each subclass
 * locks in its HTTP status via the constructor — the handler simply reads it.
 *
 * 4. Concurrency
 * - OptimisticLockingFailureException, OptimisticLockException: 409, when another transaction
 *   saved a versioned entity after this request read it.
 *
 * <h3>5. Generic / Fallback</h3>
 * <table>
 *   <tr><th>Exception</th><th>Status</th><th>When</th></tr>
 *   <tr>
 *     <td>{@link NoResourceFoundException}</td>
 *     <td>404</td>
 *     <td>Request passes security but no controller endpoint matches the path</td>
 *   </tr>
 *   <tr>
 *     <td>{@link Exception}</td>
 *     <td>500</td>
 *     <td>Catch-all fallback — any uncaught exception, no details exposed to client</td>
 *   </tr>
 * </table>
 */
@ControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private static final LogPrefix LOG_PREFIX = LogPrefix.fromClass(GlobalExceptionHandler.class);
    private final LoggingService loggingService;

    // ========= SYNTAX / PARSE ERRORS ========= //

    /**
     * Handles {@link HttpMessageNotReadableException} thrown when Spring cannot read or parse the
     * HTTP request body during JSON deserialization (Jackson).
     *
     * <p><b>When is this triggered?</b> Any {@code POST}, {@code PUT}, or {@code PATCH} endpoint
     * that accepts a {@code @RequestBody} can trigger this exception. There are three distinct cases:
     *
     * <ol>
     *   <li><b>Invalid format for a specific field</b> ({@link InvalidFormatException}) — The JSON
     *       is syntactically valid, but a field value cannot be converted to its target Java type.
     *       <ul>
     *         <li><b>Enum mismatch:</b> e.g., {@code {"role": "INVALID_ROLE"}} — returns the field
     *             path with {@link ExceptionConstant#MSG_INVALID_ENUM_VALUE}.</li>
     *         <li><b>Type mismatch:</b> e.g., {@code {"count": "not-a-number"}} — returns the field
     *             path with {@link ExceptionConstant#MSG_INVALID_VALUE}.</li>
     *         <li><b>Nested fields:</b> e.g., {@code {"organization": {"contact": {"email": 123}}}} —
     *             the field path is built as {@code "organization.contact.email"} using Jackson's
     *             {@link JsonMappingException.Reference} chain.</li>
     *       </ul>
     *       The outer message is {@link ExceptionConstant#MSG_INVALID_FIELD_VALUE}.</li>
     *
     *   <li><b>Malformed JSON</b> (any other cause) — The JSON itself is broken: missing braces,
     *       trailing commas, unquoted keys, etc.
     *       The outer message is {@link ExceptionConstant#MSG_MALFORMED_JSON}.</li>
     *
     *   <li><b>No cause at all</b> — The request body is completely empty or unreadable.
     *       Falls through with an empty details map and the default
     *       {@link ExceptionConstant#MSG_MALFORMED_JSON} outer message.</li>
     * </ol>
     *
     * @param ex the HttpMessageNotReadableException thrown during JSON deserialization
     * @return 400 BAD REQUEST with field-specific feedback for InvalidFormatException,
     *         or a generic malformed JSON message otherwise
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<CustomResponse<Void>> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        Map<String, Object> badRequestDetails = new HashMap<>();
        String outerMessage = ExceptionConstant.MSG_MALFORMED_JSON;

        if (ex.getCause() != null) {
            switch (ex.getCause()) {
                case InvalidFormatException ife -> {
                    outerMessage = ExceptionConstant.MSG_INVALID_FIELD_VALUE;
                    String field = ExceptionConstant.FALLBACK_UNKNOWN_FIELD;

                    if (!ife.getPath().isEmpty()) {
                        StringBuilder pathBuilder = new StringBuilder();
                        for (JsonMappingException.Reference ref : ife.getPath()) {
                            if (ref.getFieldName() != null) {
                                if (!pathBuilder.isEmpty()) {
                                    pathBuilder.append(".");
                                }
                                pathBuilder.append(ref.getFieldName());
                            } else if (ref.getIndex() >= 0) {
                                pathBuilder.append("[").append(ref.getIndex()).append("]");
                            }
                        }
                        if (!pathBuilder.isEmpty()) {
                            field = pathBuilder.toString();
                        }
                    }

                    if (ife.getTargetType() != null && ife.getTargetType().isEnum()) {
                        badRequestDetails.put(field, ExceptionConstant.MSG_INVALID_ENUM_VALUE);
                    } else {
                        badRequestDetails.put(field, ExceptionConstant.MSG_INVALID_VALUE);
                    }
                }
                default -> badRequestDetails.put(ExceptionConstant.ERROR, ExceptionConstant.MSG_MALFORMED_JSON);
            }
        }

        loggingService.error(LOG_PREFIX, LogLayer.HANDLER, ex.getMessage(), ex);
        return asJson(ResponseUtil.error(HttpStatus.BAD_REQUEST, outerMessage, new ErrorResponse(
                ExceptionApiError.BAD_REQUEST.getCode(),
                ExceptionApiError.BAD_REQUEST.getMessage(),
                badRequestDetails
        )));
    }

    // ========= VALIDATION ERRORS ========= //

    /**
     * Handles {@link MethodArgumentNotValidException} thrown when Jakarta Bean Validation
     * constraints fail on a {@code @Valid}-annotated {@code @RequestBody} or {@code @ModelAttribute}.
     *
     * @param ex the MethodArgumentNotValidException triggered on failed validation
     * @return 422 UNPROCESSABLE ENTITY with field-specific validation messages
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<CustomResponse<Void>> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        Map<String, Object> validationErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String field;
            if (error instanceof FieldError fieldError) {
                field = fieldError.getField();
            } else {
                field = error.getObjectName();
            }
            validationErrors.put(field, error.getDefaultMessage());
        });

        loggingService.error(LOG_PREFIX, LogLayer.HANDLER, ex.getMessage(), ex);
        return asJson(ResponseUtil.error(HttpStatus.UNPROCESSABLE_ENTITY, ExceptionConstant.MSG_VALIDATION_ERROR, new ErrorResponse(
                ExceptionValidationError.VALIDATION.getCode(),
                ExceptionValidationError.VALIDATION.getMessage(),
                validationErrors
        )));
    }

    /**
     * Handles {@link ConstraintViolationException} thrown by Jakarta Bean Validation.
     *
     * <p>This handler is currently a safety net. This codebase uses {@code @Valid} on
     * {@code @RequestBody}/{@code @ModelAttribute} DTOs, which triggers
     * {@link MethodArgumentNotValidException} instead. This handler catches validation
     * failures from {@code @Validated} service beans deeper in the call chain.
     *
     * @param ex the ConstraintViolationException thrown by the validator
     * @return 422 UNPROCESSABLE ENTITY with field-specific validation messages
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<CustomResponse<Void>> handleConstraintViolationException(ConstraintViolationException ex) {
        Map<String, Object> validationErrors = new HashMap<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            String propertyPath = violation.getPropertyPath().toString();
            String field = propertyPath.contains(".")
                    ? propertyPath.substring(propertyPath.indexOf(".") + 1)
                    : propertyPath;
            validationErrors.put(field, violation.getMessage());
        }

        loggingService.error(LOG_PREFIX, LogLayer.HANDLER, ex.getMessage(), ex);
        return asJson(ResponseUtil.error(HttpStatus.UNPROCESSABLE_ENTITY, ExceptionConstant.MSG_VALIDATION_ERROR, new ErrorResponse(
                ExceptionValidationError.VALIDATION.getCode(),
                ExceptionValidationError.VALIDATION.getMessage(),
                validationErrors
        )));
    }

    /**
     * Handles {@link HandlerMethodValidationException}, introduced in Spring 6 (Spring Boot 3.2+)
     * as the replacement for {@link ConstraintViolationException} at the controller level.
     *
     * <p>This handler is currently a safety net. All controllers use {@code @Valid} on DTO parameters,
     * which triggers {@link MethodArgumentNotValidException} instead. This handler exists in case
     * a developer adds direct constraints on controller method parameters in the future.
     *
     * @param ex the HandlerMethodValidationException from Spring 6+
     * @return 422 UNPROCESSABLE ENTITY with a map of field names to their validation error messages
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<CustomResponse<Void>> handleHandlerMethodValidationException(HandlerMethodValidationException ex) {
        Map<String, Object> validationErrors = new HashMap<>();
        ex.getAllErrors().forEach(error -> {
            String field = ExceptionConstant.FALLBACK_UNKNOWN_FIELD;
            String message = error.getDefaultMessage() != null ? error.getDefaultMessage() : ExceptionConstant.FALLBACK_VALIDATION_FAILED;

            // Primary: extract field name from MethodParameter in error arguments (most reliable)
            if (error.getArguments() != null) {
                for (Object arg : error.getArguments()) {
                    if (arg instanceof MethodParameter param) {
                        field = param.getParameterName() != null
                                ? param.getParameterName()
                                : ExceptionConstant.FALLBACK_PARAM_PREFIX + param.getParameterIndex();
                        break;
                    }
                }
            }

            // Fallback: try codes from DefaultMessageSourceResolvable
            if (field.equals(ExceptionConstant.FALLBACK_UNKNOWN_FIELD)
                    && error instanceof DefaultMessageSourceResolvable resolvable
                    && resolvable.getCodes() != null
                    && resolvable.getCodes().length > 0) {
                String code = resolvable.getCodes()[0];
                field = code.contains(".") ? code.substring(code.lastIndexOf(".") + 1) : code;
            }

            validationErrors.put(field, message);
        });

        loggingService.error(LOG_PREFIX, LogLayer.HANDLER, ex.getMessage(), ex);
        return asJson(ResponseUtil.error(HttpStatus.UNPROCESSABLE_ENTITY, ExceptionConstant.MSG_VALIDATION_ERROR, new ErrorResponse(
                ExceptionValidationError.VALIDATION.getCode(),
                ExceptionValidationError.VALIDATION.getMessage(),
                validationErrors
        )));
    }

    /**
     * Handles {@link MultipartException} thrown when a file upload request fails.
     *
     * <p>Differentiates between two cases:
     * <ol>
     *   <li><b>File too large</b> ({@code MaxUploadSizeExceededException}) → <b>413 Payload Too Large</b></li>
     *   <li><b>Any other multipart error</b> → <b>400 Bad Request</b></li>
     * </ol>
     *
     * @param ex MultipartException from Spring's multipart resolver
     * @return 413 PAYLOAD TOO LARGE for oversized files, 400 BAD REQUEST for other multipart errors
     */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<CustomResponse<Void>> handleMultipartException(MultipartException ex) {
        loggingService.error(LOG_PREFIX, LogLayer.HANDLER, ex.getMessage(), ex);

        if (ex instanceof MaxUploadSizeExceededException) {
            return asJson(ResponseUtil.error(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    ExceptionConstant.MSG_FILE_TOO_LARGE,
                    new ErrorResponse(
                            ExceptionApiError.FILE_TOO_LARGE.getCode(),
                            ExceptionApiError.FILE_TOO_LARGE.getMessage(),
                            Map.of()
                    )
            ));
        }

        return asJson(ResponseUtil.error(
                HttpStatus.BAD_REQUEST,
                ExceptionConstant.MSG_INVALID_FILE_UPLOAD,
                new ErrorResponse(
                        ExceptionApiError.BAD_REQUEST.getCode(),
                        ExceptionApiError.BAD_REQUEST.getMessage(),
                        Map.of()
                )
        ));
    }

    /**
     * Handles {@link HttpMediaTypeNotSupportedException} thrown when the {@code Content-Type}
     * header does not match what the controller endpoint can consume.
     *
     * @param ex the exception thrown by Spring when the media type is not supported
     * @return 415 UNSUPPORTED MEDIA TYPE
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<CustomResponse<Void>> handleHttpMediaTypeNotSupportedException(HttpMediaTypeNotSupportedException ex) {
        loggingService.error(LOG_PREFIX, LogLayer.HANDLER, ex.getMessage(), ex);
        return asJson(ResponseUtil.error(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                ExceptionConstant.MSG_UNSUPPORTED_CONTENT_TYPE,
                new ErrorResponse(
                        ExceptionApiError.UNSUPPORTED_CONTENT_TYPE.getCode(),
                        ExceptionApiError.UNSUPPORTED_CONTENT_TYPE.getMessage(),
                        Map.of()
                )
        ));
    }

    /**
     * Handles {@link MethodArgumentTypeMismatchException} thrown when a controller method parameter
     * (path variable, request parameter, or header) cannot be converted to its expected Java type.
     *
     * @param ex the exception thrown when Spring cannot convert a method parameter to its target type
     * @return 400 BAD REQUEST with the parameter name and a message indicating the value is invalid
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<CustomResponse<Void>> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException ex) {
        Map<String, Object> validationErrors = new HashMap<>();
        String paramName = ex.getName() != null ? ex.getName() : ExceptionConstant.FALLBACK_UNKNOWN_FIELD;
        validationErrors.put(paramName, ExceptionConstant.MSG_INVALID_VALUE);

        loggingService.error(LOG_PREFIX, LogLayer.HANDLER, ex.getMessage(), ex);
        return asJson(ResponseUtil.error(HttpStatus.BAD_REQUEST, ExceptionConstant.MSG_TYPE_MISMATCH, new ErrorResponse(
                ExceptionApiError.BAD_REQUEST.getCode(),
                ExceptionApiError.BAD_REQUEST.getMessage(),
                validationErrors
        )));
    }

    /**
     * Handles {@link MissingServletRequestParameterException} thrown when a required
     * {@code @RequestParam} is not included in the request.
     *
     * @param ex the exception containing the missing parameter name and type
     * @return 400 BAD REQUEST with the missing parameter name in the error details
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<CustomResponse<Void>> handleMissingServletRequestParameterException(MissingServletRequestParameterException ex) {
        loggingService.error(LOG_PREFIX, LogLayer.HANDLER, ex.getMessage(), ex);
        return asJson(ResponseUtil.error(HttpStatus.BAD_REQUEST, ExceptionConstant.MSG_MISSING_PARAMETER, new ErrorResponse(
                ExceptionApiError.MISSING_PROPERTY.getCode(),
                ExceptionApiError.MISSING_PROPERTY.getMessage(),
                Map.of(ex.getParameterName(), ExceptionConstant.MSG_MISSING_PARAMETER)
        )));
    }

    /**
     * Handles {@link MissingRequestHeaderException} thrown when a required {@code @RequestHeader}
     * is not included in the request.
     *
     * @param ex the exception containing the missing header name
     * @return 400 BAD REQUEST with the missing header name in the error details
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<CustomResponse<Void>> handleMissingRequestHeaderException(MissingRequestHeaderException ex) {
        loggingService.error(LOG_PREFIX, LogLayer.HANDLER, ex.getMessage(), ex);
        return asJson(ResponseUtil.error(HttpStatus.BAD_REQUEST, ExceptionConstant.MSG_MISSING_HEADER, new ErrorResponse(
                ExceptionApiError.MISSING_HEADER.getCode(),
                ExceptionApiError.MISSING_HEADER.getMessage(),
                Map.of(ex.getHeaderName(), ExceptionConstant.MSG_MISSING_HEADER)
        )));
    }

    /**
     * Handles {@link HttpRequestMethodNotSupportedException} thrown when the client uses an
     * HTTP method that the endpoint does not support.
     *
     * @param ex the exception containing the unsupported method and the list of supported methods
     * @return 405 METHOD NOT ALLOWED
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<CustomResponse<Void>> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException ex) {
        loggingService.error(LOG_PREFIX, LogLayer.HANDLER, ex.getMessage(), ex);
        return asJson(ResponseUtil.error(HttpStatus.METHOD_NOT_ALLOWED, ExceptionConstant.MSG_METHOD_NOT_ALLOWED, new ErrorResponse(
                ExceptionApiError.METHOD_NOT_ALLOWED.getCode(),
                ExceptionApiError.METHOD_NOT_ALLOWED.getMessage(),
                Map.of("method", ex.getMethod())
        )));
    }

    // ========= APPLICATION ========= //

    /**
     * Handles {@link ApplicationException} and all its subclasses — the business-level exceptions
     * thrown deliberately by service-layer code throughout this application.
     *
     * @param ex the application exception carrying status code, error code, message, and details
     * @return error response with the status code and error metadata from the exception
     */
    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<CustomResponse<Void>> handleApplicationException(ApplicationException ex) {
        loggingService.error(
                LOG_PREFIX,
                LogLayer.HANDLER,
                ExceptionHandlerLogConstant.LOG_APPLICATION_ERROR,
                ex,
                ex.getMessage()
        );
        return asJson(ResponseUtil.error(
                ex.getStatusCode(),
                new ErrorResponse(ex.getErrorCode(), ex.getErrorMessage(), ex.getErrorValues())
        ));
    }

    // ========= CONCURRENCY ========= //

    /**
     * Handles an optimistic lock failure: another transaction saved the same
     * versioned entity after this request read it, so this request's save would
     * have overwritten that change. Spring translates the JPA exception into
     * {@link OptimisticLockingFailureException} on repository calls and at commit;
     * the raw {@link OptimisticLockException} is caught too, for a flush outside
     * both. The client is told to reload and try again, since repeating the same
     * request body would only fail again or overwrite the other change.
     *
     * @param ex the optimistic lock failure
     * @return 409 CONFLICT with the generic conflict code and no details exposed
     */
    @ExceptionHandler({OptimisticLockingFailureException.class, OptimisticLockException.class})
    public ResponseEntity<CustomResponse<Void>> handleOptimisticLockFailure(Exception ex) {
        loggingService.warn(LOG_PREFIX, LogLayer.HANDLER, ExceptionHandlerLogConstant.LOG_CONCURRENT_UPDATE, ex.getMessage());
        return asJson(ResponseUtil.error(HttpStatus.CONFLICT, ExceptionConstant.MSG_CONCURRENT_UPDATE, new ErrorResponse(
                ExceptionStateError.CONFLICT.getCode(),
                ExceptionStateError.CONFLICT.getMessage(),
                Map.of()
        )));
    }

    // ========= GENERIC / FALLBACK ========= //

    /**
     * Handles {@link AsyncRequestTimeoutException}, raised when an async request such as the session
     * stream reaches its timeout. A long-lived stream timing out is routine, so it is logged at
     * debug only. A stream has already sent its status and events, so the response just ends;
     * one that sent nothing yet answers 503 without a body.
     *
     * @param ex the timeout
     * @param response the response of the timed-out request
     */
    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public void handleAsyncRequestTimeoutException(AsyncRequestTimeoutException ex, HttpServletResponse response) {
        loggingService.debug(LOG_PREFIX, LogLayer.HANDLER, ExceptionHandlerLogConstant.LOG_ASYNC_REQUEST_TIMED_OUT, ex.getMessage());
        if (!response.isCommitted()) {
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        }
    }

    /**
     * Handles {@link AsyncRequestNotUsableException}, raised when the client of an async request
     * has gone away, such as a closed browser tab on the session stream. That is normal, so it is
     * logged at debug only, and nothing is written because no one is reading.
     *
     * @param ex the failure to use the response
     * @param response the unusable response; taking it marks the request as handled
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleAsyncRequestNotUsableException(AsyncRequestNotUsableException ex, HttpServletResponse response) {
        // rule-10-no-op -- reason: the client closed the connection, so no one can be told and nothing is retried
        loggingService.debug(LOG_PREFIX, LogLayer.HANDLER, ExceptionHandlerLogConstant.LOG_CLIENT_GONE, ex.getMessage());
    }

    /**
     * Handles {@link NoResourceFoundException} thrown when a request passes through security
     * but no controller endpoint matches the requested path.
     *
     * @param ex the NoResourceFoundException thrown by Spring MVC's DispatcherServlet
     * @return 404 NOT FOUND with the requested path in the error details
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<CustomResponse<Void>> handleNoResourceFoundException(NoResourceFoundException ex) {
        loggingService.warn(LOG_PREFIX, LogLayer.HANDLER, ex.getMessage());
        return asJson(ResponseUtil.error(HttpStatus.NOT_FOUND, new ErrorResponse(
                ExceptionNotFoundError.RESOURCE_FOUND.getCode(),
                ExceptionNotFoundError.RESOURCE_FOUND.getMessage(),
                Map.of(ExceptionConstant.FIELD_PATH, ex.getResourcePath())
        )));
    }

    /**
     * Catch-all fallback handler for any exception not matched by the more specific handlers above.
     *
     * <p>This handler intentionally returns only a generic error code and message.
     * Exposing internal exception details (class names, stack traces, SQL queries)
     * to the client would be a security risk. The full exception is logged server-side.
     *
     * @param ex any uncaught exception that was not handled by a more specific handler
     * @return 500 INTERNAL SERVER ERROR with a generic error code and no details exposed
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<CustomResponse<Void>> handleUnhandledExceptions(Exception ex) {
        loggingService.error(
                LOG_PREFIX,
                LogLayer.HANDLER,
                ExceptionHandlerLogConstant.LOG_UNEXPECTED_ERROR,
                ex,
                ex.getMessage()
        );
        return asJson(ResponseUtil.error(HttpStatus.INTERNAL_SERVER_ERROR, new ErrorResponse(
                ExceptionInternalError.UNEXPECTED.getCode(),
                ExceptionInternalError.UNEXPECTED.getMessage(),
                Map.of()
        )));
    }

    /**
     * Fixes the error answer's type to JSON, so that content negotiation does not look for a
     * converter of the requested type, which fails for text/event-stream.
     */
    private static ResponseEntity<CustomResponse<Void>> asJson(ResponseEntity<CustomResponse<Void>> response) {
        return ResponseEntity.status(response.getStatusCode())
                .headers(response.getHeaders())
                .contentType(MediaType.APPLICATION_JSON)
                .body(response.getBody());
    }
}
