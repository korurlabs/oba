package com.korur.oba.connect.push.dto.request;

// 1. Framework-level
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// 3. Java SDK
import java.util.Set;

// 4. Internal
import com.korur.oba.connect.push.enums.PushPlatform;

/**
 * Unit tests for the bean validation of the push request payloads, which the API answers as 422 GEN_VAL_001.
 */
class PushRequestValidationUnitTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private PushDeviceRegisterRequest device(String token, String locale) {
        return new PushDeviceRegisterRequest(token, PushPlatform.IOS, null, "1.0.0", locale);
    }

    private Set<String> invalidFields(Object request) {
        Set<ConstraintViolation<Object>> violations = validator.validate(request);
        return violations.stream().map(violation -> violation.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet());
    }

    @Test
    void register_validRequest_hasNoViolation() {
        assertThat(invalidFields(device("ExponentPushToken[abc]", "nl"))).isEmpty();
    }

    @Test
    void register_tokenWithoutTheExponentForm_isInvalid() {
        assertThat(invalidFields(device("abc123", "en"))).containsExactly("expoPushToken");
    }

    @Test
    void register_blankToken_isInvalid() {
        assertThat(invalidFields(device(" ", "en"))).contains("expoPushToken");
    }

    @Test
    void register_unsupportedLocale_isInvalid() {
        assertThat(invalidFields(device("ExponentPushToken[abc]", "de"))).containsExactly("locale");
    }

    @Test
    void register_missingPlatformAndVersion_areInvalid() {
        PushDeviceRegisterRequest request = new PushDeviceRegisterRequest("ExponentPushToken[abc]", null, null,
                null, "en");
        assertThat(invalidFields(request)).containsExactlyInAnyOrder("platform", "appVersion");
    }

    @Test
    void register_emptyDeviceName_isInvalidButNullIsAllowed() {
        PushDeviceRegisterRequest empty = new PushDeviceRegisterRequest("ExponentPushToken[abc]", PushPlatform.IOS,
                "", "1.0.0", "en");
        assertThat(invalidFields(empty)).containsExactly("deviceName");
    }

    @Test
    void preferences_everySwitchPresent_hasNoViolation() {
        assertThat(invalidFields(new PushPreferenceUpdateRequest(true, false, true, false, true))).isEmpty();
    }

    @Test
    void preferences_nullFields_areInvalid() {
        PushPreferenceUpdateRequest request = new PushPreferenceUpdateRequest(null, null, null, null, null);
        assertThat(invalidFields(request)).containsExactlyInAnyOrder("mentionsAndReplies", "everyone", "here",
                "urgentIncidents", "showPreviews");
    }
}
