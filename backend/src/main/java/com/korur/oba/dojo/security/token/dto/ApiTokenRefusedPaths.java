package com.korur.oba.dojo.security.token.dto;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.List;
import java.util.Map;

// 4. Internal
import com.korur.oba.dojo.exception.enums.ExceptionError;

/**
 * Paths a token kind may never call, although its role and app grants would allow them, checked in the
 * token filter once the token is authenticated.
 *
 * @param pathPatterns Spring path patterns of the refused paths
 * @param refusedError the error answered with 403 on them
 * @param errorValues  the values the error carries
 */
public record ApiTokenRefusedPaths(
        List<String> pathPatterns,
        ExceptionError refusedError,
        Map<String, Object> errorValues
) {

    public ApiTokenRefusedPaths {
        pathPatterns = List.copyOf(pathPatterns);
        errorValues = Map.copyOf(errorValues);
    }
}
